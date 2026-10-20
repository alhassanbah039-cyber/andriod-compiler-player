package com.android.sandbox.compiler.model

import java.io.File

/**
 * Recognized project execution languages for offline sandboxed compilation.
 */
enum class ProjectLanguage(val token: String) {
    JAVA("JAVA"),
    KOTLIN("KOTLIN"),
    CPP("CPP"),
    C("C"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromToken(token: String): ProjectLanguage {
            val normalized = token.trim().uppercase()
            return entries.firstOrNull { it.token == normalized } ?: UNKNOWN
        }
    }
}

/**
 * Categorization of files within the sandbox project hierarchy.
 */
enum class SandboxFileType {
    GRADLE_BUILD,
    GRADLE_SETTINGS,
    MANIFEST_XML,
    LAYOUT_OR_RESOURCE_XML,
    SOURCE_KOTLIN,
    SOURCE_JAVA,
    SOURCE_CPP,
    SOURCE_C,
    GENERIC_TEXT;

    companion object {
        fun fromPath(relativePath: String): SandboxFileType {
            val normalized = relativePath.replace("\\", "/").lowercase()
            return when {
                normalized.endsWith("settings.gradle") || normalized.endsWith("settings.gradle.kts") -> GRADLE_SETTINGS
                normalized.endsWith("build.gradle") || normalized.endsWith("build.gradle.kts") -> GRADLE_BUILD
                normalized.endsWith("androidmanifest.xml") -> MANIFEST_XML
                normalized.endsWith(".xml") -> LAYOUT_OR_RESOURCE_XML
                normalized.endsWith(".kt") || normalized.endsWith(".kts") -> SOURCE_KOTLIN
                normalized.endsWith(".java") -> SOURCE_JAVA
                normalized.endsWith(".cpp") || normalized.endsWith(".cc") || normalized.endsWith(".cxx") -> SOURCE_CPP
                normalized.endsWith(".c") || normalized.endsWith(".h") || normalized.endsWith(".hpp") -> SOURCE_C
                else -> GENERIC_TEXT
            }
        }
    }
}

/**
 * Metadata extracted from payload headers (e.g., [META: LANGUAGE=JAVA, NAME=CameraSandbox]).
 */
data class ProjectMetadata(
    val language: ProjectLanguage = ProjectLanguage.JAVA,
    val explicitName: String? = null,
    val extraProperties: Map<String, String> = emptyMap()
)

/**
 * In-memory representation of an individual file in a sandbox project.
 */
data class SandboxFile(
    val relativePath: String,
    val content: String,
    val fileType: SandboxFileType = SandboxFileType.fromPath(relativePath),
    val sizeBytes: Long = content.toByteArray(Charsets.UTF_8).size.toLong()
)

/**
 * Comprehensive representation of an on-disk sandbox project.
 */
data class SandboxProject(
    val id: String,
    val name: String,
    val rootDirectory: File,
    val metadata: ProjectMetadata,
    val files: List<SandboxFile>,
    val lastModifiedEpochMs: Long = System.currentTimeMillis()
) {
    val settingsGradleFile: SandboxFile?
        get() = files.firstOrNull { it.fileType == SandboxFileType.GRADLE_SETTINGS }

    val rootBuildGradleFile: SandboxFile?
        get() = files.firstOrNull { 
            val p = it.relativePath.replace("\\", "/")
            (p == "build.gradle" || p == "build.gradle.kts") 
        }

    val appBuildGradleFile: SandboxFile?
        get() = files.firstOrNull { 
            val p = it.relativePath.replace("\\", "/")
            (p == "app/build.gradle" || p == "app/build.gradle.kts") 
        }

    val manifestFile: SandboxFile?
        get() = files.firstOrNull { it.fileType == SandboxFileType.MANIFEST_XML }

    val sourceFiles: List<SandboxFile>
        get() = files.filter { 
            it.fileType in listOf(
                SandboxFileType.SOURCE_KOTLIN,
                SandboxFileType.SOURCE_JAVA,
                SandboxFileType.SOURCE_CPP,
                SandboxFileType.SOURCE_C
            )
        }
}

/**
 * Validation diagnostics model.
 */
sealed class ValidationError(val message: String, val filePath: String) {
    data class UnbalancedBrackets(
        val path: String,
        val line: Int,
        val column: Int,
        val bracketType: String,
        val detail: String
    ) : ValidationError("Unbalanced $bracketType in $path at line $line, col $column: $detail", path)

    data class UnclosedLiteral(
        val path: String,
        val line: Int,
        val column: Int,
        val literalType: String
    ) : ValidationError("Unclosed $literalType literal in $path at line $line, col $column", path)

    data class DisallowedDependency(
        val path: String,
        val line: Int,
        val dependencyCoordinate: String
    ) : ValidationError("Forbidden dependency '$dependencyCoordinate' detected in $path at line $line", path)

    data class StructuralError(
        val path: String,
        val error: String
    ) : ValidationError("Structural schema error in $path: $error", path)
}

data class ValidationReport(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList()
)
