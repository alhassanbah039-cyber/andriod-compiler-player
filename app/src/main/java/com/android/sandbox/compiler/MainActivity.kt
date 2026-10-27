package com.android.sandbox.compiler

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.android.sandbox.compiler.module3_pipeline.ExecutionOutput
import com.android.sandbox.compiler.ui.ConsoleLogEntry
import com.android.sandbox.compiler.ui.ConsoleLogLevel
import com.android.sandbox.compiler.ui.SandboxUiState
import com.android.sandbox.compiler.ui.SandboxViewModel
import com.android.sandbox.compiler.ui.theme.SandboxAppTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main application host activity directly rendering the Jetpack Compose dashboard,
 * handling hardware permissions (Camera & Media), and coordinating reactive sandbox execution.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: SandboxViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!cameraGranted) {
            Toast.makeText(
                this,
                "Camera hardware permission optional: some sensor sandbox features restricted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestHardwarePermissions()

        setContent {
            SandboxAppTheme {
                SandboxDashboardRoot(viewModel = viewModel)
            }
        }
    }

    private fun requestHardwarePermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            @Suppress("DEPRECATION")
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }
}

/**
 * Root Composable orchestrating Project Directory List, Payload Input Stage,
 * and IDE Terminal Console Output Canvas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxDashboardRoot(
    viewModel: SandboxViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var activeTab by remember { mutableStateOf(0) } // 0: Projects, 1: Payload Input

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Offline Sandbox Compiler",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Dual-Pathway In-Process Execution Engine",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadProjects() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload Projects")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Tab Selector
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Projects (${uiState.projects.size})") },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Payload Ingestion") },
                        icon = { Icon(Icons.Default.Code, contentDescription = null) }
                    )
                }

                // Upper Stage: Projects List OR Payload Input
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.0f)
                ) {
                    if (activeTab == 0) {
                        ProjectDirectoryList(
                            projects = uiState.projects,
                            selectedProject = uiState.selectedProject,
                            onSelect = { viewModel.selectProject(it) },
                            onRun = { viewModel.runProject(it) },
                            onDelete = { viewModel.deleteProject(it.id) },
                            onReload = { viewModel.reloadProject(it.id) },
                            onSwitchToInput = { activeTab = 1 }
                        )
                    } else {
                        PayloadInputStage(
                            rawInput = uiState.rawPayloadInput,
                            onInputChanged = { viewModel.onPayloadChanged(it) },
                            onImport = {
                                viewModel.importAndValidateProject(uiState.rawPayloadInput)
                                activeTab = 0
                            },
                            onLoadJavaSample = { viewModel.loadSampleJava() },
                            onLoadCppSample = { viewModel.loadSampleCpp() }
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                // Lower Stage: IDE Terminal Console Output Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    ConsoleOutputCanvas(
                        logs = uiState.consoleLogs,
                        executionOutput = uiState.executionOutput,
                        onClearLogs = { viewModel.clearConsole() }
                    )
                }
            }

            // Alert Snackbars
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
                    Text(text = errorMsg, style = MaterialTheme.typography.bodySmall)
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
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = uiState.loadingProgressText.ifEmpty { "Compiling in sandbox..." },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
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
 * 1. Project Directory List: Clean LazyColumn displaying saved projects,
 * language tags (Kotlin vs CPP), file count, and action triggers.
 */
@Composable
fun ProjectDirectoryList(
    projects: List<SandboxProject>,
    selectedProject: SandboxProject?,
    onSelect: (SandboxProject) -> Unit,
    onRun: (SandboxProject) -> Unit,
    onDelete: (SandboxProject) -> Unit,
    onReload: (SandboxProject) -> Unit,
    onSwitchToInput: () -> Unit
) {
    if (projects.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No saved sandbox projects",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Paste a multi-file payload stream in the 'Payload Ingestion' tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onSwitchToInput) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ingest New Payload")
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(projects, key = { it.id }) { project ->
                val isSelected = selectedProject?.id == project.id
                ProjectDirectoryCard(
                    project = project,
                    isSelected = isSelected,
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
fun ProjectDirectoryCard(
    project: SandboxProject,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
    onReload: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val formattedDate = remember(project.lastModifiedEpochMs) {
        dateFormat.format(Date(project.lastModifiedEpochMs))
    }

    Card(
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .then(
                if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                LanguageBadgeChip(language = project.metadata.language)
            }

            Spacer(modifier = Modifier.height(4.dp))
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

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onReload, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reload",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onRun,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Build & Run", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun LanguageBadgeChip(language: ProjectLanguage) {
    val (bgColor, textColor) = when (language) {
        ProjectLanguage.JAVA -> Color(0xFFFFF3E0) to Color(0xFFE65100)
        ProjectLanguage.KOTLIN -> Color(0xFFF3E5F5) to Color(0xFF7B1FA2)
        ProjectLanguage.CPP, ProjectLanguage.C -> Color(0xFFE0F2F1) to Color(0xFF00695C)
        ProjectLanguage.UNKNOWN -> Color.LightGray.copy(alpha = 0.3f) to Color.DarkGray
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = language.name,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 2. Payload Input Stage: Large OutlinedTextField for multi-file raw streams.
 */
@Composable
fun PayloadInputStage(
    rawInput: String,
    onInputChanged: (String) -> Unit,
    onImport: () -> Unit,
    onLoadJavaSample: () -> Unit,
    onLoadCppSample: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Stream Ingestion Stage",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onLoadJavaSample,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Sample Java", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onLoadCppSample,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Sample C++", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = rawInput,
            onValueChange = onInputChanged,
            placeholder = {
                Text(
                    "[META: LANGUAGE=Kotlin]\n[FILE: settings.gradle.kts]\nrootProject.name = \"App\"\n[FILE: Main.kt]\n...",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Parse, Validate & Save Project")
        }
    }
}

/**
 * 3. Console Output Canvas: Mimics an IDE terminal window with real-time logs,
 * standard output strings, compiler success logs, and SecurityRuleBrokenException alerts.
 */
@Composable
fun ConsoleOutputCanvas(
    logs: List<ConsoleLogEntry>,
    executionOutput: ExecutionOutput?,
    onClearLogs: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll terminal to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(logs.size - 1)
            }
        }
    }

    Surface(
        color = Color(0xFF141414),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Terminal Window Titlebar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF202020))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFFF5F56)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFFFBD2E)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF27C93F)))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "TERMINAL CONSOLE (LIVE STREAM)",
                        color = Color(0xFFCCCCCC),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${logs.size} lines",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CLEAR",
                        color = Color(0xFF64B5F6),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onClearLogs() }
                    )
                }
            }

            // Scrollable Console Log Lines
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(logs) { entry ->
                    ConsoleLogItem(entry = entry)
                }
            }
        }
    }
}

@Composable
fun ConsoleLogItem(entry: ConsoleLogEntry) {
    val (badgeColor, textColor) = when (entry.level) {
        ConsoleLogLevel.INFO -> Color(0xFF4FC3F7) to Color(0xFFE0E0E0)
        ConsoleLogLevel.SUCCESS -> Color(0xFF81C784) to Color(0xFFA5D6A7)
        ConsoleLogLevel.WARNING -> Color(0xFFFFB74D) to Color(0xFFFFE082)
        ConsoleLogLevel.ERROR -> Color(0xFFE57373) to Color(0xFFFFCDD2)
        ConsoleLogLevel.COMMAND -> Color(0xFFBA68C8) to Color(0xFFE1BEE7)
    }

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp)
    ) {
        Text(
            text = entry.formattedTime,
            color = Color(0xFF616161),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "[${entry.level.name}]",
            color = badgeColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = entry.message,
            color = textColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}
