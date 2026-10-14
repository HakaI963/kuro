package com.kuro.app.engine.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 5d: the 416 root-cause rules — Range is sent ONLY into a strictly
 * partial temp; stale/oversize temps are deleted, never resumed.
 */
class DownloadPolicyTest {

    @Test
    fun `resume only into strictly partial temp`() {
        assertTrue(DownloadPolicy.shouldResume(100, 1000))
        assertTrue(DownloadPolicy.shouldResume(1, 3439136L))
        assertTrue(DownloadPolicy.shouldResume(3439135L, 3439136L))
    }

    @Test
    fun `never resume from empty missing or complete temp`() {
        assertFalse(DownloadPolicy.shouldResume(0, 1000)) // empty
        assertFalse(DownloadPolicy.shouldResume(1000, 1000)) // exact
        assertFalse(DownloadPolicy.shouldResume(1500, 1000)) // oversize (416 trigger)
        assertFalse(DownloadPolicy.shouldResume(100, 0)) // unknown expectation
        assertFalse(DownloadPolicy.shouldResume(100, -1))
    }

    @Test
    fun `stale temp is size at or over expectation`() {
        assertTrue(DownloadPolicy.isStaleTemp(1000, 1000))
        assertTrue(DownloadPolicy.isStaleTemp(5000, 3439136L)) // the 416 case
        assertFalse(DownloadPolicy.isStaleTemp(999, 1000))
        assertFalse(DownloadPolicy.isStaleTemp(0, 1000))
        assertFalse(DownloadPolicy.isStaleTemp(5000, 0)) // unknown: not stale
    }

    @Test
    fun `416 and 415 mean drop temp and plain retry`() {
        assertTrue(DownloadPolicy.isResumeRejected(416))
        assertTrue(DownloadPolicy.isResumeRejected(415))
        assertFalse(DownloadPolicy.isResumeRejected(500))
        assertFalse(DownloadPolicy.isResumeRejected(404))
        assertFalse(DownloadPolicy.isResumeRejected(200))
    }

    @Test
    fun `http failure is any 4xx 5xx`() {
        assertTrue(DownloadPolicy.isHttpFailure(400))
        assertTrue(DownloadPolicy.isHttpFailure(404))
        assertTrue(DownloadPolicy.isHttpFailure(416))
        assertTrue(DownloadPolicy.isHttpFailure(500))
        assertTrue(DownloadPolicy.isHttpFailure(503))
        assertFalse(DownloadPolicy.isHttpFailure(200))
        assertFalse(DownloadPolicy.isHttpFailure(206))
        assertFalse(DownloadPolicy.isHttpFailure(301))
    }

    @Test
    fun `failover flips on the second http failure when fallback exists`() {
        val tracker = DownloadPolicy.MirrorTracker(hasFallback = true)
        assertFalse(tracker.usingFallback)
        // First 500: stay on primary.
        assertFalse(tracker.recordHttpFailure(500))
        assertFalse(tracker.usingFallback)
        assertEquals(1, tracker.primaryHttpFailures)
        // Second 500: flip to fallback.
        assertTrue(tracker.recordHttpFailure(503))
        assertTrue(tracker.usingFallback)
        // Later failures on the fallback never re-flip.
        assertFalse(tracker.recordHttpFailure(500))
        assertTrue(tracker.usingFallback)
    }

    @Test
    fun `no fallback means never flip`() {
        val tracker = DownloadPolicy.MirrorTracker(hasFallback = false)
        assertFalse(tracker.recordHttpFailure(500))
        assertFalse(tracker.recordHttpFailure(500))
        assertFalse(tracker.recordHttpFailure(500))
        assertFalse(tracker.usingFallback)
        assertEquals(0, tracker.primaryHttpFailures)
    }

    @Test
    fun `non-http errors never count toward failover`() {
        val tracker = DownloadPolicy.MirrorTracker(hasFallback = true)
        // Timeouts/DNS surface as exceptions, not codes — recordHttpFailure
        // is only called with real statuses; 2xx/3xx never flip.
        assertFalse(tracker.recordHttpFailure(200))
        assertFalse(tracker.recordHttpFailure(206))
        assertEquals(0, tracker.primaryHttpFailures)
        assertFalse(tracker.usingFallback)
    }

    @Test
    fun `mixed 4xx then 5xx still flips at two`() {
        val tracker = DownloadPolicy.MirrorTracker(hasFallback = true)
        assertFalse(tracker.recordHttpFailure(403))
        assertTrue(tracker.recordHttpFailure(500))
        assertTrue(tracker.usingFallback)
    }

    @Test
    fun `completion size must equal expectation`() {
        assertTrue(DownloadPolicy.sizeMatches(3439136L, 3439136L))
        assertFalse(DownloadPolicy.sizeMatches(3439135L, 3439136L)) // truncated
        assertFalse(DownloadPolicy.sizeMatches(0, 3439136L)) // empty
        assertFalse(DownloadPolicy.sizeMatches(4000000L, 3439136L)) // oversize
        assertTrue(DownloadPolicy.sizeMatches(123, 0)) // unknown: cannot fail
    }

    @Test
    fun `simulated 416 recovery decision`() {
        // Device case: stale temp (offset >= asset) + server answers 416.
        val tempSize = 5000000L
        val expected = 3439136L
        assertFalse(
            DownloadPolicy.shouldResume(tempSize, expected)
        )
        assertTrue(DownloadPolicy.isStaleTemp(tempSize, expected))
        assertTrue(DownloadPolicy.isResumeRejected(416))
    }

    @Test
    fun `simulated mid-download resume decision`() {
        // Killed network at 1 MB of 3.4 MB: temp is strictly partial.
        val tempSize = 1048576L
        val expected = 3439136L
        assertTrue(DownloadPolicy.shouldResume(tempSize, expected))
        assertFalse(DownloadPolicy.isStaleTemp(tempSize, expected))
    }
}
