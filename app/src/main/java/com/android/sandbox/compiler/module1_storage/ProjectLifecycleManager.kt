package com.android.sandbox.compiler.module1_storage

import android.content.Context
import com.android.sandbox.compiler.model.ProjectLanguage
import com.android.sandbox.compiler.model.ProjectMetadata
import com.android.sandbox.compiler.model.SandboxFile
import com.android.sandbox.compiler.model.SandboxProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-level orchestration manager coordinating payload parsing, project sanitization,
 * disk persistence, and project lifecycle states across background IO threads.
 */
class ProjectLifecycleManager(private val context: Context) {

    private val projectManager = AndroidSandboxProjectManager(context)

    /**
     * Ingests a raw copy-pasted payload string, parses the stream, sanitizes project identification,
     * fills in missing standard project topology components, and writes permanently to storage.
     */
    suspend fun importProjectFromPayload(
        rawPayload: String,
        fallbackName: String? = null
    ): Result<SandboxProject> = withContext(Dispatchers.IO) {
        try {
            val parsed = ProjectPayloadParser.parse(rawPayload)
            
            // Resolve project name priority: payload meta -> fallback -> default timestamp
            val rawName = parsed.metadata.explicitName ?: fallbackName ?: "project_${System.currentTimeMillis()}"
            val sanitizedName = projectManager.sanitizeProjectName(rawName)

            val effectiveMetadata = parsed.metadata.copy(explicitName = sanitizedName)

            val savedProject = projectManager.saveProject(
                sanitizedName = sanitizedName,
                metadata = effectiveMetadata,
                files = parsed.files
            )

            Result.success(savedProject)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lists all saved projects from disk asynchronously.
     */
    suspend fun getAllProjects(): List<SandboxProject> = withContext(Dispatchers.IO) {
        projectManager.listProjects()
    }

    /**
     * Reloads an existing project by its sanitized ID/folder name.
     */
    suspend fun getProject(projectId: String): SandboxProject? = withContext(Dispatchers.IO) {
        projectManager.reloadProject(projectId)
    }

    /**
     * Permanently deletes a project from disk.
     */
    suspend fun deleteProject(projectId: String): Boolean = withContext(Dispatchers.IO) {
        projectManager.deleteProject(projectId)
    }

    /**
     * Updates an existing project's files or adds a new file to it.
     */
    suspend fun updateProjectFile(
        projectId: String,
        file: SandboxFile
    ): Result<SandboxProject> = withContext(Dispatchers.IO) {
        try {
            val existing = projectManager.reloadProject(projectId)
                ?: return@withContext Result.failure(IllegalArgumentException("Project not found: $projectId"))
            
            val updatedFileList = existing.files
                .filterNot { it.relativePath == file.relativePath }
                .toMutableList()
            updatedFileList.add(file)

            val saved = projectManager.saveProject(
                sanitizedName = projectId,
                metadata = existing.metadata,
                files = updatedFileList
            )
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
