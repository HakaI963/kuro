package com.kuro.app.engine.proot

/**
 * Step 5d: pure download-resume + mirror-failover policy.
 *
 * Root cause of the device-verified HTTP 416 ("Range Not Satisfiable"): the
 * old downloader sent `Range: bytes=<staleTempSize>-` whenever ANY temp file
 * existed — including temps whose size was >= the real asset size (stale,
 * oversized, or from a rotated upstream). The server correctly refuses a
 * start offset past EOF with 416.
 *
 * Rules (all pure, all unit-tested in DownloadPolicyTest):
 * - A Range header is sent ONLY when 0 < tempSize < expectedBytes, where
 *   expectedBytes comes from a HEAD request (refreshed per attempt) or the
 *   catalog constant. Otherwise: plain GET.
 * - A temp file with size >= expected is stale/corrupt: delete it (and its
 *   metadata sidecar) BEFORE any GET, then plain GET.
 * - HTTP 416/415 on a ranged GET: delete the temp, retry ONCE with plain GET.
 * - Completed size != expected: corrupt → delete, full re-download ONCE.
 * - Mirror failover: 2 HTTP 4xx/5xx failures on the primary → switch to the
 *   catalog fallbackUrl (when present). Non-HTTP errors (timeouts, DNS) keep
 *   retrying the current source; they do not count toward failover.
 *
 * Pure JVM, no Android APIs — safe for local unit tests.
 */
object DownloadPolicy {

    /** Resume only into a strictly-partial temp file. */
    fun shouldResume(tempSize: Long, expectedBytes: Long): Boolean =
        tempSize > 0 && expectedBytes > 0 && tempSize < expectedBytes

    /** A temp at/over the expected size must never be resumed from. */
    fun isStaleTemp(tempSize: Long, expectedBytes: Long): Boolean =
        expectedBytes > 0 && tempSize >= expectedBytes

    /** Range-request rejections that mean "drop the temp, start over". */
    fun isResumeRejected(httpCode: Int): Boolean = httpCode == 416 || httpCode == 415

    /** Any client/server error status (failover counting + retry). */
    fun isHttpFailure(httpCode: Int): Boolean = httpCode in 400..599

    /** Primary is abandoned after this many HTTP 4xx/5xx failures. */
    const val PRIMARY_HTTP_FAILURES_BEFORE_FAILOVER = 2

    fun shouldFailover(primaryHttpFailures: Int, hasFallback: Boolean): Boolean =
        hasFallback && primaryHttpFailures >= PRIMARY_HTTP_FAILURES_BEFORE_FAILOVER

    /** Completion check. Unknown expectation (<= 0) cannot fail. */
    fun sizeMatches(actualBytes: Long, expectedBytes: Long): Boolean =
        expectedBytes <= 0 || actualBytes == expectedBytes

    /**
     * Tiny state machine for one install's mirror tracking. Pure so the
     * threshold behavior is unit-testable without HTTP.
     */
    class MirrorTracker(val hasFallback: Boolean) {
        var primaryHttpFailures: Int = 0
            private set
        var usingFallback: Boolean = false
            private set

        /**
         * Record one failed attempt. Returns true on the attempt that flips
         * to the fallback (caller switches URL + surfaces the UI note).
         */
        fun recordHttpFailure(httpCode: Int): Boolean {
            if (usingFallback || !isHttpFailure(httpCode)) return false
            primaryHttpFailures++
            if (shouldFailover(primaryHttpFailures, hasFallback)) {
                usingFallback = true
                return true
            }
            return false
        }
    }
}
