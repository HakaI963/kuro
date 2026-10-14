package com.kuro.app.engine.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Step 5f: codeCache/cache executable locations + root-gated tmp fallback. */
class CodeCacheExecTest {

    @Test
    fun `codeCache dest nests files bin proot`() {
        val dest = ProotAcquisition.codeCacheDestFor(File("/data/data/com.kuro.app.debug/code_cache"))
        assertEquals(
            File("/data/data/com.kuro.app.debug/code_cache/files/bin/proot").absolutePath,
            dest.absolutePath
        )
    }

    @Test
    fun `cache dest nests bin proot`() {
        val dest = ProotAcquisition.cacheDestFor(File("/data/data/com.kuro.app.debug/cache"))
        assertEquals(
            File("/data/data/com.kuro.app.debug/cache/bin/proot").absolutePath,
            dest.absolutePath
        )
    }

    @Test
    fun `codeCache and cache dests differ`() {
        val root = File("/data/data/x")
        assertFalse(
            ProotAcquisition.codeCacheDestFor(File(root, "code_cache")) ==
                ProotAcquisition.cacheDestFor(File(root, "cache"))
        )
    }

    @Test
    fun `tmp fallback only with user flow plus granted root`() {
        assertTrue(ProotAcquisition.shouldAttemptTmpFallback(true, true))
        assertFalse(ProotAcquisition.shouldAttemptTmpFallback(false, true))
        assertFalse(ProotAcquisition.shouldAttemptTmpFallback(true, false))
        assertFalse(ProotAcquisition.shouldAttemptTmpFallback(false, false))
    }

    @Test
    fun `no-exec-dirs message is exact`() {
        assertEquals(
            "No executable directory found (noexec on codeCache and cache)",
            ProotAcquisition.NO_EXEC_DIR_MESSAGE
        )
    }
}
