package com.android.sandbox.compiler

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import com.android.sandbox.compiler.model.*
import com.android.sandbox.compiler.module3_pipeline.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*
import java.io.ByteArrayInputStream
import java.io.File

class Module3ExecutionPipelineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockAssets: AssetManager
    private lateinit var mockAppInfo: ApplicationInfo
    private lateinit var filesDir: File
    private lateinit var codeCacheDir: File
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockAssets = mock(AssetManager::class.java)
        mockAppInfo = mock(ApplicationInfo::class.java)

        filesDir = tempFolder.newFolder("files")
        codeCacheDir = tempFolder.newFolder("code_cache")
        cacheDir = tempFolder.newFolder("cache")

        mockAppInfo.nativeLibraryDir = tempFolder.newFolder("native_libs").absolutePath
        mockAppInfo.sourceDir = tempFolder.newFile("dummy_base.apk").absolutePath

        `when`(mockContext.filesDir).thenReturn(filesDir)
        `when`(mockContext.codeCacheDir).thenReturn(codeCacheDir)
        `when`(mockContext.cacheDir).thenReturn(cacheDir)
        `when`(mockContext.applicationInfo).thenReturn(mockAppInfo)
        `when`(mockContext.assets).thenReturn(mockAssets)
        `when`(mockContext.classLoader).thenReturn(this.javaClass.classLoader)
    }

    @Test
    fun testToolchainAssetManagerDirectories() = runBlocking {
        val manager = ToolchainAssetManager(mockContext)

        val jvmResult = manager.ensureJvmToolchainExtracted()
        assertTrue(jvmResult.isSuccess)
        assertTrue(manager.jvmToolsDir.exists())

        val nativeResult = manager.ensureNativeToolchainExtracted()
        assertTrue(nativeResult.isSuccess)
        assertTrue(manager.nativeToolchainDir.exists())
    }

    @Test
    fun testJavaDexCompilerPipelineSequentialExecution() = runBlocking {
        val manager = ToolchainAssetManager(mockContext)
        val compiler = JavaDexCompiler(mockContext, manager)

        val projectRoot = tempFolder.newFolder("TestJavaProject")
        val javaSource = """
            package com.sandbox;

            public class MainActivity {
                public String getSandboxStatus() {
                    return "Pipeline A Java Test Running";
                }
            }
        """.trimIndent()

        val project = SandboxProject(
            id = "TestJavaProject",
            name = "TestJavaProject",
            rootDirectory = projectRoot,
            metadata = ProjectMetadata(language = ProjectLanguage.JAVA, explicitName = "TestJavaProject"),
            files = listOf(
                SandboxFile(
                    relativePath = "app/src/main/java/com/sandbox/MainActivity.java",
                    content = javaSource,
                    fileType = SandboxFileType.SOURCE_JAVA
                )
            )
        )

        val result = compiler.compileProject(project)
        assertTrue("Compilation should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val artifact = result.getOrThrow()
        assertEquals(CompilationTarget.DEX_JVM, artifact.target)
        assertTrue(artifact.primaryBinary.exists())
        assertEquals("classes.dex", artifact.primaryBinary.name)
        assertTrue("Compiler output log should be non-empty", artifact.compilerOutputLog.contains("PIPELINE A"))
    }

    @Test
    fun testDynamicDexLoaderValidation() = runBlocking {
        val loader = DynamicDexLoader(mockContext)

        val dummyDexFile = tempFolder.newFile("classes.dex")
        // Minimal valid DEX header magic: "dex\n035\0"
        val header = byteArrayOf(
            0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x70, 0x00, 0x00, 0x00,
            0x70, 0x00, 0x00, 0x00
        )
        val dexBytes = ByteArray(112)
        System.arraycopy(header, 0, dexBytes, 0, header.size)
        dummyDexFile.writeBytes(dexBytes)

        val artifact = CompilationArtifact(
            target = CompilationTarget.DEX_JVM,
            primaryBinary = dummyDexFile,
            compilerOutputLog = "Compilation log",
            compilationDurationMs = 150
        )

        // Attempting to load empty/synthetic DEX in desktop unit test JVM will safely return result or handle ClassNotFoundException
        val result = loader.loadAndExecute(artifact, targetClassName = "com.sandbox.MainActivity")
        assertNotNull(result)
    }

    @Test
    fun testNativeCCompilerPipelineArm64Targeting() = runBlocking {
        val manager = ToolchainAssetManager(mockContext)
        val compiler = NativeCCompiler(mockContext, manager)

        val projectRoot = tempFolder.newFolder("TestCppProject")
        val cppSource = """
            #include <string>

            extern "C" const char* getEngineSignature() {
                return "ARM64 Dynamic Sandbox Engine Active";
            }
        """.trimIndent()

        val project = SandboxProject(
            id = "TestCppProject",
            name = "TestCppProject",
            rootDirectory = projectRoot,
            metadata = ProjectMetadata(language = ProjectLanguage.CPP, explicitName = "TestCppProject"),
            files = listOf(
                SandboxFile(
                    relativePath = "app/src/main/cpp/native-engine.cpp",
                    content = cppSource,
                    fileType = SandboxFileType.SOURCE_CPP
                )
            )
        )

        val result = compiler.compileProject(project)
        assertTrue("Native compilation should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val artifact = result.getOrThrow()
        assertEquals(CompilationTarget.NATIVE_ARM64_SO, artifact.target)
        assertTrue(artifact.primaryBinary.exists())
        assertEquals("libDynamic.so", artifact.primaryBinary.name)
        assertTrue(artifact.compilerOutputLog.contains("PIPELINE B"))
    }

    @Test
    fun testNativeDynamicLoaderElfInspection() = runBlocking {
        val loader = NativeDynamicLoader(mockContext)

        val dummySo = tempFolder.newFile("libDynamic.so")
        // Standard ELF64 header for ARM64
        val elfHeader = byteArrayOf(
            0x7F, 0x45, 0x4C, 0x46,
            0x02, 0x01, 0x01, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x03, 0x00, 0xB7.toByte(), 0x00, 0x01, 0x00, 0x00, 0x00
        )
        val dummyBytes = ByteArray(256)
        System.arraycopy(elfHeader, 0, dummyBytes, 0, elfHeader.size)
        // Add test string symbol
        val symbol = "Java_com_sandbox_NativeEngine_getEngineSignature"
        System.arraycopy(symbol.toByteArray(), 0, dummyBytes, 64, symbol.length)
        dummySo.writeBytes(dummyBytes)

        val artifact = CompilationArtifact(
            target = CompilationTarget.NATIVE_ARM64_SO,
            primaryBinary = dummySo,
            compilerOutputLog = "Native compile log",
            compilationDurationMs = 250
        )

        val result = loader.loadAndExecute(artifact)
        assertTrue(result.isSuccess)

        val output = result.getOrThrow()
        val symbols = output.details["exportedSymbols"] as? List<*>
        assertNotNull(symbols)
        assertTrue("Should detect exported symbol in ELF", symbols?.contains(symbol) == true)
    }
}
