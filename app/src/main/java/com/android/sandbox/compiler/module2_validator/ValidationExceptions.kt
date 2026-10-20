package com.android.sandbox.compiler.module2_validator

/**
 * Thrown immediately when a build script references unauthorized third-party libraries
 * outside of approved system/core packages.
 */
class SecurityRuleBrokenException(
    val filePath: String,
    val line: Int,
    val offendingDependency: String,
    detailMessage: String = "Security Rule Violation: Dependency '$offendingDependency' in '$filePath' at line $line is not allowed in the offline sandbox."
) : SecurityException(detailMessage)

/**
 * Thrown when code fails bracket balancing or has unclosed string literals.
 */
class SyntaxValidationException(
    val filePath: String,
    val line: Int,
    val column: Int,
    val detailMessage: String
) : Exception("Syntax Validation Error in '$filePath' at line $line, col $column: $detailMessage")
