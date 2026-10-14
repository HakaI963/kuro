package com.kuro.app.engine.proot

import android.content.Context
import android.system.Os
import com.kuro.app.core.root.RootChecker
import com.kuro.app.core.root.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Step 5c: proot acquisition, reordered by reliability (first pass wins).
 * Step 5e: app-private storage cannot exec (error=13), so the winner is
 * relocated to an allowed executable location BEFORE it counts.
 * Step 5f: nativeLibraryDir is read-only at runtime (EACCES on copy), so
 * the order is now:
 *   1) codeCacheDir/files/bin/proot (writable, no su),
 *   2) cacheDir/bin/proot fallback (writable, no su),
 *   3) /data/local/tmp/kuro-proot via su — ONLY in user-initiated flows
 *      with granted root (KernelSU); never background, never unprompted.
 * Every candidate must pass the VALIDATION GATE from its final path:
 *   a) ELF: fully static (PT_INTERP ABSENT — any interp rejects), EM_AARCH64, PIE.
 *   b) `proot -V` prints a version.
 *   c) smoke: `proot -w / /system/bin/sh -c 'echo proot-ok'` prints proot-ok.
 * Each candidate logs one proof/failure line to kuro-engine.log; all-fail
 * yields an honest error card listing every logged reason. su is used ONLY
 * for the /data/local/tmp fallback copy, and only when the caller allows it
 * AND root is granted (user-initiated flows — never background, unprompted).
 */
data class ProotProvenance(
    val source: String,
    val sha256: String,
    val versionLine: String,
    val installedAt: Long,
    val elfProof: List<String>,
    /** Step 5e: final validated executable path (null for pre-5e records). */
    val path: String? = null
) {
    fun toLines(): List<String> = listOf(
        "source=$source",
        "sha256=$sha256",
        "version=$versionLine",
        "installedAt=$installedAt",
        "path=${path ?: "-"}"
    ) + elfProof.map { "elf=$it" }

    companion object {
        fun parse(lines: List<String>): ProotProvenance? {
            return runCatching {
                val map = lines.mapNotNull {
                    val i = it.indexOf('=')
                    if (i < 0) null else it.take(i) to it.drop(i + 1)
                }.toMap()
                ProotProvenance(
                    source = map["source"] ?: return null,
                    sha256 = map["sha256"] ?: "?",
                    versionLine = map["version"] ?: "?",
                    installedAt = map["installedAt"]?.toLongOrNull() ?: 0L,
                    elfProof = lines.filter { it.startsWith("elf=") }.map { it.removePrefix("elf=") },
                    path = map["path"]?.takeIf { it != "-" }
                )
            }.getOrNull()
        }
    }
}

sealed interface AcquireResult {
    data class Success(val provenance: ProotProvenance) : AcquireResult
    data class Failure(val reasons: List<String>) : AcquireResult
}

object ProotAcquisition {
    const val ALPINE_INDEX_URL =
        "https://dl-cdn.alpinelinux.org/alpine/edge/community/aarch64/APKINDEX.tar.gz"
    const val ALPINE_DIR_URL =
        "https://dl-cdn.alpinelinux.org/alpine/edge/community/aarch64"
    const val AHMED_TAG = "v26.10.08-a179d3e"
    const val AHMED_ZIP_SHA256 =
        "d621d71fb68bf07accbada37ffc4208cf4a963eed70916ee68f848cbd9555cc3"
    const val BUNDLED_ASSET = "proot/proot-aarch64"

    const val PROVENANCE_FILE = "bin/proot.provenance"
    const val VALIDATION_FILE = "bin/proot.validation"

    /**
     * Step 5e: the APK-bundled static binary (offline byte source, no
     * network). Step 5f: it is NOT executed in place — nativeLibraryDir is
     * read-only at runtime, so these bytes are relocated to codeCache like
     * every other candidate and validated from there.
     */
    const val APK_LIB_NAME = "libkuro-proot.so"

    /** Step 5e: su-only fallback executable path (user-initiated only). */
    const val TMP_FALLBACK_DIR = "/data/local/tmp"
    const val TMP_FALLBACK_NAME = "kuro-proot"

    /**
     * Step 5f: both app-writable locations failed this run (usually noexec).
     * Logged verbatim as the terminal honest error — never followed by an
     * unprompted su attempt.
     */
    const val NO_EXEC_DIR_MESSAGE =
        "No executable directory found (noexec on codeCache and cache)"

    fun apkLibFile(appContext: Context): File =
        File(appContext.applicationInfo.nativeLibraryDir, APK_LIB_NAME)

    fun tmpFallbackFile(): File = File(TMP_FALLBACK_DIR, TMP_FALLBACK_NAME)

    /**
     * Step 5f primary executable: <codeCacheDir>/files/bin/proot.
     * Pure on the dir (unit-tested); Context overload below.
     */
    fun codeCacheDestFor(codeCacheDir: File): File =
        File(File(File(codeCacheDir, "files"), "bin"), "proot")

    /** Step 5f fallback executable: <cacheDir>/bin/proot. */
    fun cacheDestFor(cacheDir: File): File =
        File(File(cacheDir, "bin"), "proot")

    fun codeCacheDest(appContext: Context): File = codeCacheDestFor(appContext.codeCacheDir)

    fun cacheDest(appContext: Context): File = cacheDestFor(appContext.cacheDir)

    /**
     * The /data/local/tmp su copy runs ONLY when the caller allows su (a
     * user-initiated flow — never background) AND root is granted. Pure
     * (unit-tested); the su command itself still has to exit 0.
     */
    fun shouldAttemptTmpFallback(allowSuCopy: Boolean, rootGranted: Boolean): Boolean =
        allowSuCopy && rootGranted

    private const val HTTP_TIMEOUT_MS = 30_000
    private const val MAX_CANDIDATE_BYTES = 16L * 1024 * 1024

    suspend fun acquire(
        appContext: Context,
        allowSuCopy: Boolean = false
    ): AcquireResult = withContext(Dispatchers.IO) { acquireOnIo(appContext, allowSuCopy) }

    private fun acquireOnIo(appContext: Context, allowSuCopy: Boolean): AcquireResult {
        val filesDir = appContext.filesDir
        val reasons = mutableListOf<String>()
        // apk-lib first: offline bytes from the APK-bundled .so (no network),
        // relocated to codeCache and validated like every other candidate.
        val candidates: List<Pair<String, () -> ByteArray?>> = listOf(
            "apk-lib" to { fetchApkLib(appContext) },
            "alpine-apk" to { fetchAlpine(filesDir) },
            "ahmed-ndk" to { fetchAhmed() },
            "proot-static-build" to { fetchStaticBuild() },
            "bundled-asset" to { fetchBundled(appContext) }
        )
        candidates.forEach { (source, fetch) ->
            val (line, installed) = runCatching {
                gateCandidate(appContext, source, allowSuCopy) { fetch() }
            }.getOrElse { e ->
                ("$source: fetch crashed: ${e.message}" to null)
            }
            reasons.add(line)
            ContainerEngineLog.append(filesDir, "proot-acquire", line)
            if (installed != null) return AcquireResult.Success(installed)
        }
        reasons.add(
            "build-script: local CI build only, not attempted on device " +
                "(see scripts/build-proot.sh)"
        )
        ContainerEngineLog.append(filesDir, "proot-acquire", reasons.last())
        return AcquireResult.Failure(reasons)
    }

    /**
     * Step 5e: APK-bundled .so as offline bytes (no network). Step 5f: never
     * executed in place — the caller relocates these bytes to codeCache.
     */
    private fun fetchApkLib(appContext: Context): ByteArray? {
        val lib = apkLibFile(appContext)
        if (!lib.isFile) {
            throw IllegalStateException("apk-lib absent at ${lib.absolutePath} (reinstall the APK?)")
        }
        return lib.readBytes()
    }

    /**
     * True when an exec attempt failed because the FILE LOCATION forbids
     * execution (Android error=13 on app-private storage) rather than the
     * binary being bad. Only this failure mode may proceed to relocation.
     * Pure (unit-tested).
     */
    fun isLocationBlocked(stderr: String, exitCode: Int): Boolean =
        exitCode == -1 && ("permission denied" in stderr.lowercase() || "eacces" in stderr.lowercase())

    private fun runVersion(binary: File): Triple<String, String, Int> {
        val (out, err, code) = ExecCapture.run(listOf(binary.absolutePath, "-V"), 10_000L)
        val line = out.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        return Triple(line, err, code)
    }

    private fun runSmoke(binary: File): Triple<Boolean, String, Int> {
        val (out, err, code) = ExecCapture.run(
            listOf(binary.absolutePath, "-w", "/", "/system/bin/sh", "-c", "echo proot-ok"),
            15_000L
        )
        return Triple(code == 0 && "proot-ok" in out, err, code)
    }

    /**
     * Step 5f: install [staged] bytes at a writable, executable location and
     * RE-VALIDATE from the final path. Order: codeCacheDir/files/bin/proot
     * (no su), then cacheDir/bin/proot (no su), then /data/local/tmp via su
     * (only when [allowSuCopy] AND root is granted — never background).
     * Returns the validated final file, or null with the logged reason.
     */
    private fun relocateToExecLocation(
        appContext: Context,
        staged: File,
        source: String,
        allowSuCopy: Boolean
    ): Pair<File?, String?> {
        // 1) codeCache (no su).
        val codeCache = codeCacheDest(appContext)
        val codeCacheFailure = installAndValidate(staged, codeCache)
        if (codeCacheFailure == null) return (codeCache to null)
        // 2) cache fallback (no su).
        val cache = cacheDest(appContext)
        val cacheFailure = installAndValidate(staged, cache)
        if (cacheFailure == null) return (cache to null)
        val roots = "$source: codeCache install failed: ${codeCacheFailure.message}; " +
            "cache install failed: ${cacheFailure.message}"
        // 3) su fallback — user-initiated flows with granted root only.
        if (!shouldAttemptTmpFallback(allowSuCopy, rootGranted())) {
            return (null to "$roots; su fallback skipped " +
                "(needs user-initiated flow + granted root). $NO_EXEC_DIR_MESSAGE")
        }
        val dst = tmpFallbackFile()
        val su = ExecCapture.run(suInstallCommand(staged, dst), 30_000L)
        if (su.third != 0 || !dst.isFile) {
            return (null to "$roots; tmp fallback failed: " +
                "su exit=${su.third} stderr=${su.second.take(200)}. $NO_EXEC_DIR_MESSAGE")
        }
        val (tVersionLine, tVersionErr, tVersionCode) = runVersion(dst)
        if (tVersionCode != 0 || !tVersionLine.contains("proot", ignoreCase = true)) {
            return (null to "$roots; tmp fallback -V failed: " +
                "exit=$tVersionCode stderr=${tVersionErr.take(200)}")
        }
        val (tSmokeOk, tSmokeErr, tSmokeCode) = runSmoke(dst)
        if (!tSmokeOk) {
            return (null to "$roots; tmp fallback smoke failed: " +
                "exit=$tSmokeCode stderr=${tSmokeErr.take(200)}")
        }
        return (dst to null)
    }

    private fun rootGranted(): Boolean =
        RootChecker.status.value is RootStatus.Granted

    /**
     * Copy [staged] to [dest] (via .new + rename, chmod 700) and validate
     * -V + smoke FROM [dest]. Returns null on success, the failure otherwise.
     */
    private fun installAndValidate(staged: File, dest: File): Throwable? {
        return runCatching {
            dest.parentFile?.mkdirs()
            val next = File(dest.parentFile, "${dest.name}.new")
            runCatching { if (next.exists()) next.delete() }
            staged.copyTo(next, overwrite = true)
            chmod700(next)
            runCatching { if (dest.exists()) dest.delete() }
            if (!next.renameTo(dest)) {
                next.copyTo(dest, overwrite = true)
                runCatching { next.delete() }
            }
            chmod700(dest)
            val (versionLine, versionErr, versionCode) = runVersion(dest)
            if (versionCode != 0 || !versionLine.contains("proot", ignoreCase = true)) {
                throw IllegalStateException(
                    "proot -V exit=$versionCode stderr=${versionErr.take(200)}"
                )
            }
            val (smokeOk, smokeErr, smokeCode) = runSmoke(dest)
            if (!smokeOk) {
                throw IllegalStateException("smoke exit=$smokeCode stderr=${smokeErr.take(200)}")
            }
        }.exceptionOrNull()
    }

    /**
     * Pure su copy+chmod command builder (unit-tested): refresh [dst] from
     * [src] as root. Validation afterwards runs WITHOUT su (plain exec).
     */
    fun suInstallCommand(src: File, dst: File): List<String> {
        val inner = "rm -f '${dst.absolutePath}' && " +
            "cp '${src.absolutePath}' '${dst.absolutePath}' && " +
            "chmod 700 '${dst.absolutePath}'"
        return listOf("su", "-c", inner)
    }

    private fun persistProvenance(filesDir: File, provenance: ProotProvenance) {
        runCatching {
            File(filesDir, PROVENANCE_FILE).apply {
                parentFile?.mkdirs()
                writeText(provenance.toLines().joinToString("\n", postfix = "\n"))
            }
        }
    }

    private fun gateCandidate(
        appContext: Context,
        source: String,
        allowSuCopy: Boolean,
        fetch: () -> ByteArray?
    ): Pair<String, ProotProvenance?> {
        val filesDir = appContext.filesDir
        val bytes: ByteArray
        try {
            bytes = fetch() ?: return ("$source: no bytes produced" to null)
        } catch (e: Exception) {
            return ("$source: fetch failed: ${e.message}" to null)
        }
        if (bytes.isEmpty()) return ("$source: empty download" to null)
        // 1) Stage to a temp file (exec needs a path; note: app-private
        // storage cannot exec on targetSdk 29+ — error=13 is EXPECTED here
        // and only gates relocation, never success).
        val tmp = File(filesDir, "bin/.proot-candidate")
        try {
            tmp.parentFile?.mkdirs()
            tmp.writeBytes(bytes)
            chmod700(tmp)
        } catch (e: Exception) {
            return ("$source: staging failed: ${e.message}" to null)
        }
        try {
            // a) ELF forensics — Step 5e: ANY PT_INTERP rejects.
            val elf = ElfProbe.inspect(tmp)
                ?: return ("$source: not an ELF binary" to null).also { tmp.delete() }
            val proof = elf.proofLines()
            val verdict = ElfProbe.verdict(elf)
            if (verdict != null) {
                tmp.delete()
                writeValidation(filesDir, source, proof, "", "rejected", ok = false)
                return ("$source: REJECTED: $verdict | ${proof.joinToString(" ")}" to null)
            }
            // b+c) Staged -V + smoke: informative only. A location-blocked
            // exec (error=13) proceeds to relocation; any REAL failure
            // (bad output, nonzero exit) rejects the candidate outright.
            val (stagedVersion, stagedVersionErr, stagedVersionCode) = runVersion(tmp)
            val stagedBlocked = isLocationBlocked(stagedVersionErr, stagedVersionCode)
            if (!stagedBlocked &&
                (stagedVersionCode != 0 || !stagedVersion.contains("proot", ignoreCase = true))
            ) {
                tmp.delete()
                writeValidation(filesDir, source, proof, stagedVersion, "proot -V exit=$stagedVersionCode", ok = false)
                return ("$source: REJECTED: staged proot -V exit=$stagedVersionCode " +
                    "stderr=${stagedVersionErr.take(200)} | ${proof.joinToString(" ")}" to null)
            }
            if (!stagedBlocked) {
                val (stagedSmokeOk, stagedSmokeErr, stagedSmokeCode) = runSmoke(tmp)
                if (!stagedSmokeOk) {
                    tmp.delete()
                    writeValidation(filesDir, source, proof, stagedVersion, "smoke exit=$stagedSmokeCode", ok = false)
                    return ("$source: REJECTED: staged smoke exit=$stagedSmokeCode " +
                        "stderr=${stagedSmokeErr.take(200)} | ${proof.joinToString(" ")}" to null)
                }
            }
            // d) Relocate to an executable location + RE-VALIDATE from there.
            // ONLY a binary passing -V AND smoke from its final path wins.
            val (final, relocateReason) = relocateToExecLocation(appContext, tmp, source, allowSuCopy)
            tmp.delete()
            if (final == null) {
                writeValidation(filesDir, source, proof, stagedVersion, "relocate", ok = false)
                return ("$source: REJECTED: $relocateReason | ${proof.joinToString(" ")}" to null)
            }
            // Winner: provenance records the FINAL executable path.
            val sha = Sha256.ofBytes(bytes)
            val (finalVersion, _, _) = runVersion(final)
            val provenance = ProotProvenance(
                source = source,
                sha256 = sha,
                versionLine = finalVersion.take(120),
                installedAt = System.currentTimeMillis(),
                elfProof = proof,
                path = final.absolutePath
            )
            persistProvenance(filesDir, provenance)
            writeValidation(
                filesDir, source, proof, finalVersion.take(120),
                "smoke=proot-ok exit=0", ok = true
            )
            ContainerEngineLog.append(
                filesDir, "proot-acquire",
                "$source: INSTALLED $finalVersion sha=${sha.take(16)}… path=${final.absolutePath}"
            )
            return ("$source: PASS ${proof.joinToString(" ")} | $finalVersion | " +
                "smoke=proot-ok | path=${final.absolutePath}" to provenance)
        } finally {
            runCatching { if (tmp.exists()) tmp.delete() }
        }
    }

    private fun chmod700(file: File) {
        val ok = runCatching {
            Os.chmod(file.absolutePath, 448) // 0700
            true
        }.getOrDefault(false) || runCatching {
            val p = ProcessBuilder("chmod", "700", file.absolutePath).start()
            p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)
        if (!ok) throw IllegalStateException("chmod 700 failed")
    }

    // ——— Candidate 1: Alpine proot-static .apk ———

    private fun fetchAlpine(filesDir: File): ByteArray? {
        val indexBytes = httpGet(URL(ALPINE_INDEX_URL))
        val indexText = GZIPInputStream(indexBytes.inputStream()).bufferedReader().readText()
        val version = parseApkIndexVersion(indexText, "proot-static")
            ?: throw IllegalStateException("proot-static missing from APKINDEX")
        val apkUrl = "$ALPINE_DIR_URL/proot-static-$version.apk"
        val apkBytes = httpGet(URL(apkUrl))
        // .apk = gzipped tar; want usr/bin/proot.static.
        GZIPInputStream(apkBytes.inputStream()).use { gz ->
            org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gz).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name == "usr/bin/proot.static") {
                        return readCapped(tar, entry.size)
                    }
                    entry = tar.nextEntry
                }
            }
        }
        throw IllegalStateException("usr/bin/proot.static not in $apkUrl")
    }

    /** Pure index parser (unit-tested): P:<pkg> block -> V:<version>. */
    fun parseApkIndexVersion(indexText: String, pkg: String): String? {
        var inBlock = false
        indexText.lineSequence().forEach { line ->
            if (line.isBlank()) {
                inBlock = false
                return@forEach
            }
            if (line == "P:$pkg") {
                inBlock = true
                return@forEach
            }
            if (inBlock && line.startsWith("V:")) return line.removePrefix("V:")
        }
        return null
    }

    // ——— Candidate 2: ahmed-alnassif NDK builds ———

    private fun fetchAhmed(): ByteArray? {
        // Pinned automated Android build (statically linked talloc per repo).
        val pinned =
            "https://github.com/ahmed-alnassif/proot/releases/download/$AHMED_TAG/proot-aarch64.zip"
        val zipBytes = try {
            httpGet(URL(pinned))
        } catch (e: Exception) {
            return fetchAhmedLatest()
        }
        val pinnedSha = AHMED_ZIP_SHA256
        if (!Sha256.ofBytes(zipBytes).equals(pinnedSha, ignoreCase = true)) {
            throw IllegalStateException("pinned zip checksum mismatch")
        }
        return unzipSingle(zipBytes, "proot")
            ?: throw IllegalStateException("proot entry missing in pinned zip")
    }

    private fun fetchAhmedLatest(): ByteArray? {
        // Best-effort rotation fallback: newest release asset starting with
        // proot-aarch64 (no published checksum for fresh tags: size-gated only).
        val api = httpGet(URL("https://api.github.com/repos/ahmed-alnassif/proot/releases?per_page=5"))
            .toString(Charsets.UTF_8)
        val urls = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]*proot-aarch64[^\"]*)\"")
            .findAll(api).map { it.groupValues[1].replace("\\/", "/") }.toList()
        val url = urls.firstOrNull()
            ?: throw IllegalStateException("no proot-aarch64 asset in recent releases")
        val raw = httpGet(URL(url))
        return if (url.endsWith(".zip")) {
            unzipSingle(raw, "proot")
                ?: throw IllegalStateException("proot entry missing in $url")
        } else raw
    }

    // ——— Candidate 3: proot-me proot-static-build assets ———

    private fun fetchStaticBuild(): ByteArray? {
        val api = httpGet(URL("https://api.github.com/repos/proot-me/proot-static-build/releases?per_page=10"))
            .toString(Charsets.UTF_8)
        val urls = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(api).map { it.groupValues[1].replace("\\/", "/") }
            .filter { u ->
                val n = u.lowercase()
                "aarch64" in n && "static" in n
            }
        val url = urls.firstOrNull()
            ?: throw IllegalStateException("no aarch64-static asset in proot-static-build releases")
        return httpGet(URL(url))
    }

    // ——— Candidate 4: bundled repo asset ———

    private fun fetchBundled(appContext: Context): ByteArray? {
        return runCatching {
            appContext.assets.open(ProotBinary.ASSET_PATH).use { it.readBytes() }
        }.getOrNull() ?: throw IllegalStateException("bundled asset missing")
    }

    // ——— HTTP + zip helpers ———

    private fun httpGet(url: URL): ByteArray {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = HTTP_TIMEOUT_MS
            readTimeout = HTTP_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Kuro-proot-acquire")
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP $code for $url")
            }
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_CANDIDATE_BYTES) {
                        throw IllegalStateException("candidate exceeds 16 MB cap")
                    }
                    out.write(buf, 0, n)
                }
                return out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun unzipSingle(zipBytes: ByteArray, entryName: String): ByteArray? {
        ZipInputStream(zipBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == entryName) {
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_CANDIDATE_BYTES) return null
                        out.write(buf, 0, n)
                    }
                    return out.toByteArray()
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun readCapped(
        input: java.io.InputStream,
        knownSize: Long
    ): ByteArray {
        if (knownSize in 1..MAX_CANDIDATE_BYTES) {
            val out = ByteArrayOutputStream(knownSize.toInt().coerceAtMost(8 * 1024 * 1024))
            val buf = ByteArray(64 * 1024)
            var remaining = knownSize
            while (remaining > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (n < 0) break
                out.write(buf, 0, n)
                remaining -= n
            }
            return out.toByteArray()
        }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_CANDIDATE_BYTES) throw IllegalStateException("entry exceeds 16 MB cap")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun readProvenance(filesDir: File): ProotProvenance? {
        val f = File(filesDir, PROVENANCE_FILE)
        if (!f.exists()) return null
        return runCatching { ProotProvenance.parse(f.readLines()) }.getOrNull()
    }

    fun writeValidation(
        filesDir: File,
        source: String,
        elfProof: List<String>,
        versionLine: String,
        smokeLine: String,
        ok: Boolean
    ) {
        runCatching {
            val f = File(filesDir, VALIDATION_FILE)
            f.parentFile?.mkdirs()
            val lines = mutableListOf(
                "ts=${System.currentTimeMillis()}",
                "source=$source",
                "ok=$ok",
                "version=$versionLine",
                "smoke=$smokeLine"
            )
            elfProof.forEach { lines.add("elf=$it") }
            f.writeText(lines.joinToString("\n", postfix = "\n"))
        }
    }

    fun readValidation(filesDir: File): List<String> {
        val f = File(filesDir, VALIDATION_FILE)
        if (!f.exists()) return emptyList()
        return runCatching { f.readLines() }.getOrDefault(emptyList())
    }
}
