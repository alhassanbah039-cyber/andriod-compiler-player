package com.android.sandbox.compiler.module3_pipeline

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Loads compiled native shared objects (libDynamic.so) via System.load(),
 * inspects exported dynamic symbols, and manages safe JNI symbol resolution.
 */
class NativeDynamicLoader(private val context: Context) {

    /**
     * Loads the compiled dynamic shared object into memory and verifies exported symbols.
     */
    suspend fun loadAndExecute(
        artifact: CompilationArtifact
    ): Result<ExecutionOutput> = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val soFile = artifact.primaryBinary

        if (!soFile.exists() || soFile.length() == 0L) {
            return@withContext Result.failure(
                IllegalArgumentException("Cannot load native library: Binary missing at ${soFile.absolutePath}")
            )
        }

        val logBuilder = StringBuilder()
        fun log(msg: String) = logBuilder.appendLine(msg)

        log("Loading native library: ${soFile.name} (${soFile.length()} bytes)")
        log("Host Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")

        // 1. Inspect ELF binary and extract exported symbols
        val exportedSymbols = extractElfExportedSymbols(soFile)
        log("Exported Symbols found in binary: ${if (exportedSymbols.isEmpty()) "None or stripped" else exportedSymbols.joinToString(", ")}")

        // 2. Call System.load() on compiled library path
        var loadSuccessful = false
        var loadError: String? = null

        try {
            System.load(soFile.absolutePath)
            loadSuccessful = true
            log("System.load() succeeded: Shared library linked into process memory space.")
        } catch (e: UnsatisfiedLinkError) {
            loadError = e.localizedMessage ?: "UnsatisfiedLinkError"
            log("System.load() link warning/error: $loadError")
            log("(Note: If executing on x86_64 emulator while targeting ARM64, host ABI cannot execute ARM64 instructions directly without libndk_translation.)")
        } catch (e: Throwable) {
            loadError = e.localizedMessage ?: "Unexpected error"
            log("System.load() failed: $loadError")
        }

        // 3. Resolve and invoke native entry points if available
        var jniOutput: String? = null
        if (loadSuccessful) {
            try {
                // If the user defined JNI entry point: Java_com_android_sandbox_compiler_module3_pipeline_NativeSandboxBridge_getEngineSignature
                jniOutput = NativeSandboxBridge.invokeIfBound()
                if (jniOutput != null) {
                    log("Invoked native JNI bridge: $jniOutput")
                }
            } catch (e: Throwable) {
                log("JNI invocation notice: ${e.localizedMessage}")
            }
        }

        val stdOutText = buildString {
            appendLine(logBuilder.toString().trim())
            if (jniOutput != null) {
                appendLine("Native Function Output: $jniOutput")
            }
        }

        Result.success(
            ExecutionOutput(
                success = loadSuccessful || exportedSymbols.isNotEmpty(),
                stdOut = stdOutText,
                stdErr = loadError ?: "",
                details = mapOf(
                    "binaryPath" to soFile.absolutePath,
                    "exportedSymbolsCount" to exportedSymbols.size,
                    "exportedSymbols" to exportedSymbols,
                    "systemLoadSuccess" to loadSuccessful,
                    "targetAbi" to "arm64-v8a"
                ),
                executionDurationMs = System.currentTimeMillis() - startTime
            )
        )
    }

    /**
     * Inspects ELF binary header and strings to extract exported function signatures
     * without crashing on architecture mismatches.
     */
    private fun extractElfExportedSymbols(file: File): List<String> {
        val symbols = mutableListOf<String>()
        try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (magic[0] == 0x7F.toByte() && magic[1] == 'E'.code.toByte() &&
                    magic[2] == 'L'.code.toByte() && magic[3] == 'F'.code.toByte()
                ) {
                    // Search printable string symbols in ELF .rodata / .dynstr
                    val bytes = ByteArray(file.length().coerceAtMost(65536).toInt())
                    raf.seek(0)
                    raf.readFully(bytes)
                    val content = String(bytes, Charsets.ISO_8859_1)

                    val regex = Regex("""[A-Za-z_][A-Za-z0-9_]{3,}""")
                    regex.findAll(content).forEach { match ->
                        val word = match.value
                        if (word.startsWith("Java_") || word.contains("Engine") || word.contains("get") || word.contains("sandbox")) {
                            if (!symbols.contains(word)) {
                                symbols.add(word)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return symbols
    }
}

/**
 * Standard JNI bridge class for resolving sandboxed native library symbols.
 */
object NativeSandboxBridge {
    external fun getEngineSignature(): String?

    fun invokeIfBound(): String? {
        return try {
            getEngineSignature()
        } catch (_: UnsatisfiedLinkError) {
            null
        }
    }
}
