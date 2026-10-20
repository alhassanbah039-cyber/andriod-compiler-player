package com.android.sandbox.compiler.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.sandbox.compiler.model.ProjectLanguage
import com.android.sandbox.compiler.model.SandboxProject
import com.android.sandbox.compiler.module3_pipeline.CompilationArtifact
import com.android.sandbox.compiler.module3_pipeline.ExecutionOutput
import java.text.SimpleDateFormat
import java.util.*

enum class NavigationSection {
    PROJECTS,
    RAW_INPUT,
    EXECUTION_CANVAS
}

/**
 * Pure Jetpack Compose Dashboard layout featuring Projects List, Raw Ingestion Area,
 * Loading Spinner Overlay, and Hardware Execution Canvas Container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxDashboardScreen(
    viewModel: SandboxViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedSection by remember { mutableStateOf(NavigationSection.PROJECTS) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Native Sandbox Compiler",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Offline App-in-an-App Execution Engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadProjects() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload Projects from Storage")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedSection == NavigationSection.PROJECTS,
                    onClick = { selectedSection = NavigationSection.PROJECTS },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("Projects (${uiState.projects.size})") }
                )
                NavigationBarItem(
                    selected = selectedSection == NavigationSection.RAW_INPUT,
                    onClick = { selectedSection = NavigationSection.RAW_INPUT },
                    icon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                    label = { Text("Raw Payload") }
                )
                NavigationBarItem(
                    selected = selectedSection == NavigationSection.EXECUTION_CANVAS,
                    onClick = { selectedSection = NavigationSection.EXECUTION_CANVAS },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                    label = { Text("Execution Canvas") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedSection) {
                NavigationSection.PROJECTS -> ProjectsListView(
                    projects = uiState.projects,
                    onSelect = {
                        viewModel.selectProject(it)
                        selectedSection = NavigationSection.EXECUTION_CANVAS
                    },
                    onRun = {
                        viewModel.runProject(it)
                        selectedSection = NavigationSection.EXECUTION_CANVAS
                    },
                    onDelete = { viewModel.deleteProject(it.id) },
                    onReload = { viewModel.reloadProject(it.id) },
                    onNavigateToInput = { selectedSection = NavigationSection.RAW_INPUT }
                )
                NavigationSection.RAW_INPUT -> RawPayloadInputArea(
                    rawInput = uiState.rawPayloadInput,
                    onInputChanged = { viewModel.onPayloadChanged(it) },
                    onImportClicked = {
                        viewModel.importAndValidateProject(uiState.rawPayloadInput)
                        selectedSection = NavigationSection.EXECUTION_CANVAS
                    },
                    onLoadSampleJava = { viewModel.loadSampleJava() },
                    onLoadSampleCpp = { viewModel.loadSampleCpp() }
                )
                NavigationSection.EXECUTION_CANVAS -> ExecutionCanvasContainer(
                    selectedProject = uiState.selectedProject,
                    artifact = uiState.compilationArtifact,
                    executionOutput = uiState.executionOutput,
                    onTriggerBuild = { proj -> viewModel.runProject(proj) }
                )
            }

            // Error Alert Banner
            uiState.errorMessage?.let { errorMsg ->
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.dismissMessages() }) {
                            Text("DISMISS", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = errorMsg, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Success Alert Banner
            uiState.successMessage?.let { successMsg ->
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.dismissMessages() }) {
                            Text("OK", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = successMsg, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Fullscreen Loading Spinner Overlay
            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = uiState.loadingProgressText.ifEmpty { "Executing background task..." },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1. Project List View
 */
@Composable
fun ProjectsListView(
    projects: List<SandboxProject>,
    onSelect: (SandboxProject) -> Unit,
    onRun: (SandboxProject) -> Unit,
    onDelete: (SandboxProject) -> Unit,
    onReload: (SandboxProject) -> Unit,
    onNavigateToInput: () -> Unit
) {
    if (projects.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No saved sandbox projects",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Import a multi-file stream payload to start compiling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onNavigateToInput) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Raw Payload Stream")
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(projects, key = { it.id }) { project ->
                ProjectCardItem(
                    project = project,
                    onSelect = { onSelect(project) },
                    onRun = { onRun(project) },
                    onDelete = { onDelete(project) },
                    onReload = { onReload(project) }
                )
            }
        }
    }
}

@Composable
fun ProjectCardItem(
    project: SandboxProject,
    onSelect: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
    onReload: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val formattedDate = remember(project.lastModifiedEpochMs) {
        dateFormat.format(Date(project.lastModifiedEpochMs))
    }

    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                LanguageMetadataBadge(language = project.metadata.language)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Files: ${project.files.size} | Last Modified: $formattedDate",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Path: /projects/${project.id}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onReload) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload Project", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Project", tint = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onSelect) {
                    Text("Details")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run")
                }
            }
        }
    }
}

@Composable
fun LanguageMetadataBadge(language: ProjectLanguage) {
    val (bgColor, textColor) = when (language) {
        ProjectLanguage.JAVA -> Color(0xFFFFF3E0) to Color(0xFFE65100)
        ProjectLanguage.KOTLIN -> Color(0xFFF3E5F5) to Color(0xFF7B1FA2)
        ProjectLanguage.CPP, ProjectLanguage.C -> Color(0xFFE0F2F1) to Color(0xFF00695C)
        ProjectLanguage.UNKNOWN -> Color.LightGray.copy(alpha = 0.3f) to Color.DarkGray
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = language.name,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * 2. Raw Input Area
 */
@Composable
fun RawPayloadInputArea(
    rawInput: String,
    onInputChanged: (String) -> Unit,
    onImportClicked: () -> Unit,
    onLoadSampleJava: () -> Unit,
    onLoadSampleCpp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Raw Multi-File Payload Stream",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Paste code bounded by [META: LANGUAGE=...] and [FILE: path/filename.ext] tokens.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onLoadSampleJava) {
                Text("Sample Java")
            }
            OutlinedButton(onClick = onLoadSampleCpp) {
                Text("Sample C++")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = rawInput,
            onValueChange = onInputChanged,
            label = { Text("Stream Input String") },
            placeholder = { Text("[META: LANGUAGE=JAVA]\n[FILE: app/src/main/java/Main.java]\n...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onImportClicked,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Parse, Validate & Save Project")
        }
    }
}

/**
 * 3. Execution Canvas Container Block
 */
@Composable
fun ExecutionCanvasContainer(
    selectedProject: SandboxProject?,
    artifact: CompilationArtifact?,
    executionOutput: ExecutionOutput?,
    onTriggerBuild: (SandboxProject) -> Unit
) {
    if (selectedProject == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Select a project from the Projects tab to inspect or execute.")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Project Header Action Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = selectedProject.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Target: ${selectedProject.metadata.language} Pipeline",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(onClick = { onTriggerBuild(selectedProject) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Trigger Build")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hardware Execution Canvas Container
        Text(
            text = "Hardware Execution Canvas Container",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                if (executionOutput != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        val isOk = executionOutput.success
                        Icon(
                            if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isOk) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isOk) "Dynamic Execution Output" else "Execution Warnings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isOk) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = executionOutput.stdOut.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                        if (executionOutput.stdErr.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "StdErr: ${executionOutput.stdErr.trim()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            executionOutput.details["loadedClass"]?.let { cls ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("Class: $cls", fontSize = 11.sp) }
                                )
                            }
                            executionOutput.details["targetAbi"]?.let { abi ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("ABI: $abi", fontSize = 11.sp) }
                                )
                            }
                            executionOutput.details["returnValue"]?.let { ret ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("Return: $ret", fontSize = 11.sp) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Execution duration: ${executionOutput.executionDurationMs} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "App-in-an-App Container Ready",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Trigger build to run compiled binary inside hardware sandbox.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Compiler Output Log Console
        Text(
            text = "Compiler & Linker Logs",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 140.dp)
        ) {
            val logText = artifact?.compilerOutputLog ?: "No build pipeline run yet."
            Text(
                text = logText,
                color = Color(0xFF76FF03),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
