package com.android.sandbox.compiler.module3_pipeline

import android.content.Context
import com.android.sandbox.compiler.model.SandboxProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PIPELINE A (JAVA / JVM):
 * Production compilation pipeline directing Java sources through ECJ,
 * processing output bytecode with Android D8 to generate classes.dex,
 * and dynamically executing the binary in-process using DynamicDexLoader.
 */
class JavaD8CompilationPipeline(
    private val context: Context,
    private val toolchainManager: ToolchainAssetManager = ToolchainAssetManager(context)
) : CompilationPipeline {

    override val target: CompilationTarget = CompilationTarget.DEX_JVM

    private val compiler = JavaDexCompiler(context, toolchainManager)
    private val loader = DynamicDexLoader(context)

    /**
     * Compiles Java source files to classes.dex using sequential ECJ + D8 pipeline.
     */
    override suspend fun compile(project: SandboxProject): Result<CompilationArtifact> = withContext(Dispatchers.IO) {
        compiler.compileProject(project)
    }

    /**
     * Loads the generated classes.dex dynamically via DexClassLoader and invokes entry point.
     */
    override suspend fun execute(artifact: CompilationArtifact): Result<ExecutionOutput> = withContext(Dispatchers.Default) {
        loader.loadAndExecute(artifact)
    }
}
