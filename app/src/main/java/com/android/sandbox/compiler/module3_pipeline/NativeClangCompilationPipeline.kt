package com.android.sandbox.compiler.module3_pipeline

import android.content.Context
import com.android.sandbox.compiler.model.SandboxProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PIPELINE B (C++ / NATIVE):
 * Production compilation pipeline compiling C/C++ native source code into
 * an ARM64-v8a dynamic shared object (libDynamic.so) via ProcessBuilder Clang,
 * and dynamically loading the binary into memory using NativeDynamicLoader.
 */
class NativeClangCompilationPipeline(
    private val context: Context,
    private val toolchainManager: ToolchainAssetManager = ToolchainAssetManager(context)
) : CompilationPipeline {

    override val target: CompilationTarget = CompilationTarget.NATIVE_ARM64_SO

    private val compiler = NativeCCompiler(context, toolchainManager)
    private val loader = NativeDynamicLoader(context)

    /**
     * Compiles native C/C++ sources to ARM64 libDynamic.so using ProcessBuilder Clang.
     */
    override suspend fun compile(project: SandboxProject): Result<CompilationArtifact> = withContext(Dispatchers.IO) {
        compiler.compileProject(project)
    }

    /**
     * Loads the compiled libDynamic.so via System.load() and verifies JNI symbols.
     */
    override suspend fun execute(artifact: CompilationArtifact): Result<ExecutionOutput> = withContext(Dispatchers.Default) {
        loader.loadAndExecute(artifact)
    }
}
