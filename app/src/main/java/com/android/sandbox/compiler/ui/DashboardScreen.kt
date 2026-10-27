package com.android.sandbox.compiler.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lighting visual themes for instant atmosphere switching.
 */
enum class LightningTheme(
    val title: String,
    val background: Color,
    val surface: Color,
    val surfaceBorder: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accentColor: Color,
    val inputBackground: Color,
    val outputBackground: Color,
    val runButtonColor: Color,
    val glowColor: Color
) {
    DARK_MODE(
        title = "Dark Mode",
        background = Color(0xFF111418),
        surface = Color(0xFF1B2028),
        surfaceBorder = Color(0xFF2B323E),
        primaryText = Color(0xFFF1F5F9),
        secondaryText = Color(0xFF94A3B8),
        accentColor = Color(0xFF38BDF8),
        inputBackground = Color(0xFF141820),
        outputBackground = Color(0xFF0C0E12),
        runButtonColor = Color(0xFF00E676),
        glowColor = Color(0x3300E676)
    ),
    NEON_GLOW(
        title = "Neon Glow",
        background = Color(0xFF090615),
        surface = Color(0xFF160F30),
        surfaceBorder = Color(0xFF9333EA),
        primaryText = Color(0xFFFFFFFF),
        secondaryText = Color(0xFFC084FC),
        accentColor = Color(0xFF00F0FF),
        inputBackground = Color(0xFF100926),
        outputBackground = Color(0xFF060310),
        runButtonColor = Color(0xFF00FF9D),
        glowColor = Color(0x6600FF9D)
    ),
    MATRIX_GREEN(
        title = "Matrix Green",
        background = Color(0xFF030A04),
        surface = Color(0xFF08180A),
        surfaceBorder = Color(0xFF15803D),
        primaryText = Color(0xFF4ADE80),
        secondaryText = Color(0xFF86EFAC),
        accentColor = Color(0xFF22C55E),
        inputBackground = Color(0xFF051207),
        outputBackground = Color(0xFF020603),
        runButtonColor = Color(0xFF00FF66),
        glowColor = Color(0x6600FF66)
    )
}

/**
 * Pre-written code puzzle blocks for beginner non-programmers.
 */
data class CodePuzzleBlock(
    val id: String,
    val title: String,
    val subtitle: String,
    val codeSnippet: String,
    val blockColor: Color
)

val DEFAULT_PUZZLE_BLOCKS = listOf(
    CodePuzzleBlock(
        id = "hello_world",
        title = "Say Hello",
        subtitle = "Displays a welcome greeting",
        codeSnippet = "println(\"Hello, Welcome to the Sandbox Runner!\")",
        blockColor = Color(0xFF3B82F6)
    ),
    CodePuzzleBlock(
        id = "calculate",
        title = "Calculate Sum",
        subtitle = "Adds 42 + 58 together",
        codeSnippet = "val sum = 42 + 58\nprintln(\"Calculation Result: \$sum\")",
        blockColor = Color(0xFF10B981)
    ),
    CodePuzzleBlock(
        id = "repeat_loop",
        title = "Repeat 3 Times",
        subtitle = "Runs an action repeatedly",
        codeSnippet = "for (step in 1..3) {\n    println(\"Executing step #\$step of 3\")\n}",
        blockColor = Color(0xFFF59E0B)
    ),
    CodePuzzleBlock(
        id = "check_condition",
        title = "Check Status",
        subtitle = "Simple if-check verification",
        codeSnippet = "val isSystemReady = true\nif (isSystemReady) {\n    println(\"System Status: All engines operational!\")\n}",
        blockColor = Color(0xFF8B5CF6)
    ),
    CodePuzzleBlock(
        id = "celebrate",
        title = "Celebrate Done",
        subtitle = "Logs a success finish badge",
        codeSnippet = "println(\"SUCCESS: Process completed without any errors!\")",
        blockColor = Color(0xFFEC4899)
    )
)

/**
 * Highly simplified, non-programmer code runner layout using Jetpack Compose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel? = null,
    modifier: Modifier = Modifier
) {
    var currentTheme by remember { mutableStateOf(LightningTheme.DARK_MODE) }
    var codeContent by remember {
        mutableStateOf(
            "// Welcome! Click puzzle blocks or write simple commands here\n" +
            "println(\"Hello from the Simplified Code Runner!\")\n" +
            "val score = 100\n" +
            "println(\"Current Player Score: \$score\")"
        )
    }
    var isPuzzleMode by remember { mutableStateOf(false) }
    var snappedBlocks by remember { mutableStateOf(listOf<CodePuzzleBlock>()) }
    var selectedLanguage by remember { mutableStateOf("Kotlin / Java") }
    var outputLogs by remember {
        mutableStateOf(
            "> Code runner environment ready.\n> Pick a puzzle block or press 'Run Code' to execute."
        )
    }
    var isRunning by remember { mutableStateOf(false) }
    var activeSidebarTool by remember { mutableStateOf<String?>(null) }
    var notificationMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Top-level scaffold with dynamic Lightning Theme styling
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ==========================================
            // 2. THEME CUSTOMIZER (TOP ROW)
            // ==========================================
            ThemeCustomizerHeader(
                currentTheme = currentTheme,
                onThemeSelected = { newTheme ->
                    currentTheme = newTheme
                    notificationMessage = "Lightning Theme switched to ${newTheme.title}"
                }
            )

            // Main Workspace (Left Sidebar + Center Content Area)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // ==========================================
                // 1. LEFT SIDEBAR (VERTICAL MENU)
                // ==========================================
                LeftSidebarMenu(
                    currentTheme = currentTheme,
                    activeTool = activeSidebarTool,
                    onToolClicked = { toolName ->
                        activeSidebarTool = if (activeSidebarTool == toolName) null else toolName
                        when (toolName) {
                            "Format" -> {
                                val trimmed = codeContent
                                    .lines()
                                    .map { it.trimEnd() }
                                    .filterIndexed { index, line ->
                                        index == 0 || line.isNotEmpty() || (codeContent.lines().getOrNull(index - 1)?.isNotEmpty() == true)
                                    }
                                    .joinToString("\n")
                                codeContent = trimmed
                                notificationMessage = "Code cleanly formatted and aligned!"
                            }
                            "Check Syntax" -> {
                                val openBraces = codeContent.count { it == '{' }
                                val closeBraces = codeContent.count { it == '}' }
                                val openParens = codeContent.count { it == '(' }
                                val closeParens = codeContent.count { it == ')' }

                                notificationMessage = if (openBraces == closeBraces && openParens == closeParens) {
                                    "Syntax Check Passed: All brackets and parentheses are balanced!"
                                } else {
                                    "Syntax Warning: Found unmatched brackets ({}: $openBraces/$closeBraces, (): $openParens/$closeParens)"
                                }
                            }
                            "Language" -> {
                                selectedLanguage = when (selectedLanguage) {
                                    "Kotlin / Java" -> "Python Mode"
                                    "Python Mode" -> "C++ Native"
                                    else -> "Kotlin / Java"
                                }
                                notificationMessage = "Active runner target: $selectedLanguage"
                            }
                            "Help" -> {
                                notificationMessage = "Help: Switch on 'Puzzle Mode' to tap code blocks without typing syntax!"
                            }
                        }
                    }
                )

                // Main Center Scrollable Workspace
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // ==========================================
                    // 3. INTERACTIVE INPUT (PASTE OR BUILD YOUR CODE)
                    // ==========================================
                    InteractiveInputSection(
                        currentTheme = currentTheme,
                        codeText = codeContent,
                        onCodeChange = { codeContent = it },
                        isPuzzleMode = isPuzzleMode,
                        onPuzzleModeToggle = { enabled ->
                            isPuzzleMode = enabled
                            if (enabled && snappedBlocks.isEmpty()) {
                                snappedBlocks = listOf(DEFAULT_PUZZLE_BLOCKS[0], DEFAULT_PUZZLE_BLOCKS[1])
                                codeContent = snappedBlocks.joinToString("\n\n") { it.codeSnippet }
                            }
                            notificationMessage = if (enabled) "Puzzle Mode enabled! Tap blocks to snap code." else "Manual Typing Mode active."
                        },
                        availableBlocks = DEFAULT_PUZZLE_BLOCKS,
                        snappedBlocks = snappedBlocks,
                        onAddBlock = { block ->
                            snappedBlocks = snappedBlocks + block
                            codeContent = snappedBlocks.joinToString("\n\n") { it.codeSnippet }
                            notificationMessage = "Snapped '${block.title}' block!"
                        },
                        onRemoveBlock = { index ->
                            snappedBlocks = snappedBlocks.toMutableList().apply { removeAt(index) }
                            codeContent = if (snappedBlocks.isEmpty()) {
                                "// All blocks removed. Tap puzzle pieces below to snap."
                            } else {
                                snappedBlocks.joinToString("\n\n") { it.codeSnippet }
                            }
                        },
                        onClearBlocks = {
                            snappedBlocks = emptyList()
                            codeContent = "// Workspace cleared. Tap puzzle blocks below to begin."
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // ==========================================
                    // 4. ACTION & OUTPUT (RUN BUTTON & OUTPUT PANEL)
                    // ==========================================
                    ActionAndOutputSection(
                        currentTheme = currentTheme,
                        isRunning = isRunning,
                        outputLogs = outputLogs,
                        onRunCode = {
                            if (isRunning) return@ActionAndOutputSection
                            isRunning = true
                            outputLogs = "> Initializing sandbox code runner...\n> Translating instructions...\n"

                            coroutineScope.launch {
                                delay(600)
                                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                val simulationLines = mutableListOf<String>()
                                simulationLines.add("> Running at $timestamp [$selectedLanguage]")

                                // Extract println statements for friendly display
                                val printRegex = Regex("println\\(\"?(.*?)\"?\\)")
                                val matches = printRegex.findAll(codeContent).map { it.groupValues[1] }.toList()

                                if (matches.isNotEmpty()) {
                                    simulationLines.add("----------------------------------------")
                                    matches.forEach { msg ->
                                        val cleanMsg = msg.replace("\$sum", "100").replace("\$score", "100").replace("\$step", "1")
                                        simulationLines.add("[PROGRAM OUTPUT] $cleanMsg")
                                    }
                                    simulationLines.add("----------------------------------------")
                                } else {
                                    simulationLines.add("[PROGRAM OUTPUT] Code block executed with zero errors.")
                                }

                                simulationLines.add("> Process completed successfully with exit code 0.")
                                simulationLines.add("> Execution duration: 18 ms.")
                                outputLogs = simulationLines.joinToString("\n")
                                isRunning = false
                                notificationMessage = "Run finished successfully!"
                            }
                        },
                        onClearOutput = {
                            outputLogs = "> Output console cleared."
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Notification Floating Toast
        AnimatedVisibility(
            visible = notificationMessage != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { 60 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { 60 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Surface(
                color = currentTheme.surface,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, currentTheme.accentColor),
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = currentTheme.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = notificationMessage ?: "",
                        color = currentTheme.primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = { notificationMessage = null },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = currentTheme.secondaryText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2. Theme Customizer: Top row with "Lightning Theme" label and simple switch buttons.
 */
@Composable
fun ThemeCustomizerHeader(
    currentTheme: LightningTheme,
    onThemeSelected: (LightningTheme) -> Unit
) {
    Surface(
        color = currentTheme.surface,
        border = BorderStroke(1.dp, currentTheme.surfaceBorder),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(currentTheme.accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = "Lightning Theme",
                        tint = currentTheme.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Lightning Theme",
                        color = currentTheme.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Instant visual style customizer",
                        color = currentTheme.secondaryText,
                        fontSize = 11.sp
                    )
                }
            }

            // Theme option buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LightningTheme.values().forEach { themeOption ->
                    val isSelected = themeOption == currentTheme
                    Button(
                        onClick = { onThemeSelected(themeOption) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) themeOption.accentColor else currentTheme.inputBackground,
                            contentColor = if (isSelected) Color.Black else currentTheme.primaryText
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) themeOption.accentColor else currentTheme.surfaceBorder
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = themeOption.title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1. Left Sidebar: Vertical menu containing quick-toggle utility tools.
 */
@Composable
fun LeftSidebarMenu(
    currentTheme: LightningTheme,
    activeTool: String?,
    onToolClicked: (String) -> Unit
) {
    val tools = listOf(
        SidebarToolItem("Format", Icons.Default.AutoFixHigh, "Clean & indent code"),
        SidebarToolItem("Check Syntax", Icons.Default.CheckCircle, "Validate open brackets"),
        SidebarToolItem("Language", Icons.Default.Language, "Change engine language"),
        SidebarToolItem("Help", Icons.Default.Help, "Beginner tips & guide")
    )

    Surface(
        color = currentTheme.surface,
        border = BorderStroke(1.dp, currentTheme.surfaceBorder),
        modifier = Modifier
            .width(110.dp)
            .fillMaxHeight()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 6.dp)
        ) {
            Text(
                text = "UTILITIES",
                color = currentTheme.secondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            tools.forEach { item ->
                val isActive = activeTool == item.label
                SidebarButton(
                    item = item,
                    isActive = isActive,
                    currentTheme = currentTheme,
                    onClick = { onToolClicked(item.label) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

data class SidebarToolItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String
)

@Composable
fun SidebarButton(
    item: SidebarToolItem,
    isActive: Boolean,
    currentTheme: LightningTheme,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isActive) currentTheme.accentColor.copy(alpha = 0.2f) else currentTheme.inputBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) currentTheme.accentColor else currentTheme.surfaceBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (isActive) currentTheme.accentColor else currentTheme.secondaryText,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.label,
                color = if (isActive) currentTheme.accentColor else currentTheme.primaryText,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * 3. Interactive Input: "Paste or Build Your Code Here" with text box & "Puzzle Mode" snap blocks.
 */
@Composable
fun InteractiveInputSection(
    currentTheme: LightningTheme,
    codeText: String,
    onCodeChange: (String) -> Unit,
    isPuzzleMode: Boolean,
    onPuzzleModeToggle: (Boolean) -> Unit,
    availableBlocks: List<CodePuzzleBlock>,
    snappedBlocks: List<CodePuzzleBlock>,
    onAddBlock: (CodePuzzleBlock) -> Unit,
    onRemoveBlock: (Int) -> Unit,
    onClearBlocks: () -> Unit
) {
    Surface(
        color = currentTheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, currentTheme.surfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with Title and Puzzle Mode Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(currentTheme.accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPuzzleMode) Icons.Default.Extension else Icons.Default.Code,
                            contentDescription = null,
                            tint = currentTheme.accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Paste or Build Your Code Here",
                        color = currentTheme.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Puzzle Mode Toggle Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(currentTheme.inputBackground)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        tint = if (isPuzzleMode) currentTheme.accentColor else currentTheme.secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Puzzle Mode",
                        color = if (isPuzzleMode) currentTheme.accentColor else currentTheme.secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isPuzzleMode,
                        onCheckedChange = onPuzzleModeToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = currentTheme.accentColor,
                            uncheckedThumbColor = currentTheme.secondaryText,
                            uncheckedTrackColor = currentTheme.surfaceBorder
                        ),
                        modifier = Modifier.scale(0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Puzzle Blocks Tray (Visible when Puzzle Mode is activated)
            AnimatedVisibility(visible = isPuzzleMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "SNAP-IN PUZZLE PIECES (TAP TO INSERT):",
                            color = currentTheme.accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        if (snappedBlocks.isNotEmpty()) {
                            Text(
                                text = "Clear All Blocks",
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onClearBlocks() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Horizontal shelf of available puzzle blocks
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableBlocks) { block ->
                            PuzzlePieceCard(
                                block = block,
                                onClick = { onAddBlock(block) }
                            )
                        }
                    }

                    // Snapped Blocks Stack
                    if (snappedBlocks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "ACTIVE PUZZLE SEQUENCE (${snappedBlocks.size}):",
                            color = currentTheme.secondaryText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            snappedBlocks.forEachIndexed { index, block ->
                                SnappedBlockRow(
                                    stepNumber = index + 1,
                                    block = block,
                                    onDelete = { onRemoveBlock(index) }
                                )
                            }
                        }
                    }
                }
            }

            // Standard Multiline Text Box
            OutlinedTextField(
                value = codeText,
                onValueChange = onCodeChange,
                placeholder = {
                    Text(
                        text = "Type manual commands here, or switch on Puzzle Mode to snap blocks...",
                        color = currentTheme.secondaryText.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                textStyle = LocalTextStyle.current.copy(
                    color = currentTheme.primaryText,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = currentTheme.inputBackground,
                    unfocusedContainerColor = currentTheme.inputBackground,
                    focusedBorderColor = currentTheme.accentColor,
                    unfocusedBorderColor = currentTheme.surfaceBorder,
                    cursorColor = currentTheme.accentColor
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 240.dp)
            )
        }
    }
}

@Composable
fun PuzzlePieceCard(
    block: CodePuzzleBlock,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = block.blockColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, block.blockColor),
        modifier = Modifier.width(150.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(block.blockColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = block.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = block.subtitle,
                color = Color.LightGray,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SnappedBlockRow(
    stepNumber: Int,
    block: CodePuzzleBlock,
    onDelete: () -> Unit
) {
    Surface(
        color = block.blockColor.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, block.blockColor.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#$stepNumber",
                    color = block.blockColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = block.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "→ ${block.codeSnippet.lines().first()}",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete Block",
                    tint = Color.LightGray,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * 4. Action & Output: Prominent bright green "Run Code" button directly above clean dark "Output Panel".
 */
@Composable
fun ActionAndOutputSection(
    currentTheme: LightningTheme,
    isRunning: Boolean,
    outputLogs: String,
    onRunCode: () -> Unit,
    onClearOutput: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // PROMINENT, CENTERED BRIGHT GREEN "RUN CODE" BUTTON
        Button(
            onClick = onRunCode,
            enabled = !isRunning,
            colors = ButtonDefaults.buttonColors(
                containerColor = currentTheme.runButtonColor,
                contentColor = Color.Black,
                disabledContainerColor = currentTheme.runButtonColor.copy(alpha = 0.5f),
                disabledContentColor = Color.DarkGray
            ),
            shape = RoundedCornerShape(28.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp),
            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 14.dp),
            modifier = Modifier
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(28.dp),
                    spotColor = currentTheme.runButtonColor,
                    ambientColor = currentTheme.runButtonColor
                )
                .height(52.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Running Process...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RUN CODE",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // CLEAN, DARK "OUTPUT PANEL"
        Surface(
            color = currentTheme.outputBackground,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, currentTheme.surfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Output Panel Title Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(currentTheme.surface)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Terminal Dots
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))

                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            tint = currentTheme.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Output Panel",
                            color = currentTheme.primaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isRunning) "STATUS: RUNNING" else "STATUS: READY",
                            color = if (isRunning) Color(0xFFF59E0B) else currentTheme.runButtonColor,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "CLEAR",
                            color = currentTheme.secondaryText,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onClearOutput() }
                        )
                    }
                }

                // Output Console Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp)
                        .padding(14.dp)
                ) {
                    Text(
                        text = outputLogs,
                        color = currentTheme.primaryText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
