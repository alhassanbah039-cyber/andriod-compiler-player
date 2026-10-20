package com.android.sandbox.compiler.module3_pipeline

import com.android.sandbox.compiler.model.SandboxProject
import java.io.File

/**
 * Target compilation binary output format.
 */
enum class CompilationTarget {
    DEX_JVM,
    NATIVE_ARM64_SO
}

/**
 * Result bundle produced by a compiler pipeline.
 */
data class CompilationArtifact(
    val target: CompilationTarget,
    val primaryBinary: File,
    val intermediateFiles: List<File> = emptyList(),
    val compilerOutputLog: String,
    val compilationDurationMs: Long
)

/**
 * Output produced when executing a compiled artifact dynamically on the hardware.
 */
data class ExecutionOutput(
    val success: Boolean,
    val stdOut: String,
    val stdErr: String,
    val details: Map<String, Any> = emptyMap(),
    val executionDurationMs: Long
)

/**
 * Unified interface contract for offline sandbox compilation pipelines.
 */
interface CompilationPipeline {
    val target: CompilationTarget

    /**
     * Translates project sources into machine/dex executable binaries.
     */
    suspend fun compile(project: SandboxProject): Result<CompilationArtifact>

    /**
     * Executes the compiled artifact directly inside the Android process memory space.
     */
    suspend fun execute(artifact: CompilationArtifact): Result<ExecutionOutput>
}
