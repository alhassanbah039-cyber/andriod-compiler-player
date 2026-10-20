package com.android.sandbox.compiler

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import com.android.sandbox.compiler.model.ProjectLanguage
import com.android.sandbox.compiler.model.SandboxProject
import com.android.sandbox.compiler.ui.SandboxViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SandboxViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApp: Application
    private lateinit var mockAssets: AssetManager
    private lateinit var mockAppInfo: ApplicationInfo

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApp = mock(Application::class.java)
        mockAssets = mock(AssetManager::class.java)
        mockAppInfo = mock(ApplicationInfo::class.java)

        val filesDir = tempFolder.newFolder("files")
        val codeCacheDir = tempFolder.newFolder("code_cache")
        val cacheDir = tempFolder.newFolder("cache")

        mockAppInfo.nativeLibraryDir = tempFolder.newFolder("libs").absolutePath
        mockAppInfo.sourceDir = tempFolder.newFile("base.apk").absolutePath

        `when`(mockApp.filesDir).thenReturn(filesDir)
        `when`(mockApp.codeCacheDir).thenReturn(codeCacheDir)
        `when`(mockApp.cacheDir).thenReturn(cacheDir)
        `when`(mockApp.applicationInfo).thenReturn(mockAppInfo)
        `when`(mockApp.assets).thenReturn(mockAssets)
        `when`(mockApp.classLoader).thenReturn(this.javaClass.classLoader)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateAndPayloadUpdate() {
        val viewModel = SandboxViewModel(mockApp)
        assertEquals("", viewModel.uiState.value.rawPayloadInput)

        val payload = "[META: LANGUAGE=JAVA]\n[FILE: Main.java]\nclass Main {}"
        viewModel.onPayloadChanged(payload)
        assertEquals(payload, viewModel.uiState.value.rawPayloadInput)
    }

    @Test
    fun testImportAndValidateEmptyPayloadSetsError() {
        val viewModel = SandboxViewModel(mockApp)
        viewModel.importAndValidateProject("")
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("empty") == true)
    }

    @Test
    fun testLoadSamplesUpdatesInputState() {
        val viewModel = SandboxViewModel(mockApp)
        viewModel.loadSampleJava()
        assertTrue(viewModel.uiState.value.rawPayloadInput.contains("LANGUAGE=JAVA"))

        viewModel.loadSampleCpp()
        assertTrue(viewModel.uiState.value.rawPayloadInput.contains("LANGUAGE=CPP"))
    }

    @Test
    fun testConsoleLogsRecorded() {
        val viewModel = SandboxViewModel(mockApp)
        assertTrue("Initial console log should be present", viewModel.uiState.value.consoleLogs.isNotEmpty())
        
        val initialSize = viewModel.uiState.value.consoleLogs.size
        viewModel.importAndValidateProject("")
        assertTrue("New error log should be recorded", viewModel.uiState.value.consoleLogs.size > initialSize)
    }
}
