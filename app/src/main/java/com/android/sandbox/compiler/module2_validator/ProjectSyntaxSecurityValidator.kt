package com.android.sandbox.compiler.module2_validator

import com.android.sandbox.compiler.model.SandboxProject
import com.android.sandbox.compiler.model.ValidationError
import com.android.sandbox.compiler.model.ValidationReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Top-level security and syntax validation coordinator.
 * Executes both lexical bracket balance checks and dependency blocklist scanners
 * across all project files before passing the project to the compilation pipelines.
 */
class ProjectSyntaxSecurityValidator {

    /**
     * Executes strict validation.
     * Throws SyntaxValidationException or SecurityRuleBrokenException on the first critical violation.
     */
    @Throws(SyntaxValidationException::class, SecurityRuleBrokenException::class)
    suspend fun validateStrict(project: SandboxProject) = withContext(Dispatchers.Default) {
        for (file in project.files) {
            // 1. Dependency blocklist check for build scripts
            DependencyBlocklistScanner.scanFile(file)

            // 2. Syntax bracket check for JVM and XML files
            SyntaxBracketValidator.validateFile(file)
        }
    }

    /**
     * Executes non-throwing validation that collects all detectable errors into a ValidationReport.
     */
    suspend fun validateAndReport(project: SandboxProject): ValidationReport = withContext(Dispatchers.Default) {
        val errors = mutableListOf<ValidationError>()

        for (file in project.files) {
            // 1. Scan dependencies
            try {
                DependencyBlocklistScanner.scanFile(file)
            } catch (e: SecurityRuleBrokenException) {
                errors.add(
                    ValidationError.DisallowedDependency(
                        path = e.filePath,
                        line = e.line,
                        dependencyCoordinate = e.offendingDependency
                    )
                )
            }

            // 2. Scan syntax
            try {
                SyntaxBracketValidator.validateFile(file)
            } catch (e: SyntaxValidationException) {
                errors.add(
                    ValidationError.UnbalancedBrackets(
                        path = e.filePath,
                        line = e.line,
                        column = e.column,
                        bracketType = "brackets/braces/quotes",
                        detail = e.detailMessage
                    )
                )
            }
        }

        ValidationReport(isValid = errors.isEmpty(), errors = errors)
    }
}
