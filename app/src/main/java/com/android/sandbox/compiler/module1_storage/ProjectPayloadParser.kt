package com.android.sandbox.compiler.module1_storage

import com.android.sandbox.compiler.model.ProjectLanguage
import com.android.sandbox.compiler.model.ProjectMetadata
import com.android.sandbox.compiler.model.SandboxFile
import java.util.regex.Pattern

/**
 * Parses raw stream payloads containing multi-file projects with [META: ...] and [FILE: ...] tags.
 */
object ProjectPayloadParser {

    private val META_PATTERN = Pattern.compile("^\\[META:\\s*(.+?)\\]$", Pattern.CASE_INSENSITIVE)
    private val FILE_HEADER_PATTERN = Pattern.compile("^\\[FILE:\\s*(.+?)\\]$", Pattern.CASE_INSENSITIVE)

    data class ParsedPayload(
        val metadata: ProjectMetadata,
        val files: List<SandboxFile>
    )

    /**
     * Parses the raw incoming string stream into typed metadata and a list of SandboxFiles.
     */
    fun parse(rawPayload: String): ParsedPayload {
        val lines = rawPayload.lines()
        var metadata = ProjectMetadata()
        val files = mutableListOf<SandboxFile>()

        var currentFilePath: String? = null
        val currentFileContent = StringBuilder()

        for (line in lines) {
            val trimmedLine = line.trim()

            // 1. Check for [META: ...] tag
            val metaMatcher = META_PATTERN.matcher(trimmedLine)
            if (metaMatcher.matches()) {
                val metaBody = metaMatcher.group(1) ?: ""
                metadata = parseMetadataBody(metaBody, metadata)
                continue
            }

            // 2. Check for [FILE: ...] tag
            val fileMatcher = FILE_HEADER_PATTERN.matcher(trimmedLine)
            if (fileMatcher.matches()) {
                // If a file was already accumulating, flush it
                if (currentFilePath != null) {
                    files.add(
                        SandboxFile(
                            relativePath = sanitizeRelativePath(currentFilePath),
                            content = currentFileContent.toString().trimEnd()
                        )
                    )
                    currentFileContent.clear()
                }

                currentFilePath = fileMatcher.group(1)?.trim()
                continue
            }

            // 3. File content line
            if (currentFilePath != null) {
                if (currentFileContent.isNotEmpty()) {
                    currentFileContent.append("\n")
                }
                currentFileContent.append(line)
            }
        }

        // Flush final file if open
        if (currentFilePath != null) {
            files.add(
                SandboxFile(
                    relativePath = sanitizeRelativePath(currentFilePath),
                    content = currentFileContent.toString().trimEnd()
                )
            )
        }

        return ParsedPayload(metadata = metadata, files = files)
    }

    private fun parseMetadataBody(body: String, currentMeta: ProjectMetadata): ProjectMetadata {
        val pairs = body.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        var language = currentMeta.language
        var name = currentMeta.explicitName
        val extra = currentMeta.extraProperties.toMutableMap()

        for (pair in pairs) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().uppercase()
                val value = parts[1].trim()
                when (key) {
                    "LANGUAGE", "LANG" -> language = ProjectLanguage.fromToken(value)
                    "NAME", "PROJECT" -> name = value
                    else -> extra[key] = value
                }
            }
        }
        return ProjectMetadata(
            language = language,
            explicitName = name,
            extraProperties = extra
        )
    }

    /**
     * Prevents directory traversal attacks (e.g., ../../../etc) and normalizes paths.
     */
    fun sanitizeRelativePath(path: String): String {
        var clean = path.replace("\\", "/").trim()
        while (clean.startsWith("/")) {
            clean = clean.substring(1)
        }
        val segments = clean.split("/").filter { it.isNotBlank() && it != "." }
        val safeSegments = mutableListOf<String>()
        for (segment in segments) {
            if (segment == "..") {
                if (safeSegments.isNotEmpty()) {
                    safeSegments.removeAt(safeSegments.lastIndex)
                }
            } else {
                safeSegments.add(segment)
            }
        }
        return if (safeSegments.isEmpty()) "unnamed_file.txt" else safeSegments.joinToString("/")
    }
}
