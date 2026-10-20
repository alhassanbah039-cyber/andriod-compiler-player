package com.android.sandbox.compiler.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.sandbox.compiler.model.ProjectLanguage
import com.android.sandbox.compiler.model.SandboxProject
import com.android.sandbox.compiler.model.ValidationReport
import com.android.sandbox.compiler.module1_storage.ProjectLifecycleManager
import com.android.sandbox.compiler.module2_validator.ProjectSyntaxSecurityValidator
import com.android.sandbox.compiler.module2_validator.SecurityRuleBrokenException
import com.android.sandbox.compiler.module2_validator.SyntaxValidationException
import com.android.sandbox.compiler.module3_pipeline.CompilationArtifact
import com.android.sandbox.compiler.module3_pipeline.ExecutionOutput
import com.android.sandbox.compiler.module3_pipeline.JavaD8CompilationPipeline
import com.android.sandbox.compiler.module3_pipeline.NativeClangCompilationPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConsoleLogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    COMMAND
}

data class ConsoleLogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val level: ConsoleLogLevel = ConsoleLogLevel.INFO,
    val message: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestampMs))
}

/**
 * UI State model representing dashboard, projects list, validation, compilation, and execution state.
 */
data class SandboxUiState(
    val projects: List<SandboxProject> = emptyList(),
    val selectedProject: SandboxProject? = null,
    val rawPayloadInput: String = "",
    val isLoading: Boolean = false,
    val loadingProgressText: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val validationReport: ValidationReport? = null,
    val compilationArtifact: CompilationArtifact? = null,
    val executionOutput: ExecutionOutput? = null,
    val consoleLogs: List<ConsoleLogEntry> = emptyList()
)

/**
 * SandboxViewModel orchestrating disk I/O, security validation, and compilation pipelines
 * strictly across Dispatchers.IO to guarantee zero-jank UI performance.
 */
class SandboxViewModel(application: Application) : AndroidViewModel(application) {

    private val lifecycleManager = ProjectLifecycleManager(application)
    private val validator = ProjectSyntaxSecurityValidator()
    private val javaPipeline = JavaD8CompilationPipeline(application)
    private val nativePipeline = NativeClangCompilationPipeline(application)

    private val _uiState = MutableStateFlow(SandboxUiState())
    val uiState: StateFlow<SandboxUiState> = _uiState.asStateFlow()

    init {
        logConsole(ConsoleLogLevel.INFO, "Sandbox Host Engine initialized. Ready for stream payloads.")
        loadProjects()
    }

    private fun logConsole(level: ConsoleLogLevel, message: String) {
        val entry = ConsoleLogEntry(level = level, message = message)
        _uiState.update { current ->
            current.copy(consoleLogs = current.consoleLogs + entry)
        }
    }

    fun clearConsole() {
        _uiState.update { it.copy(consoleLogs = emptyList()) }
        logConsole(ConsoleLogLevel.INFO, "Terminal console cleared.")
    }

    /**
     * Loads or refreshes the persistent projects list from disk.
     */
    fun loadProjects() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { 
                it.copy(isLoading = true, loadingProgressText = "Querying saved projects from storage...") 
            }
            logConsole(ConsoleLogLevel.COMMAND, "$ sandbox list-projects")
            try {
                val list = lifecycleManager.getAllProjects()
                _uiState.update { current ->
                    current.copy(
                        projects = list,
                        isLoading = false,
                        selectedProject = current.selectedProject ?: list.firstOrNull()
                    )
                }
                logConsole(ConsoleLogLevel.SUCCESS, "Found ${list.size} saved project(s) on device storage.")
            } catch (e: Exception) {
                val err = "Failed to load projects: ${e.localizedMessage}"
                _uiState.update { it.copy(isLoading = false, errorMessage = err) }
                logConsole(ConsoleLogLevel.ERROR, err)
            }
        }
    }

    /**
     * Updates the raw payload text field input.
     */
    fun onPayloadChanged(text: String) {
        _uiState.update { it.copy(rawPayloadInput = text, errorMessage = null) }
    }

    /**
     * Ingests a raw copy-pasted multi-file string payload, validates security rules and syntax,
     * and saves to disk.
     */
    fun importAndValidateProject(rawPayload: String) {
        if (rawPayload.isBlank()) {
            val err = "Payload string cannot be empty."
            _uiState.update { it.copy(errorMessage = err) }
            logConsole(ConsoleLogLevel.ERROR, err)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { 
                it.copy(
                    isLoading = true, 
                    loadingProgressText = "Parsing stream & constructing project topology..."
                ) 
            }
            logConsole(ConsoleLogLevel.COMMAND, "$ sandbox import --stream")
            logConsole(ConsoleLogLevel.INFO, "Ingesting multi-file payload (${rawPayload.length} characters)...")

            // Phase 1: Storage & Topology Router
            val importResult = lifecycleManager.importProjectFromPayload(rawPayload)
            if (importResult.isFailure) {
                val err = importResult.exceptionOrNull()?.localizedMessage ?: "Unknown parse error"
                _uiState.update { it.copy(isLoading = false, errorMessage = "Import failed: $err") }
                logConsole(ConsoleLogLevel.ERROR, "Import failed: $err")
                return@launch
            }

            val project = importResult.getOrThrow()
            logConsole(ConsoleLogLevel.SUCCESS, "Extracted project: '${project.name}' [${project.metadata.language}] (${project.files.size} files)")

            // Phase 2: Syntax & Dependency Validator
            _uiState.update { 
                it.copy(loadingProgressText = "Validating syntax brackets & dependency security allowlist...") 
            }
            logConsole(ConsoleLogLevel.INFO, "Verifying syntax brackets {} / <> and security allowlist...")

            try {
                validator.validateStrict(project)
                val report = validator.validateAndReport(project)

                logConsole(ConsoleLogLevel.SUCCESS, "Syntax & Security checks passed with 0 violations.")
                logConsole(ConsoleLogLevel.SUCCESS, "Persisted project to: ${project.rootDirectory.path}")

                val updatedList = lifecycleManager.getAllProjects()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        projects = updatedList,
                        selectedProject = project,
                        validationReport = report,
                        successMessage = "Project '${project.name}' successfully parsed, validated, and saved!"
                    )
                }
            } catch (e: SecurityRuleBrokenException) {
                val err = "SECURITY RULE VIOLATION: ${e.localizedMessage}"
                _uiState.update { it.copy(isLoading = false, errorMessage = err) }
                logConsole(ConsoleLogLevel.ERROR, err)
                logConsole(ConsoleLogLevel.WARNING, "Security pipeline halted: External dependency is not allowlisted.")
            } catch (e: SyntaxValidationException) {
                val err = "SYNTAX VALIDATION ERROR: ${e.localizedMessage}"
                _uiState.update { it.copy(isLoading = false, errorMessage = err) }
                logConsole(ConsoleLogLevel.ERROR, err)
                logConsole(ConsoleLogLevel.WARNING, "Syntax check halted: Unbalanced braces/brackets or unclosed literal.")
            } catch (e: Exception) {
                val err = "Validation Error: ${e.localizedMessage}"
                _uiState.update { it.copy(isLoading = false, errorMessage = err) }
                logConsole(ConsoleLogLevel.ERROR, err)
            }
        }
    }

    /**
     * Executes the target compilation pipeline (JVM or Native Clang) on background thread.
     */
    fun runProject(project: SandboxProject) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    selectedProject = project,
                    isLoading = true,
                    loadingProgressText = "Compiling project '${project.name}' via ${project.metadata.language} pipeline...",
                    errorMessage = null
                )
            }

            val lang = project.metadata.language
            logConsole(ConsoleLogLevel.COMMAND, "$ sandbox build --project=${project.name} --lang=$lang")

            val pipeline = when (lang) {
                ProjectLanguage.CPP, ProjectLanguage.C -> {
                    logConsole(ConsoleLogLevel.INFO, "Routing to Pipeline B: Native Clang / ARM64 execution pathway...")
                    nativePipeline
                }
                else -> {
                    logConsole(ConsoleLogLevel.INFO, "Routing to Pipeline A: Java / D8 DEX execution pathway...")
                    javaPipeline
                }
            }

            // Step 1: Compile
            val compileResult = pipeline.compile(project)
            if (compileResult.isFailure) {
                val err = "Compilation Failed: ${compileResult.exceptionOrNull()?.localizedMessage}"
                _uiState.update { it.copy(isLoading = false, errorMessage = err) }
                logConsole(ConsoleLogLevel.ERROR, err)
                return@launch
            }

            val artifact = compileResult.getOrThrow()
            logConsole(ConsoleLogLevel.SUCCESS, "Compiled binary: ${artifact.primaryBinary.name} (${artifact.primaryBinary.length()} bytes) in ${artifact.compilationDurationMs} ms")

            // Log raw compiler output lines
            artifact.compilerOutputLog.lines().filter { it.isNotBlank() }.forEach { line ->
                logConsole(ConsoleLogLevel.INFO, "  [compiler] $line")
            }

            // Step 2: Execute in dynamic sandbox
            _uiState.update {
                it.copy(
                    loadingProgressText = "Executing compiled binary (${artifact.primaryBinary.name}) on hardware..."
                )
            }
            logConsole(ConsoleLogLevel.COMMAND, "$ sandbox exec ${artifact.primaryBinary.name}")

            val execResult = pipeline.execute(artifact)
            val output = execResult.getOrNull()

            if (output != null) {
                logConsole(ConsoleLogLevel.SUCCESS, "Hardware execution finished in ${output.executionDurationMs} ms.")
                if (output.stdOut.isNotBlank()) {
                    output.stdOut.lines().filter { it.isNotBlank() }.forEach { line ->
                        logConsole(ConsoleLogLevel.SUCCESS, "  [stdout] $line")
                    }
                }
                if (output.stdErr.isNotBlank()) {
                    output.stdErr.lines().filter { it.isNotBlank() }.forEach { line ->
                        logConsole(ConsoleLogLevel.WARNING, "  [stderr] $line")
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    compilationArtifact = artifact,
                    executionOutput = output,
                    successMessage = "Build and execution finished for '${project.name}'"
                )
            }
        }
    }

    /**
     * Deletes a project from disk and refreshes the state.
     */
    fun deleteProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { 
                it.copy(isLoading = true, loadingProgressText = "Removing project '$projectId'...") 
            }
            logConsole(ConsoleLogLevel.COMMAND, "$ sandbox rm -rf /projects/$projectId")
            lifecycleManager.deleteProject(projectId)
            val updated = lifecycleManager.getAllProjects()
            _uiState.update {
                it.copy(
                    projects = updated,
                    selectedProject = if (it.selectedProject?.id == projectId) updated.firstOrNull() else it.selectedProject,
                    isLoading = false,
                    successMessage = "Project deleted successfully"
                )
            }
            logConsole(ConsoleLogLevel.SUCCESS, "Deleted project '$projectId'.")
        }
    }

    /**
     * Reloads a specific project directory from disk.
     */
    fun reloadProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { 
                it.copy(isLoading = true, loadingProgressText = "Reloading project '$projectId'...") 
            }
            logConsole(ConsoleLogLevel.COMMAND, "$ sandbox reload /projects/$projectId")
            val reloaded = lifecycleManager.getProject(projectId)
            val list = lifecycleManager.getAllProjects()
            _uiState.update {
                it.copy(
                    projects = list,
                    selectedProject = reloaded ?: it.selectedProject,
                    isLoading = false,
                    successMessage = "Project reloaded from disk"
                )
            }
            logConsole(ConsoleLogLevel.SUCCESS, "Reloaded project '${reloaded?.name ?: projectId}' (${reloaded?.files?.size ?: 0} files).")
        }
    }

    fun selectProject(project: SandboxProject) {
        _uiState.update { it.copy(selectedProject = project, errorMessage = null) }
        logConsole(ConsoleLogLevel.INFO, "Selected project: '${project.name}' [${project.metadata.language}]")
    }

    fun dismissMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun loadSampleJava() {
        val sample = """
            [META: LANGUAGE=JAVA, NAME=CameraPreviewSandbox]
            [FILE: app/src/main/java/com/sandbox/MainActivity.java]
            package com.sandbox;

            public class MainActivity {
                public String getSandboxStatus() {
                    return "Java Offline Sandbox Active: Direct DEX classloader invoked.";
                }
            }
            [FILE: app/build.gradle.kts]
            plugins {
                id("com.android.application")
            }
            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.compose.ui:ui:1.6.0")
            }
        """.trimIndent()
        _uiState.update { it.copy(rawPayloadInput = sample) }
        logConsole(ConsoleLogLevel.INFO, "Loaded Java CameraPreviewSandbox template into payload input.")
    }

    fun loadSampleCpp() {
        val sample = """
            [META: LANGUAGE=CPP, NAME=NativeComputeSandbox]
            [FILE: app/src/main/cpp/native-engine.cpp]
            #include <string>

            extern "C" const char* getEngineSignature() {
                return "ARM64 Clang Native Execution Engine v1.0";
            }
            [FILE: app/build.gradle.kts]
            plugins {
                id("com.android.application")
            }
            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
            }
        """.trimIndent()
        _uiState.update { it.copy(rawPayloadInput = sample) }
        logConsole(ConsoleLogLevel.INFO, "Loaded C++ NativeComputeSandbox template into payload input.")
    }
}
