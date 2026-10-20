package com.android.sandbox.compiler.module2_validator

import com.android.sandbox.compiler.model.SandboxFile
import com.android.sandbox.compiler.model.SandboxFileType
import java.util.regex.Pattern

/**
 * Strict security scanner inspecting build scripts (build.gradle, build.gradle.kts).
 * Blocks all third-party external dependencies except explicitly approved core components.
 */
object DependencyBlocklistScanner {

    /**
     * Explicitly permitted library coordinate prefixes.
     * Everything outside this whitelist triggers a SecurityRuleBrokenException.
     */
    val APPROVED_PACKAGE_PREFIXES = listOf(
        "androidx.compose",
        "androidx.core",
        "androidx.activity",
        "androidx.lifecycle",
        "org.jetbrains.kotlin:kotlin-stdlib"
    )

    private val DEPENDENCY_INVOCATION_REGEX = Pattern.compile(
        """(?i)\b(implementation|api|compileOnly|runtimeOnly|classpath|testImplementation|androidTestImplementation)\s*[\(\s]\s*(?:platform\s*\(\s*)?['"]([^'"]+)['"]"""
    )

    private val MAP_NOTATION_REGEX = Pattern.compile(
        """(?i)\b(implementation|api|compileOnly|runtimeOnly|classpath)\s+group\s*:\s*['"]([^'"]+)['"]"""
    )

    /**
     * Scans all gradle build files in the project.
     * Halts immediately upon detecting any non-approved dependency.
     */
    @Throws(SecurityRuleBrokenException::class)
    fun scanFile(file: SandboxFile) {
        if (file.fileType != SandboxFileType.GRADLE_BUILD && 
            file.fileType != SandboxFileType.GRADLE_SETTINGS) {
            return
        }

        val lines = file.content.lines()
        for ((lineIndex, line) in lines.withIndex()) {
            val lineNumber = lineIndex + 1
            val trimmed = line.trim()

            // Skip comment lines
            if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                continue
            }

            // Check standard coordinate notation: implementation("group:artifact:version")
            val standardMatcher = DEPENDENCY_INVOCATION_REGEX.matcher(trimmed)
            while (standardMatcher.find()) {
                val coordinate = standardMatcher.group(2) ?: ""
                verifyCoordinateAllowed(file.relativePath, lineNumber, coordinate)
            }

            // Check map style notation: implementation group: "...", name: "..."
            val mapMatcher = MAP_NOTATION_REGEX.matcher(trimmed)
            while (mapMatcher.find()) {
                val group = mapMatcher.group(2) ?: ""
                verifyCoordinateAllowed(file.relativePath, lineNumber, group)
            }
        }
    }

    private fun verifyCoordinateAllowed(filePath: String, line: Int, coordinate: String) {
        val trimmed = coordinate.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":")) {
            // Local module reference (e.g. project(":app")), permitted
            return
        }

        val isApproved = APPROVED_PACKAGE_PREFIXES.any { prefix ->
            trimmed.startsWith(prefix, ignoreCase = true)
        }

        if (!isApproved) {
            throw SecurityRuleBrokenException(
                filePath = filePath,
                line = line,
                offendingDependency = trimmed,
                detailMessage = "Security Rule Violation: External package '$trimmed' at line $line in '$filePath' is not permitted. Offline Sandbox only allows core packages: $APPROVED_PACKAGE_PREFIXES"
            )
        }
    }
}
