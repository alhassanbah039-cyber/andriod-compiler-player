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

enum class DashboardTab {
    PROJECTS,
    IMPORT_NEW,
    EXECUTION_CANVAS
}

data class DashboardUiState(
    val projects: List<SandboxProject> = emptyList(),
    val selectedProject: SandboxProject? = null,
    val rawPayloadInput: String = "",
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val currentTab: DashboardTab = DashboardTab.PROJECTS,
    val validationReport: ValidationReport? = null,
    val compilationArtifact: CompilationArtifact? = null,
    val executionOutput: ExecutionOutput? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val lifecycleManager = ProjectLifecycleManager(application)
    private val validator = ProjectSyntaxSecurityValidator()
    private val javaPipeline = JavaD8CompilationPipeline(application)
    private val nativePipeline = NativeClangCompilationPipeline(application)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    fun selectTab(tab: DashboardTab) {
        _uiState.update { it.copy(currentTab = tab, errorMessage = null) }
    }

    fun onPayloadInputChanged(text: String) {
        _uiState.update { it.copy(rawPayloadInput = text, errorMessage = null) }
    }

    fun loadProjects() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Loading projects from disk...") }
            try {
                val list = lifecycleManager.getAllProjects()
                _uiState.update { 
                    it.copy(
                        projects = list,
                        isLoading = false,
                        selectedProject = it.selectedProject ?: list.firstOrNull()
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(isLoading = false, errorMessage = "Error loading projects: ${e.localizedMessage}") 
                }
            }
        }
    }

    fun importAndValidateProject(rawPayload: String) {
        if (rawPayload.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Payload string cannot be empty") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { 
                it.copy(isLoading = true, loadingMessage = "Parsing stream & configuring topology...") 
            }

            // Step 1: Ingest & save via Module 1
            val importResult = lifecycleManager.importProjectFromPayload(rawPayload)
            if (importResult.isFailure) {
                val err = importResult.exceptionOrNull()?.localizedMessage ?: "Unknown parse error"
                _uiState.update { it.copy(isLoading = false, errorMessage = "Import failed: $err") }
                return@launch
            }

            val project = importResult.getOrThrow()

            // Step 2: Validate via Module 2
            _uiState.update { it.copy(loadingMessage = "Executing syntax & security verification...") }
            try {
                validator.validateStrict(project)
                val report = validator.validateAndReport(project)

                val updatedList = lifecycleManager.getAllProjects()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        projects = updatedList,
                        selectedProject = project,
                        validationReport = report,
                        successMessage = "Project '${project.name}' successfully parsed, validated, and saved!",
                        currentTab = DashboardTab.EXECUTION_CANVAS
                    )
                }
            } catch (e: SecurityRuleBrokenException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "SECURITY VIOLATION: ${e.localizedMessage}"
                    )
                }
            } catch (e: SyntaxValidationException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "SYNTAX ERROR: ${e.localizedMessage}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Validation failure: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun runProject(project: SandboxProject) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { 
                it.copy(
                    selectedProject = project,
                    isLoading = true, 
                    loadingMessage = "Routing to compilation pipeline (${project.metadata.language})..."
                ) 
            }

            val pipeline = when (project.metadata.language) {
                ProjectLanguage.CPP, ProjectLanguage.C -> nativePipeline
                else -> javaPipeline
            }

            val compileResult = pipeline.compile(project)
            if (compileResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Compilation failed: ${compileResult.exceptionOrNull()?.localizedMessage}"
                    )
                }
                return@launch
            }

            val artifact = compileResult.getOrThrow()
            _uiState.update { it.copy(loadingMessage = "Executing in dynamic sandbox runtime...") }

            val execResult = pipeline.execute(artifact)
            val output = execResult.getOrNull()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    compilationArtifact = artifact,
                    executionOutput = output,
                    currentTab = DashboardTab.EXECUTION_CANVAS
                )
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Deleting project $projectId...") }
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
        }
    }

    fun selectProject(project: SandboxProject) {
        _uiState.update { 
            it.copy(
                selectedProject = project,
                currentTab = DashboardTab.EXECUTION_CANVAS,
                errorMessage = null
            ) 
        }
    }

    fun loadSampleJavaPayload() {
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
    }

    fun loadSampleCppPayload() {
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
    }
}
