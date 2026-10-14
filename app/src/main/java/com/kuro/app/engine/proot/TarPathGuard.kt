package com.kuro.app.engine.proot

import java.io.File

/**
 * Step 5: tar path-traversal guard. A tarball entry resolves inside [root]
 * or it is rejected (null) and the install fails closed with an honest
 * extract error. Pure JVM — covered by unit tests.
 */
object TarPathGuard {
    fun resolve(root: File, entryName: String): File? {
        if (entryName.isBlank()) return null
        var name = entryName.trim().replace('\\', '/')
        // Reject absolute paths (posix + windows drive) outright.
        if (name.startsWith("/")) return null
        if (name.length >= 3 && name[1] == ':' && (name[2] == '/')) return null
        while (name.startsWith("./")) name = name.removePrefix("./")
        if (name.isBlank()) return null
        val parts = ArrayDeque<String>()
        for (segment in name.split('/')) {
            when {
                segment.isEmpty() || segment == "." -> Unit
                segment == ".." -> {
                    if (parts.isEmpty()) return null
                    parts.removeLast()
                }
                else -> parts.addLast(segment)
            }
        }
        if (parts.isEmpty()) return null
        val rootPath = root.toPath().normalize()
        val resolved = parts.fold(rootPath) { acc, part -> acc.resolve(part) }.normalize()
        if (!resolved.startsWith(rootPath)) return null
        return resolved.toFile()
    }
}
