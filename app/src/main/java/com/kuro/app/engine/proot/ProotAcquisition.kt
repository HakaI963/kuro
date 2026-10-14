package com.kuro.app.engine.proot

import android.content.Context
import android.system.Os
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
 * Every candidate must pass the VALIDATION GATE before use:
 *   a) ELF: static-capable (no Linux-loader PT_INTERP), EM_AARCH64, PIE.
 *   b) `proot -V` prints a version.
 *   c) smoke: `proot -w / /system/bin/sh -c 'echo proot-ok'` prints proot-ok.
 * Each candidate logs one proof/failure line; all-fail yields an honest error
 * card listing every logged reason. Never invokes su.
 */
data class ProotProvenance(
    val source: String,
    val sha256: String,
    val versionLine: String,
    val installedAt: Long,
    val elfProof: List<String>
) {
    fun toLines(): List<String> = listOf(
        "source=$source",
        "sha256=$sha256",
        "version=$versionLine",
        "installedAt=$installedAt"
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
                    elfProof = lines.filter { it.startsWith("elf=") }.map { it.removePrefix("elf=") }
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

    private const val HTTP_TIMEOUT_MS = 30_000
    private const val MAX_CANDIDATE_BYTES = 16L * 1024 * 1024

    suspend fun acquire(appContext: Context): AcquireResult =
        withContext(Dispatchers.IO) { acquireOnIo(appContext) }

    private fun acquireOnIo(appContext: Context): AcquireResult {
        val filesDir = appContext.filesDir
        val reasons = mutableListOf<String>()
        val candidates: List<Pair<String, () -> ByteArray?>> = listOf(
            "alpine-apk" to { fetchAlpine(filesDir) },
            "ahmed-ndk" to { fetchAhmed() },
            "proot-static-build" to { fetchStaticBuild() },
            "bundled-asset" to { fetchBundled(appContext) }
        )
        candidates.forEach { (source, fetch) ->
            val (line, installed) = runCatching {
                gateCandidate(appContext, source) { fetch() }
            }.getOrElse { e ->
                ("$source: fetch crashed: ${e.message}" to null)
            }
            reasons.add(line)
            if (installed != null) return AcquireResult.Success(installed)
        }
        reasons.add(
            "build-script: local CI build only, not attempted on device " +
                "(see scripts/build-proot.sh)"
        )
        reasons.forEach { ContainerEngineLog.append(filesDir, "proot-acquire", it) }
        return AcquireResult.Failure(reasons)
    }

    private fun gateCandidate(
        appContext: Context,
        source: String,
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
        // 1) Stage to a temp file (exec needs a path).
        val tmp = File(filesDir, "bin/.proot-candidate")
        try {
            tmp.parentFile?.mkdirs()
            tmp.writeBytes(bytes)
            chmod700(tmp)
        } catch (e: Exception) {
            return ("$source: staging failed: ${e.message}" to null)
        }
        try {
            // a) ELF forensics.
            val elf = ElfProbe.inspect(tmp)
                ?: return ("$source: not an ELF binary" to null).also { tmp.delete() }
            val proof = elf.proofLines()
            val verdict = ElfProbe.verdict(elf)
            if (verdict != null) {
                tmp.delete()
                writeValidation(filesDir, source, proof, "", "rejected", ok = false)
                return ("$source: REJECTED: $verdict | ${proof.joinToString(" ")}" to null)
            }
            // b) proot -V.
            val (versionOut, versionErr, versionCode) = ExecCapture.run(
                listOf(tmp.absolutePath, "-V"), 10_000L
            )
            val versionLine = versionOut.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
            if (versionCode != 0 || !versionLine.contains("proot", ignoreCase = true)) {
                tmp.delete()
                writeValidation(filesDir, source, proof, versionLine, "proot -V exit=$versionCode", ok = false)
                return ("$source: REJECTED: proot -V exit=$versionCode " +
                    "stderr=${versionErr.take(200)} | ${proof.joinToString(" ")}" to null)
            }
            // c) smoke: real guest-less run through the binary itself.
            val (smokeOut, smokeErr, smokeCode) = ExecCapture.run(
                listOf(tmp.absolutePath, "-w", "/", "/system/bin/sh", "-c", "echo proot-ok"),
                15_000L
            )
            if (smokeCode != 0 || "proot-ok" !in smokeOut) {
                tmp.delete()
                writeValidation(filesDir, source, proof, versionLine, "smoke exit=$smokeCode", ok = false)
                return ("$source: REJECTED: smoke exit=$smokeCode " +
                    "stderr=${smokeErr.take(200)} | ${proof.joinToString(" ")}" to null)
            }
            // Winner: install, chmod, provenance.
            val dest = ProotBinary.binaryFile(filesDir)
            runCatching { if (dest.exists()) dest.delete() }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            chmod700(dest)
            val sha = Sha256.ofBytes(bytes)
            val provenance = ProotProvenance(
                source = source,
                sha256 = sha,
                versionLine = versionLine.take(120),
                installedAt = System.currentTimeMillis(),
                elfProof = proof
            )
            File(filesDir, PROVENANCE_FILE).apply {
                parentFile?.mkdirs()
                writeText(provenance.toLines().joinToString("\n", postfix = "\n"))
            }
            writeValidation(
                filesDir, source, proof, versionLine.take(120),
                "smoke=proot-ok exit=0", ok = true
            )
            ContainerEngineLog.append(
                filesDir, "proot-acquire",
                "$source: INSTALLED $versionLine sha=${sha.take(16)}…"
            )
            return ("$source: PASS ${proof.joinToString(" ")} | $versionLine | smoke=proot-ok" to provenance)
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
