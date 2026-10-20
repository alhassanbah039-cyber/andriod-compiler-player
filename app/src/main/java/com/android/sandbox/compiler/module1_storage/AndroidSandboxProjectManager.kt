package com.android.sandbox.compiler.module1_storage

import android.content.Context
import com.android.sandbox.compiler.model.ProjectLanguage
import com.android.sandbox.compiler.model.ProjectMetadata
import com.android.sandbox.compiler.model.SandboxFile
import com.android.sandbox.compiler.model.SandboxFileType
import com.android.sandbox.compiler.model.SandboxProject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Handles physical on-disk file management, standard topology completion,
 * and CRUD operations inside `context.filesDir + "/projects/[Sanitized_Project_Name]"`.
 */
class AndroidSandboxProjectManager(private val context: Context) {

    private val projectsRoot: File by lazy {
        File(context.filesDir, "projects").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * Sanitizes raw project names to prevent path traversal and shell injection.
     */
    fun sanitizeProjectName(rawName: String?): String {
        if (rawName.isNullOrBlank()) {
            return "project_${System.currentTimeMillis()}"
        }
        val sanitized = rawName.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_").trim('_')
        return sanitized.ifEmpty { "project_${System.currentTimeMillis()}" }
    }

    /**
     * Returns the dedicated directory for the given project.
     */
    fun getProjectDirectory(sanitizedName: String): File {
        return File(projectsRoot, sanitizeProjectName(sanitizedName))
    }

    /**
     * Persists a project and its file tree to disk, ensuring a standard Android topology.
     */
    @Throws(IOException::class)
    fun saveProject(
        sanitizedName: String,
        metadata: ProjectMetadata,
        files: List<SandboxFile>
    ): SandboxProject {
        val projectDir = getProjectDirectory(sanitizedName)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }

        // Map files into standard project hierarchy tree
        val completedFiles = ensureStandardProjectTopology(sanitizedName, metadata, files)

        // Write each file to disk
        for (file in completedFiles) {
            val targetFile = File(projectDir, file.relativePath)
            targetFile.parentFile?.mkdirs()
            targetFile.writeText(file.content, StandardCharsets.UTF_8)
        }

        // Write metadata sidecar file
        writeMetadataSidecar(projectDir, metadata)

        return reloadProject(sanitizedName)
            ?: throw IOException("Failed to reload project after saving: $sanitizedName")
    }

    /**
     * Ensures mandatory files exist:
     * - settings.gradle.kts
     * - build.gradle.kts
     * - app/build.gradle.kts
     * - app/src/main/AndroidManifest.xml
     */
    private fun ensureStandardProjectTopology(
        sanitizedName: String,
        metadata: ProjectMetadata,
        providedFiles: List<SandboxFile>
    ): List<SandboxFile> {
        val result = providedFiles.toMutableList()

        val hasSettings = result.any { 
            val p = it.relativePath.replace("\\", "/")
            p == "settings.gradle.kts" || p == "settings.gradle" 
        }
        if (!hasSettings) {
            result.add(
                SandboxFile(
                    relativePath = "settings.gradle.kts",
                    content = generateDefaultSettingsGradle(sanitizedName)
                )
            )
        }

        val hasRootBuild = result.any { 
            val p = it.relativePath.replace("\\", "/")
            p == "build.gradle.kts" || p == "build.gradle" 
        }
        if (!hasRootBuild) {
            result.add(
                SandboxFile(
                    relativePath = "build.gradle.kts",
                    content = generateDefaultRootBuildGradle()
                )
            )
        }

        val hasAppBuild = result.any { 
            val p = it.relativePath.replace("\\", "/")
            p == "app/build.gradle.kts" || p == "app/build.gradle" 
        }
        if (!hasAppBuild) {
            result.add(
                SandboxFile(
                    relativePath = "app/build.gradle.kts",
                    content = generateDefaultAppBuildGradle(sanitizedName, metadata)
                )
            )
        }

        val hasManifest = result.any { 
            val p = it.relativePath.replace("\\", "/")
            p.equals("app/src/main/AndroidManifest.xml", ignoreCase = true) 
        }
        if (!hasManifest) {
            result.add(
                SandboxFile(
                    relativePath = "app/src/main/AndroidManifest.xml",
                    content = generateDefaultManifest(sanitizedName)
                )
            )
        }

        return result
    }

    /**
     * Lists all saved projects on device storage.
     */
    fun listProjects(): List<SandboxProject> {
        val projectDirs = projectsRoot.listFiles { file -> file.isDirectory } ?: return emptyList()
        return projectDirs.mapNotNull { dir ->
            try {
                reloadProject(dir.name)
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.lastModifiedEpochMs }
    }

    /**
     * Reloads a project from disk given its sanitized directory name.
     */
    fun reloadProject(sanitizedName: String): SandboxProject? {
        val dir = getProjectDirectory(sanitizedName)
        if (!dir.exists() || !dir.isDirectory) return null

        val metadata = readMetadataSidecar(dir)
        val files = mutableListOf<SandboxFile>()

        dir.walkTopDown().forEach { file ->
            if (file.isFile && file.name != METADATA_FILENAME) {
                val relPath = file.relativeTo(dir).path.replace("\\", "/")
                try {
                    val content = file.readText(StandardCharsets.UTF_8)
                    files.add(SandboxFile(relativePath = relPath, content = content))
                } catch (_: Exception) {
                    // Ignore unreadable binary cache files
                }
            }
        }

        return SandboxProject(
            id = dir.name,
            name = metadata.explicitName ?: dir.name,
            rootDirectory = dir,
            metadata = metadata,
            files = files,
            lastModifiedEpochMs = dir.lastModified()
        )
    }

    /**
     * Deletes a project and all associated files from disk.
     */
    fun deleteProject(sanitizedName: String): Boolean {
        val dir = getProjectDirectory(sanitizedName)
        return if (dir.exists()) {
            dir.deleteRecursively()
        } else {
            false
        }
    }

    // --- Metadata Sidecar Persistence ---

    private fun writeMetadataSidecar(projectDir: File, metadata: ProjectMetadata) {
        val metaFile = File(projectDir, METADATA_FILENAME)
        val sb = StringBuilder()
        sb.append("LANGUAGE=").append(metadata.language.name).append("\n")
        metadata.explicitName?.let { sb.append("NAME=").append(it).append("\n") }
        for ((k, v) in metadata.extraProperties) {
            sb.append("PROP_").append(k).append("=").append(v).append("\n")
        }
        metaFile.writeText(sb.toString(), StandardCharsets.UTF_8)
    }

    private fun readMetadataSidecar(projectDir: File): ProjectMetadata {
        val metaFile = File(projectDir, METADATA_FILENAME)
        if (!metaFile.exists()) {
            return ProjectMetadata(language = ProjectLanguage.UNKNOWN, explicitName = projectDir.name)
        }

        var language = ProjectLanguage.UNKNOWN
        var name: String? = null
        val props = mutableMapOf<String, String>()

        metaFile.forEachLine(StandardCharsets.UTF_8) { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                when {
                    key == "LANGUAGE" -> language = ProjectLanguage.fromToken(value)
                    key == "NAME" -> name = value
                    key.startsWith("PROP_") -> props[key.removePrefix("PROP_")] = value
                }
            }
        }

        return ProjectMetadata(language = language, explicitName = name ?: projectDir.name, extraProperties = props)
    }

    // --- Default Hierarchy Templates ---

    private fun generateDefaultSettingsGradle(projectName: String): String = """
        rootProject.name = "$projectName"
        include(":app")
    """.trimIndent()

    private fun generateDefaultRootBuildGradle(): String = """
        plugins {
            id("com.android.application") version "8.2.0" apply false
            id("org.jetbrains.kotlin.android") version "1.9.22" apply false
        }
    """.trimIndent()

    private fun generateDefaultAppBuildGradle(projectName: String, metadata: ProjectMetadata): String {
        val cppBlock = if (metadata.language == ProjectLanguage.CPP || metadata.language == ProjectLanguage.C) {
            """
            externalNativeBuild {
                cmake {
                    path = file("src/main/cpp/CMakeLists.txt")
                }
            }
            """.trimIndent()
        } else ""

        return """
        plugins {
            id("com.android.application")
            id("org.jetbrains.kotlin.android")
        }

        android {
            namespace = "com.sandbox.${projectName.lowercase()}"
            compileSdk = 34

            defaultConfig {
                applicationId = "com.sandbox.${projectName.lowercase()}"
                minSdk = 26
                targetSdk = 34
                versionCode = 1
                versionName = "1.0"
            }
            $cppBlock
        }

        dependencies {
            implementation("androidx.core:core-ktx:1.12.0")
            implementation("androidx.activity:activity:1.8.2")
            implementation("androidx.compose.ui:ui:1.6.0")
        }
        """.trimIndent()
    }

    private fun generateDefaultManifest(projectName: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="com.sandbox.${projectName.lowercase()}">

            <application
                android:label="$projectName"
                android:supportsRtl="true">
                <activity
                    android:name=".MainActivity"
                    android:exported="true">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>
        </manifest>
    """.trimIndent()

    companion object {
        private const val METADATA_FILENAME = ".project_metadata.cfg"
    }
}
