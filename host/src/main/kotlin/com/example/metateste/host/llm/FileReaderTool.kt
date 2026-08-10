package com.example.metateste.host.llm

import java.io.File
import java.io.FileNotFoundException

/**
 * Reads a file for the LLM brain's `read_file` tool, sandboxed to [rootDir] — never outside it,
 * even via `..` traversal or a symlink pointing out.
 */
class FileReaderTool(private val rootDir: File) {

    fun read(relativePath: String, maxBytes: Int = 100_000): Result<String> {
        if (File(relativePath).isAbsolute) {
            return Result.failure(IllegalArgumentException("caminho fora da área permitida: $relativePath"))
        }

        val root = rootDir.canonicalFile
        val target = File(root, relativePath).canonicalFile

        if (target != root && !target.path.startsWith(root.path + File.separator)) {
            return Result.failure(IllegalArgumentException("caminho fora da área permitida: $relativePath"))
        }
        if (!target.isFile) {
            return Result.failure(FileNotFoundException("arquivo não encontrado: $relativePath"))
        }
        if (target.length() > maxBytes) {
            return Result.failure(IllegalArgumentException("arquivo maior que o limite de $maxBytes bytes: $relativePath"))
        }
        return runCatching { target.readText(Charsets.UTF_8) }
    }
}
