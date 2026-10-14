package com.kuro.app.engine.proot

import java.io.File
import java.io.RandomAccessFile

/**
 * Step 5c: ELF forensics. Before any candidate binary is executed, its ELF
 * header + program headers are inspected and logged: EI_CLASS/EI_DATA,
 * whether PT_INTERP exists (plus its path), machine and file type.
 *
 * Why: the previously shipped asset was ET_EXEC (non-PIE). Android only
 * executes PIE (ET_DYN); a PT_INTERP pointing at a Linux loader
 * (/lib/ld-*.so.*) can likewise NEVER exec on Android. Both are rejected
 * here with the reason logged — forever, in kuro-engine.log + diagnostics.
 *
 * Pure JVM (reads at most 64 KB) — covered by unit tests with crafted headers.
 */
data class ElfReport(
    val eiClass: Int,
    val eiData: Int,
    val eType: Int,
    val eMachine: Int,
    val interpPath: String?
) {
    val is64Bit: Boolean get() = eiClass == 2
    val isLittleEndian: Boolean get() = eiData == 1
    val isAarch64: Boolean get() = eMachine == 183
    val isPie: Boolean get() = eType == 3
    val hasInterp: Boolean get() = interpPath != null

    fun proofLines(): List<String> = listOf(
        "EI_CLASS=$eiClass (${if (is64Bit) "64-bit" else "32-bit"})",
        "EI_DATA=$eiData (${if (isLittleEndian) "LE" else "BE"})",
        "machine=$eMachine (${if (isAarch64) "AArch64" else "other"})",
        "type=$eType (${if (isPie) "PIE" else "non-PIE"})",
        "PT_INTERP=" + (interpPath ?: "absent (static)")
    )

    companion object {
        const val ET_EXEC = 2
        const val ET_DYN = 3
        const val EM_AARCH64 = 183
        const val PT_INTERP = 3
        // Retained for log/test reference only — Step 5e rejects every
        // PT_INTERP, including these; see verdict().
        const val ANDROID_LINKER_64 = "/system/bin/linker64"
        const val ANDROID_LINKER = "/system/bin/linker"
    }
}

object ElfProbe {
    private const val MAX_READ = 64 * 1024L

    fun inspect(file: File): ElfReport? {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 64) return null
                val ident = ByteArray(16)
                raf.readFully(ident)
                if (!(ident[0] == 0x7F.toByte() && ident[1] == 'E'.code.toByte() &&
                        ident[2] == 'L'.code.toByte() && ident[3] == 'F'.code.toByte())
                ) return null
                val eiClass = ident[4].toInt() and 0xFF
                val eiData = ident[5].toInt() and 0xFF
                if (eiClass != 2) return ElfReport(eiClass, eiData, 0, 0, null)
                val le = eiData == 1
                val eType = raf.readU16(16, le)
                val eMachine = raf.readU16(18, le)
                val phoff = raf.readU64(32, le)
                val phentsize = raf.readU16(54, le)
                var phnum = raf.readU16(56, le)
                if (phentsize < 56 || phoff <= 0) {
                    return ElfReport(eiClass, eiData, eType, eMachine, null)
                }
                // PN_XNUM extended numbering: ignore, cap the walk instead.
                if (phnum > 32) phnum = 32
                var interp: String? = null
                var i = 0
                while (i < phnum) {
                    val base = phoff + i.toLong() * phentsize
                    if (base + 56 > MAX_READ || base + 56 > raf.length()) break
                    val pType = raf.readU32(base, le)
                    if (pType == ElfReport.PT_INTERP) {
                        val pOffset = raf.readU64(base + 8, le)
                        val pFilesz = raf.readU64(base + 32, le)
                        if (pOffset in 1 until MAX_READ && pFilesz in 1..512) {
                            val raw = ByteArray(pFilesz.toInt().coerceAtMost(512))
                            raf.seek(pOffset)
                            raf.readFully(raw)
                            interp = String(raw, Charsets.UTF_8).trimEnd('\u0000').trim()
                                .takeIf { it.isNotEmpty() }
                        }
                        break
                    }
                    i++
                }
                ElfReport(eiClass, eiData, eType, eMachine, interp)
            }
        }.getOrNull()
    }

    /**
     * Android execution verdict: null = runnable, otherwise the logged reason.
     * Step 5e: ANY PT_INTERP is an instant reject — only truly static
     * binaries (interpreter absent) are allowed. Even the Android linker
     * path is rejected: a dynamically-linked candidate can never be trusted
     * to exec from an app context, and the proof log shows the exact state.
     */
    fun verdict(report: ElfReport): String? {
        if (!report.is64Bit) return "not a 64-bit ELF (EI_CLASS=${report.eiClass})"
        if (!report.isAarch64) {
            return "machine ${report.eMachine}, need EM_AARCH64(183)"
        }
        if (!report.isPie) {
            return "non-PIE executable (ET_EXEC); Android only executes PIE"
        }
        val interp = report.interpPath
        if (interp != null) {
            return "PT_INTERP present ($interp): only fully static binaries can exec on Android"
        }
        return null
    }

    private fun RandomAccessFile.readU16(at: Long, le: Boolean): Int {
        seek(at)
        val b0 = read() and 0xFF
        val b1 = read() and 0xFF
        return if (le) b0 or (b1 shl 8) else (b0 shl 8) or b1
    }

    private fun RandomAccessFile.readU32(at: Long, le: Boolean): Int {
        seek(at)
        val b = IntArray(4) { read() and 0xFF }
        return if (le) {
            b[0] or (b[1] shl 8) or (b[2] shl 16) or (b[3] shl 24)
        } else {
            (b[0] shl 24) or (b[1] shl 16) or (b[2] shl 8) or b[3]
        }
    }

    private fun RandomAccessFile.readU64(at: Long, le: Boolean): Long {
        seek(at)
        val b = LongArray(8) { (read() and 0xFF).toLong() }
        var v = 0L
        if (le) {
            for (i in 7 downTo 0) v = (v shl 8) or b[i]
        } else {
            for (i in 0..7) v = (v shl 8) or b[i]
        }
        return v
    }
}
