package com.example.onnlauncher

import java.io.File
import java.security.MessageDigest

/** Finds files whose content is identical, whatever they are called. */
object Dedupe {
    private val cache = HashMap<String, Pair<Long, String>>() // path -> (size+mtime stamp, hash)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    private fun hashOf(file: File): String {
        val stamp = file.length() * 31 + file.lastModified()
        val hit = cache[file.path]
        if (hit != null && hit.first == stamp) return hit.second
        return sha256(file).also { cache[file.path] = stamp to it }
    }

    /** The name of another file in [dir] with the same content as [candidate], or null. */
    fun findDuplicate(dir: File, candidate: File, ignoreName: String? = null): String? {
        val size = candidate.length()
        val others = dir.listFiles { f ->
            f.isFile && f.name != candidate.name && f.name != ignoreName &&
                !f.name.endsWith(".part") && f.length() == size
        } ?: return null
        if (others.isEmpty()) return null
        val mine = hashOf(candidate)
        return others.firstOrNull { hashOf(it) == mine }?.name
    }
}
