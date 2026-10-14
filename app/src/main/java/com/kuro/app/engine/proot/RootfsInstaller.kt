package com.kuro.app.engine.proot

import android.content.Context
import android.net.ConnectivityManager
import android.os.Environment
import android.os.StatFs
import com.kuro.app.core.system.StoragePaths
import com.kuro.app.ui.screens.terminal.DistroCatalog
import com.kuro.app.ui.screens.terminal.DistroEntry
import com.kuro.app.ui.screens.terminal.expectedBytesFor
import com.kuro.app.ui.screens.terminal.sha256For
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Step 5: real rootfs pipeline — Download -> Verify -> Extract -> Bootstrap.
 * All stages are real (OkHttp Range resume + retry, streaming SHA-256,
 * Commons Compress tar.xz with traversal guard, real bootstrap files).
 * Progress is measured, never faked. Cancel cleans the partial tarball.
 */
data class InstallSpec(
    val installId: String,
    val containerId: String,
    val containerName: String,
    val distroId: String,
    val shell: String,
    val bridgeEnabled: Boolean,
    val interfaceMode: String,
    val keepAlive: Boolean,
    val wakeLock: Boolean
)

sealed interface InstallStage {
    data object Idle : InstallStage
    data class Downloading(
        val bytes: Long,
        val totalBytes: Long?,
        val bytesPerSec: Long,
        val etaSec: Long?,
        /** Step 5d: true once the primary mirror failed and we continue on the official fallback. */
        val usedFallback: Boolean = false
    ) : InstallStage

    data class Verifying(val bytes: Long, val totalBytes: Long) : InstallStage
    data class Extracting(val bytesOut: Long, val files: Int) : InstallStage
    data object Bootstrapping : InstallStage
    data class Ready(val containerId: String) : InstallStage
    data class Failed(val failure: InstallFailure) : InstallStage
    data object Cancelled : InstallStage
}

object ContainerProvisioner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _stages = MutableStateFlow<Map<String, InstallStage>>(emptyMap())
    val stages: StateFlow<Map<String, InstallStage>> = _stages.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val specs = mutableMapOf<String, InstallSpec>()

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun stageOf(installId: String): InstallStage =
        _stages.value[installId] ?: InstallStage.Idle

    fun start(context: Context, spec: InstallSpec): String {
        val app = context.applicationContext
        specs[spec.installId] = spec
        jobs[spec.installId]?.cancel()
        setStage(spec.installId, InstallStage.Downloading(0, null, 0, null))
        com.kuro.app.sessions.SessionService.ensureProvisioning(app, spec.installId)
        jobs[spec.installId] = scope.launch {
            try {
                runInstall(app, spec)
            } catch (e: CancellationException) {
                cleanupPartial(app, spec)
                setStage(spec.installId, InstallStage.Cancelled)
                ContainerEngineLog.append(app.filesDir, "install", "${spec.installId}: cancelled by user")
                com.kuro.app.sessions.SessionService.refreshNotifications(app)
                throw e
            } catch (e: InstallHaltException) {
                // Stage already set to Failed by the failing step; just refresh.
                com.kuro.app.sessions.SessionService.refreshNotifications(app)
            } catch (e: Exception) {
                val failure = InstallFailure.Extract("unexpected: ${e.message ?: e.javaClass.simpleName}")
                setStage(spec.installId, InstallStage.Failed(failure))
                ContainerEngineLog.append(app.filesDir, "install", "${spec.installId}: FAILED ${failure.detail}")
                com.kuro.app.sessions.SessionService.refreshNotifications(app)
            }
        }
        return spec.installId
    }

    fun cancel(installId: String) {
        jobs.remove(installId)?.cancel()
    }

    fun retry(context: Context, installId: String) {
        val spec = specs[installId] ?: return
        start(context, spec)
    }

    fun forget(installId: String) {
        jobs.remove(installId)?.cancel()
        specs.remove(installId)
        _stages.update { it - installId }
    }

    private val completed = mutableSetOf<String>()

    /** Claim Ready completion exactly once (Room + store write). */
    @Synchronized
    fun claimReady(installId: String): InstallSpec? {
        if (!completed.add(installId)) return null
        return specs[installId]
    }

    fun activeInstallIds(): List<String> = _stages.value
        .filterValues { it !is InstallStage.Ready && it !is InstallStage.Failed && it !is InstallStage.Cancelled }
        .keys.toList()

    private fun setStage(id: String, stage: InstallStage) {
        _stages.update { it + (id to stage) }
    }

    private suspend fun runInstall(app: Context, spec: InstallSpec) {
        val distro = DistroCatalog.byId(spec.distroId)
            ?: throw IllegalStateException("unknown distro ${spec.distroId}")
        val filesDir = app.filesDir

        // Storage pre-check: tarball + ~4x working room.
        val need = distro.tarballBytes * 5
        val free = statFree()
        if (free in 1 until need) {
            setStage(
                spec.installId,
                InstallStage.Failed(
                    InstallFailure.Storage(
                        "need ${human(need)}, only ${human(free)} free"
                    )
                )
            )
            com.kuro.app.sessions.SessionService.refreshNotifications(app)
            return
        }

        // 1) Download (guarded resume + retry + official-mirror failover).
        // Throws InstallHaltException after setting the Failed stage.
        val outcome = downloadWithRetry(app, spec, distro)
        val tarball = outcome.tarball

        // 2) Verify (only when the active source publishes a checksum).
        val expectedSha = distro.sha256For(outcome.url)
        if (expectedSha != null) {
            setStage(spec.installId, InstallStage.Verifying(0, tarball.length()))
            val got = withContext(Dispatchers.IO) {
                Sha256.ofFile(tarball) { done ->
                    setStage(spec.installId, InstallStage.Verifying(done, tarball.length()))
                }
            }
            if (!got.equals(expectedSha, ignoreCase = true)) {
                tarball.delete()
                runCatching { metaFileFor(tarball).delete() }
                setStage(
                    spec.installId,
                    InstallStage.Failed(
                        InstallFailure.Checksum(
                            "expected ${expectedSha.take(16)}…, got ${got.take(16)}…"
                        )
                    )
                )
                ContainerEngineLog.append(filesDir, "install", "${spec.installId}: checksum mismatch")
                com.kuro.app.sessions.SessionService.refreshNotifications(app)
                return
            }
        } else {
            ContainerEngineLog.append(
                filesDir, "install",
                "${spec.installId}: no published checksum for ${outcome.shortSource()}; " +
                    "size-verified ${tarball.length()} B"
            )
        }

        // 3) Extract.
        val containerDir = StoragePaths.prootContainerDir(filesDir, spec.containerId)
        val rootfs = StoragePaths.prootRootfsDir(filesDir, spec.containerId)
        runCatching { containerDir.deleteRecursively() }
        setStage(spec.installId, InstallStage.Extracting(0, 0))
        val extractOk = withContext(Dispatchers.IO) {
            extractTarXz(app, spec, tarball, rootfs)
        }
        if (!extractOk) return // stage already set to Failed inside

        // 4) Bootstrap.
        setStage(spec.installId, InstallStage.Bootstrapping)
        val bootOk = withContext(Dispatchers.IO) {
            bootstrap(app, spec, rootfs)
        }
        if (!bootOk) return

        ContainerEngineLog.append(filesDir, "install", "${spec.installId}: ready (${spec.containerId})")
        setStage(spec.installId, InstallStage.Ready(spec.containerId))
        com.kuro.app.sessions.SessionService.refreshNotifications(app)
    }

    // ——— Download (Step 5d: guarded resume + mirror failover) ———

    /** Where the bytes came from and what was expected of them. */
    data class DownloadOutcome(
        val url: String,
        val tarball: File,
        val expectedBytes: Long,
        val usedFallback: Boolean
    ) {
        fun shortSource(): String =
            if (usedFallback) "official mirror" else "primary mirror"
    }

    /** HTTP error status as an exception so retry/failover can inspect it. */
    class HttpStatusException(val code: Int, url: String) :
        java.io.IOException("HTTP $code for $url")

    private fun tarballFor(cacheDir: File, distro: DistroEntry, url: String): File =
        StoragePaths.tarballFile(cacheDir, distro.id, url)

    private fun metaFileFor(tarball: File): File = File(tarball.absolutePath + ".meta")

    private fun writeMeta(tarball: File, url: String, expectedBytes: Long) {
        runCatching {
            metaFileFor(tarball).writeText("url=$url\nexpected=$expectedBytes\n")
        }
    }

    /**
     * Expected asset size: a HEAD request (follows redirects) wins; the
     * catalog constant is the fallback. Never returns <= 0 when the catalog
     * knows the size. Runs on the caller's IO context (blocking).
     */
    private fun resolveExpected(url: String, catalogBytes: Long): Long {
        val head = runCatching {
            val req = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Kuro-proot-installer")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
                    ?: resp.body?.contentLength()?.takeIf { it > 0 }
            }
        }.getOrNull()
        return head ?: catalogBytes
    }

    private suspend fun downloadWithRetry(
        app: Context,
        spec: InstallSpec,
        distro: DistroEntry
    ): DownloadOutcome {
        val tracker = DownloadPolicy.MirrorTracker(distro.fallbackUrl != null)
        var primaryAttempts = 0
        var fallbackAttempts = 0
        var lastError: Exception? = null
        while (true) {
            val usingFallback = tracker.usingFallback
            val url = if (usingFallback) distro.fallbackUrl!! else distro.tarballUrl
            val attemptsSoFar = if (usingFallback) fallbackAttempts else primaryAttempts
            if (attemptsSoFar >= 3) break
            if (usingFallback) fallbackAttempts++ else primaryAttempts++
            val attemptNo = attemptsSoFar + 1
            try {
                return attemptDownload(app, spec, distro, url, usingFallback)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpStatusException) {
                lastError = e
                ContainerEngineLog.append(
                    app.filesDir, "install",
                    "${spec.installId}: download attempt $attemptNo " +
                        "(${if (usingFallback) "official mirror" else "primary"}) " +
                        "failed: ${e.message}"
                )
                if (tracker.recordHttpFailure(e.code)) {
                    ContainerEngineLog.append(
                        app.filesDir, "install",
                        "${spec.installId}: primary mirror failed, switched to official mirror"
                    )
                    setStage(
                        spec.installId,
                        InstallStage.Downloading(0, null, 0, null, usedFallback = true)
                    )
                    continue
                }
                if (attemptsSoFar + 1 < 3) delay(1000L * attemptNo)
            } catch (e: Exception) {
                // Non-HTTP errors (timeout, DNS, reset): retry the same source,
                // never count toward mirror failover.
                lastError = e
                ContainerEngineLog.append(
                    app.filesDir, "install",
                    "${spec.installId}: download attempt $attemptNo failed: ${e.message}"
                )
                if (attemptsSoFar + 1 < 3) delay(1000L * attemptNo)
            }
        }
        val hint = if (tracker.usingFallback) " (official mirror)" else ""
        setStage(
            spec.installId,
            InstallStage.Failed(
                InstallFailure.Network(
                    (lastError?.message ?: "download failed") + hint
                )
            )
        )
        com.kuro.app.sessions.SessionService.refreshNotifications(app)
        throw InstallHaltException()
    }

    /**
     * One source, one attempt: resolve expectation, drop stale temps, GET
     * (ranged only into a strictly-partial temp), recover once from 416/415,
     * then verify completion size with one full re-download on mismatch.
     */
    private suspend fun attemptDownload(
        app: Context,
        spec: InstallSpec,
        distro: DistroEntry,
        url: String,
        usedFallback: Boolean
    ): DownloadOutcome {
        currentCoroutineContext().ensureActive()
        val tarball = tarballFor(app.cacheDir, distro, url)
        tarball.parentFile?.mkdirs()
        val catalogBytes = distro.expectedBytesFor(url)
        val expected = resolveExpected(url, catalogBytes)
        currentCoroutineContext().ensureActive()
        writeMeta(tarball, url, expected)

        if (tarball.exists()) {
            val len = tarball.length()
            if (DownloadPolicy.isStaleTemp(len, expected)) {
                // Never resume from a temp at/over the expected size — this
                // was the 416 root cause (stale offset >= real asset size).
                ContainerEngineLog.append(
                    app.filesDir, "install",
                    "${spec.installId}: stale temp ${len}B >= expected ${expected}B, deleting"
                )
                tarball.delete()
                runCatching { metaFileFor(tarball).delete() }
            }
        }
        val have = if (tarball.exists()) tarball.length() else 0L
        val resume = DownloadPolicy.shouldResume(have, expected)
        try {
            getOnce(app, spec, url, tarball, rangeFrom = if (resume) have else null,
                expected = expected, usedFallback = usedFallback)
        } catch (e: HttpStatusException) {
            if (resume && DownloadPolicy.isResumeRejected(e.code)) {
                // 416/415 = our offset is unsatisfiable: drop the temp and
                // retry ONCE with a plain GET on the same source.
                ContainerEngineLog.append(
                    app.filesDir, "install",
                    "${spec.installId}: HTTP ${e.code} for $url: stale resume offset " +
                        "$have, deleted temp, retrying plain GET"
                )
                tarball.delete()
                runCatching { metaFileFor(tarball).delete() }
                getOnce(app, spec, url, tarball, rangeFrom = null,
                    expected = expected, usedFallback = usedFallback)
            } else {
                throw e
            }
        }

        // Completion check: actual must equal expected, else corrupt → delete
        // and do ONE full re-download before calling it a failure.
        if (!DownloadPolicy.sizeMatches(tarball.length(), expected)) {
            ContainerEngineLog.append(
                app.filesDir, "install",
                "${spec.installId}: size mismatch after download: got ${tarball.length()}B, " +
                    "expected ${expected}B — full re-download once"
            )
            tarball.delete()
            runCatching { metaFileFor(tarball).delete() }
            getOnce(app, spec, url, tarball, rangeFrom = null,
                expected = expected, usedFallback = usedFallback)
            if (!DownloadPolicy.sizeMatches(tarball.length(), expected)) {
                runCatching {
                    tarball.delete()
                    metaFileFor(tarball).delete()
                }
                throw java.io.IOException(
                    "size mismatch: got ${tarball.length()} B, expected $expected B"
                )
            }
        }
        writeMeta(tarball, url, expected)
        return DownloadOutcome(url, tarball, expected, usedFallback)
    }

    private suspend fun getOnce(
        app: Context,
        spec: InstallSpec,
        url: String,
        tarball: File,
        rangeFrom: Long?,
        expected: Long,
        usedFallback: Boolean
    ) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Kuro-proot-installer")
            .apply { if (rangeFrom != null) header("Range", "bytes=$rangeFrom-") }
            .build()
        // Network on IO; progress callbacks hop to setStage (thread-safe).
        withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val code = response.code
                if (code != 200 && code != 206) {
                    throw HttpStatusException(code, url)
                }
                val append = code == 206 && rangeFrom != null
                if (code == 200 && rangeFrom != null) {
                    // Server ignored Range: restart from zero.
                    tarball.delete()
                }
                val body = response.body ?: throw java.io.IOException("empty body")
                val total = body.contentLength().let { len ->
                    when {
                        len > 0 && append -> rangeFrom!! + len
                        len > 0 -> len
                        expected > 0 -> expected
                        else -> null
                    }
                }
                val startBytes = if (append) rangeFrom!! else 0L
                FileOutputStream(tarball, append).use { out ->
                    val buf = ByteArray(128 * 1024)
                    var done = startBytes
                    var windowBytes = 0L
                    var windowStart = android.os.SystemClock.elapsedRealtime()
                    var lastEmit = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = body.byteStream().read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        windowBytes += n
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastEmit >= 250 || n < 0) {
                            val elapsed = (now - windowStart).coerceAtLeast(1L)
                            val speed = windowBytes * 1000L / elapsed
                            val eta = if (total != null && speed > 0) (total - done) / speed else null
                            setStage(
                                spec.installId,
                                InstallStage.Downloading(done, total, speed, eta, usedFallback)
                            )
                            lastEmit = now
                            windowBytes = 0L
                            windowStart = now
                        }
                    }
                    out.fd.sync()
                }
                setStage(
                    spec.installId,
                    InstallStage.Downloading(tarball.length(), total, 0, 0, usedFallback)
                )
            }
        }
    }

    /** Non-cancellation halt: the Failed stage is already set, just unwind. */
    private class InstallHaltException : Exception()

    private fun cleanupPartial(app: Context, spec: InstallSpec) {
        runCatching {
            val distro = DistroCatalog.byId(spec.distroId)
            if (distro != null) {
                // Drop every cached partial for this distro (both mirrors +
                // metadata sidecars); the next run starts clean, never 416.
                tarballFor(app.cacheDir, distro, distro.tarballUrl).delete()
                runCatching {
                    metaFileFor(tarballFor(app.cacheDir, distro, distro.tarballUrl)).delete()
                }
                val fb = distro.fallbackUrl
                if (fb != null) {
                    tarballFor(app.cacheDir, distro, fb).delete()
                    runCatching { metaFileFor(tarballFor(app.cacheDir, distro, fb)).delete() }
                }
            }
            StoragePaths.prootContainerDir(app.filesDir, spec.containerId).deleteRecursively()
        }
    }

    // ——— Extract ———

    private fun extractTarXz(
        app: Context,
        spec: InstallSpec,
        tarball: File,
        rootfs: File
    ): Boolean {
        val staging = File(rootfs.parentFile, rootfs.name + ".staging")
        runCatching { staging.deleteRecursively() }
        runCatching { rootfs.deleteRecursively() }
        staging.mkdirs()
        var bytesOut = 0L
        var files = 0
        var skipped = 0
        try {
            BufferedInputStream(FileInputStream(tarball)).use { fileIn ->
                // Step 5d: official fallbacks ship gzip (Alpine minirootfs,
                // Ubuntu ubuntu-base) or xz (proot-distro, Kali, Fedora OCI);
                // sniff the magic instead of assuming xz.
                OciLayout.openDecompressed(fileIn).use { decIn ->
                    TarArchiveInputStream(decIn).use { tarIn ->
                        var entry = tarIn.nextEntry
                        while (entry != null) {
                            val target = TarPathGuard.resolve(staging, (entry as TarArchiveEntry).name)
                            if (target == null) {
                                fail(spec, app, "blocked path: ${(entry as TarArchiveEntry).name.take(120)}")
                                runCatching { staging.deleteRecursively() }
                                return false
                            }
                            when {
                                entry.isDirectory -> runCatching { target.mkdirs() }
                                entry.isSymbolicLink -> {
                                    runCatching {
                                        target.parentFile?.mkdirs()
                                        runCatching { Files.deleteIfExists(target.toPath()) }
                                        Files.createSymbolicLink(
                                            target.toPath(),
                                            java.nio.file.Paths.get(entry.linkName)
                                        )
                                        files++
                                    }.onFailure {
                                        skipped++
                                    }
                                }
                                entry.isLink -> {
                                    runCatching {
                                        val linkTarget = TarPathGuard.resolve(staging, entry.linkName)
                                            ?: throw java.io.IOException("bad link ${entry.linkName}")
                                        target.parentFile?.mkdirs()
                                        Files.createLink(target.toPath(), linkTarget.toPath())
                                        files++
                                    }.onFailure { skipped++ }
                                }
                                entry.isFile -> {
                                    try {
                                        target.parentFile?.mkdirs()
                                        FileOutputStream(target).use { out ->
                                            val buf = ByteArray(128 * 1024)
                                            var remaining = entry.size
                                            while (remaining > 0) {
                                                val want = minOf(buf.size.toLong(), remaining).toInt()
                                                val n = tarIn.read(buf, 0, want)
                                                if (n < 0) break
                                                out.write(buf, 0, n)
                                                remaining -= n
                                                bytesOut += n
                                            }
                                        }
                                        applyMode(target, entry.mode)
                                        files++
                                        if (files % 25 == 0) {
                                            setStage(
                                                spec.installId,
                                                InstallStage.Extracting(bytesOut, files)
                                            )
                                        }
                                    } catch (e: Exception) {
                                        if (e.message?.contains("space", ignoreCase = true) == true) {
                                            fail(spec, app, "extract ran out of space")
                                        } else {
                                            fail(spec, app, "extract I/O: ${e.message}")
                                        }
                                        runCatching { staging.deleteRecursively() }
                                        return false
                                    }
                                }
                                else -> skipped++ // sockets/devices/fifos: bound at runtime
                            }
                            entry = tarIn.nextEntry
                        }
                    }
                }
            }
            // Step 5d: official Fedora fallback ships as an OCI image layout —
            // materialize its layers into a plain rootfs. Primary tarballs
            // never contain a layout, so this is a strict no-op for them.
            if (OciLayout.isOciStaging(staging)) {
                ContainerEngineLog.append(
                    app.filesDir, "install",
                    "${spec.installId}: oci image layout detected, materializing layers"
                )
                val layerFiles = try {
                    OciLayout.materialize(staging)
                } catch (e: CancellationException) {
                    runCatching { staging.deleteRecursively() }
                    throw e
                } catch (e: Exception) {
                    fail(spec, app, "oci layers: ${e.message ?: e.javaClass.simpleName}")
                    runCatching { staging.deleteRecursively() }
                    return false
                }
                files += layerFiles
                ContainerEngineLog.append(
                    app.filesDir, "install",
                    "${spec.installId}: oci layers materialized ($layerFiles files)"
                )
            }
            // Collapse a single top-level wrapper dir (proot-distro + NetHunter).
            collapseWrapper(staging)
            // Move staging -> rootfs atomically-ish (same parent fs: rename).
            if (!staging.renameTo(rootfs)) {
                // Fallback: copy is unnecessary — rename within same dir works;
                // if it failed, the fs is likely full or broken.
                fail(spec, app, "could not finalize rootfs dir")
                runCatching { staging.deleteRecursively() }
                return false
            }
            ContainerEngineLog.append(
                app.filesDir, "install",
                "${spec.installId}: extracted $files files, ${human(bytesOut)}, skipped $skipped"
            )
            return true
        } catch (e: CancellationException) {
            runCatching { staging.deleteRecursively() }
            throw e
        } catch (e: Exception) {
            fail(spec, app, "extract: ${e.message ?: e.javaClass.simpleName}")
            runCatching { staging.deleteRecursively() }
            return false
        }
    }

    private fun collapseWrapper(staging: File) {
        val kids = staging.listFiles()?.toList() ?: return
        if (kids.size == 1 && kids[0].isDirectory) {
            val inner = kids[0]
            inner.listFiles()?.forEach { child ->
                val dest = File(staging, child.name)
                if (!child.renameTo(dest)) {
                    // Cross-device fallback (should not happen): copy then delete.
                    runCatching {
                        if (child.isDirectory) child.copyRecursively(dest) else child.copyTo(dest)
                        child.deleteRecursively()
                    }
                }
            }
            runCatching { inner.deleteRecursively() }
        }
    }

    private fun applyMode(target: File, mode: Int) {
        runCatching {
            // Precise permission restore; best-effort on filesystems without it.
            android.system.Os.chmod(target.absolutePath, mode and 0xFFF)
        }.onFailure {
            runCatching {
                if (mode and 0x49 != 0) target.setExecutable(true, false)
            }
        }
    }

    private fun fail(spec: InstallSpec, app: Context, detail: String) {
        val failure = if ("space" in detail.lowercase()) {
            InstallFailure.Storage(detail)
        } else {
            InstallFailure.Extract(detail)
        }
        setStage(spec.installId, InstallStage.Failed(failure))
        ContainerEngineLog.append(app.filesDir, "install", "${spec.installId}: FAILED $detail")
        com.kuro.app.sessions.SessionService.refreshNotifications(app)
    }

    // ——— Bootstrap ———

    private fun bootstrap(app: Context, spec: InstallSpec, rootfs: File): Boolean {
        return runCatching {
            writeResolvConf(app, rootfs)
            writeHosts(rootfs)
            writeTimezone(rootfs)
            File(rootfs, ".kuro-container-id").writeText(spec.containerId + "\n")
            ContainerEngineLog.append(app.filesDir, "install", "${spec.installId}: bootstrapped")
            true
        }.getOrElse { e ->
            fail(spec, app, "bootstrap: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    private fun writeResolvConf(app: Context, rootfs: File) {
        val servers = runCatching {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            val props = cm?.getLinkProperties(cm.activeNetwork)
            props?.dnsServers?.map { it.hostAddress }?.filter { a -> a.contains('.') || a.contains(':') }
        }.getOrNull().orEmpty().take(3)
        val list = if (servers.isEmpty()) listOf("1.1.1.1", "8.8.8.8") else servers
        val etc = File(rootfs, "etc").also { it.mkdirs() }
        File(etc, "resolv.conf").writeText(
            list.joinToString("\n", postfix = "\n") { "nameserver $it" }
        )
    }

    private fun writeHosts(rootfs: File) {
        val etc = File(rootfs, "etc").also { it.mkdirs() }
        val hosts = File(etc, "hosts")
        if (!hosts.exists()) {
            hosts.writeText("127.0.0.1 localhost\n::1 localhost\n")
        }
    }

    private fun writeTimezone(rootfs: File) {
        val zoneId = runCatching { TimeZone.getDefault().id }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "UTC"
        val etc = File(rootfs, "etc").also { it.mkdirs() }
        runCatching { File(etc, "timezone").writeText(zoneId + "\n") }
        runCatching {
            val zoneinfo = File(rootfs, "usr/share/zoneinfo/$zoneId")
            if (zoneinfo.exists()) {
                val localtime = File(etc, "localtime")
                runCatching { Files.deleteIfExists(localtime.toPath()) }
                Files.createSymbolicLink(localtime.toPath(), zoneinfo.toPath())
            }
        }
    }

    private fun statFree(): Long {
        return runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        }.getOrDefault(0L)
    }

    private fun human(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.0f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.0f MB".format(mb)
        return "%.1f GB".format(mb / 1024.0)
    }
}
