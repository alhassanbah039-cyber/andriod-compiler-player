package com.android.sandbox.compiler.ui

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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Offline Sandbox Compiler",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "App-in-an-App Dual Execution Engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadProjects() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Projects")
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
                    selected = uiState.currentTab == DashboardTab.PROJECTS,
                    onClick = { viewModel.selectTab(DashboardTab.PROJECTS) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("Projects (${uiState.projects.size})") }
                )
                NavigationBarItem(
                    selected = uiState.currentTab == DashboardTab.IMPORT_NEW,
                    onClick = { viewModel.selectTab(DashboardTab.IMPORT_NEW) },
                    icon = { Icon(Icons.Default.AddBox, contentDescription = null) },
                    label = { Text("Import Stream") }
                )
                NavigationBarItem(
                    selected = uiState.currentTab == DashboardTab.EXECUTION_CANVAS,
                    onClick = { viewModel.selectTab(DashboardTab.EXECUTION_CANVAS) },
                    icon = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
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
            when (uiState.currentTab) {
                DashboardTab.PROJECTS -> ProjectsListSection(
                    projects = uiState.projects,
                    onSelect = { viewModel.selectProject(it) },
                    onRun = { viewModel.runProject(it) },
                    onDelete = { viewModel.deleteProject(it.id) },
                    onNavigateToImport = { viewModel.selectTab(DashboardTab.IMPORT_NEW) }
                )
                DashboardTab.IMPORT_NEW -> ImportPayloadSection(
                    rawInput = uiState.rawPayloadInput,
                    onInputChange = { viewModel.onPayloadInputChanged(it) },
                    onImportClick = { viewModel.importAndValidateProject(uiState.rawPayloadInput) },
                    onLoadSampleJava = { viewModel.loadSampleJavaPayload() },
                    onLoadSampleCpp = { viewModel.loadSampleCppPayload() }
                )
                DashboardTab.EXECUTION_CANVAS -> ExecutionCanvasSection(
                    selectedProject = uiState.selectedProject,
                    artifact = uiState.compilationArtifact,
                    executionOutput = uiState.executionOutput,
                    onRunProject = { proj -> viewModel.runProject(proj) }
                )
            }

            // Error Banner
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Success Banner
            uiState.successMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Fullscreen Loading Spinner Overlay
            if (uiState.isLoading) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = uiState.loadingMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectsListSection(
    projects: List<SandboxProject>,
    onSelect: (SandboxProject) -> Unit,
    onRun: (SandboxProject) -> Unit,
    onDelete: (SandboxProject) -> Unit,
    onNavigateToImport: () -> Unit
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
                    text = "No sandboxed projects found",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Paste a raw project string stream to parse and execute.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onNavigateToImport) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import New Project Stream")
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(projects, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    onSelect = { onSelect(project) },
                    onRun = { onRun(project) },
                    onDelete = { onDelete(project) }
                )
            }
        }
    }
}

@Composable
fun ProjectCard(
    project: SandboxProject,
    onSelect: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val formattedDate = remember(project.lastModifiedEpochMs) {
        dateFormat.format(Date(project.lastModifiedEpochMs))
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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

                LanguageBadge(language = project.metadata.language)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Files: ${project.files.size} | Updated: $formattedDate",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Dir: /projects/${project.id}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
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
fun LanguageBadge(language: ProjectLanguage) {
    val (bgColor, textColor) = when (language) {
        ProjectLanguage.JAVA -> Color(0xFFE65100).copy(alpha = 0.2f) to Color(0xFFBF360C)
        ProjectLanguage.KOTLIN -> Color(0xFF7B1FA2).copy(alpha = 0.2f) to Color(0xFF4A148C)
        ProjectLanguage.CPP, ProjectLanguage.C -> Color(0xFF00695C).copy(alpha = 0.2f) to Color(0xFF004D40)
        ProjectLanguage.UNKNOWN -> Color.Gray.copy(alpha = 0.2f) to Color.DarkGray
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

@Composable
fun ImportPayloadSection(
    rawInput: String,
    onInputChange: (String) -> Unit,
    onImportClick: () -> Unit,
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
            text = "Ingest Project String Stream",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Paste payloads delimited with [META: LANGUAGE=...] and [FILE: path/filename.ext] tags.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onLoadSampleJava) {
                Text("Sample Java")
            }
            OutlinedButton(onClick = onLoadSampleCpp) {
                Text("Sample C++")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = rawInput,
            onValueChange = onInputChange,
            label = { Text("Raw Multi-File Project Stream") },
            placeholder = { Text("[META: LANGUAGE=JAVA]\n[FILE: app/src/main/java/Main.java]\n...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onImportClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Parse, Validate & Save Project")
        }
    }
}

@Composable
fun ExecutionCanvasSection(
    selectedProject: SandboxProject?,
    artifact: CompilationArtifact?,
    executionOutput: ExecutionOutput?,
    onRunProject: (SandboxProject) -> Unit
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
        // Active Project Info Header
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
                        text = "Target Language: ${selectedProject.metadata.language}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(onClick = { onRunProject(selectedProject) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Trigger Build")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Execution Canvas Container
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
                .height(220.dp)
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
                            text = if (isOk) "Dynamic Execution Succeeded" else "Execution Encountered Warnings",
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
                            text = "Trigger build to invoke dynamic sandbox pipeline.",
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
