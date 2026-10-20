package com.android.sandbox.compiler.module3_pipeline

import android.content.Context
import dalvik.system.DexClassLoader
import dalvik.system.DexFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.reflect.Modifier

/**
 * Loads generated classes.dex dynamically using DexClassLoader and executes
 * the target entry point within the sandbox process memory space.
 */
class DynamicDexLoader(private val context: Context) {

    /**
     * Dynamically loads and executes the compiled DEX binary.
     */
    suspend fun loadAndExecute(
        artifact: CompilationArtifact,
        targetClassName: String? = null
    ): Result<ExecutionOutput> = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val dexFile = artifact.primaryBinary

        if (!dexFile.exists() || dexFile.length() == 0L) {
            return@withContext Result.failure(
                IllegalArgumentException("Cannot load DEX: Binary does not exist or is empty at ${dexFile.absolutePath}")
            )
        }

        val codeCacheDir = File(context.codeCacheDir, "opt_exec_${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            val classLoader = DexClassLoader(
                dexFile.absolutePath,
                codeCacheDir.absolutePath,
                null,
                context.classLoader
            )

            // 1. Resolve target entry point class
            val resolvedClassName = resolveEntryClassName(dexFile, classLoader, targetClassName)
            val loadedClass = classLoader.loadClass(resolvedClassName)

            // 2. Intercept System.out / System.err during execution
            val originalOut = System.out
            val originalErr = System.err
            val capturedOut = ByteArrayOutputStream()
            val capturedErr = ByteArrayOutputStream()
            val customOut = PrintStream(capturedOut)
            val customErr = PrintStream(capturedErr)

            var executionReturn: Any? = null
            var invokedMethodName = "none"

            try {
                System.setOut(customOut)
                System.setErr(customErr)

                // 3. Locate and invoke appropriate entry point
                val mainMethod = loadedClass.methods.firstOrNull { 
                    it.name == "main" && Modifier.isStatic(it.modifiers) && it.parameterTypes.size == 1 
                }

                if (mainMethod != null) {
                    invokedMethodName = "main(String[])"
                    val args = arrayOf<String>()
                    executionReturn = mainMethod.invoke(null, args)
                } else {
                    // Try no-arg lifecycle / sandbox methods
                    val candidateMethod = loadedClass.methods.firstOrNull { 
                        it.name in listOf("getSandboxStatus", "run", "execute", "start") && it.parameterTypes.isEmpty()
                    }

                    if (candidateMethod != null) {
                        invokedMethodName = "${candidateMethod.name}()"
                        val instance = if (Modifier.isStatic(candidateMethod.modifiers)) {
                            null
                        } else {
                            val constructor = loadedClass.getDeclaredConstructor()
                            constructor.isAccessible = true
                            constructor.newInstance()
                        }
                        executionReturn = candidateMethod.invoke(instance)
                    } else {
                        // Class loaded successfully, inspect available methods
                        invokedMethodName = "classLoadedInspection"
                        executionReturn = "Loaded class '$resolvedClassName' with ${loadedClass.declaredMethods.size} method(s)."
                    }
                }
            } finally {
                System.setOut(originalOut)
                System.setErr(originalErr)
                customOut.flush()
                customErr.flush()
            }

            val stdOutStr = buildString {
                val captured = capturedOut.toString(Charsets.UTF_8.name())
                if (captured.isNotBlank()) appendLine(captured.trim())
                if (executionReturn != null) {
                    appendLine("Method [$invokedMethodName] returned: $executionReturn")
                }
                if (isEmpty()) appendLine("Execution completed successfully with no standard output.")
            }

            val stdErrStr = capturedErr.toString(Charsets.UTF_8.name())

            Result.success(
                ExecutionOutput(
                    success = true,
                    stdOut = stdOutStr,
                    stdErr = stdErrStr,
                    details = mapOf(
                        "loadedClass" to resolvedClassName,
                        "invokedMethod" to invokedMethodName,
                        "dexSize" to dexFile.length(),
                        "returnValue" to (executionReturn?.toString() ?: "void")
                    ),
                    executionDurationMs = System.currentTimeMillis() - startTime
                )
            )
        } catch (e: Exception) {
            val cause = e.cause ?: e
            Result.failure(cause)
        } finally {
            // Clean up temporary optimization directory
            codeCacheDir.deleteRecursively()
        }
    }

    /**
     * Resolves the entry class from caller preference, DEX class enumeration, or fallback presets.
     */
    private fun resolveEntryClassName(
        dexFile: File,
        classLoader: ClassLoader,
        targetClassName: String?
    ): String {
        if (!targetClassName.isNullOrBlank()) {
            try {
                classLoader.loadClass(targetClassName)
                return targetClassName
            } catch (_: ClassNotFoundException) {}
        }

        // Try Dalvik DexFile enumeration
        try {
            @Suppress("DEPRECATION")
            val dalvikDex = DexFile(dexFile)
            val entries = dalvikDex.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement()
                if (!name.contains("$") && !name.startsWith("android.") && !name.startsWith("androidx.")) {
                    return name
                }
            }
        } catch (_: Throwable) {
            // DexFile enumeration not supported or failed on this runtime
        }

        // Standard conventions fallback
        val candidates = listOf(
            "com.sandbox.MainActivity",
            "MainActivity",
            "com.sandbox.Main",
            "Main"
        )

        for (candidate in candidates) {
            try {
                classLoader.loadClass(candidate)
                return candidate
            } catch (_: ClassNotFoundException) {}
        }

        return targetClassName ?: "com.sandbox.MainActivity"
    }
}
