package com.android.sandbox.compiler.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.android.sandbox.compiler.ui.theme.SandboxAppTheme

/**
 * Main host activity for the Offline "App-in-an-App" Compilation Sandbox.
 * Coordinates hardware permissions (Camera, Audio, Media/Gallery) and binds
 * the pure Jetpack Compose dashboard UI.
 */
class SandboxDashboardActivity : ComponentActivity() {

    private val viewModel: SandboxViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!cameraGranted) {
            Toast.makeText(
                this,
                "Camera permission optional: hardware camera features will be disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestHardwarePermissions()

        setContent {
            SandboxAppTheme {
                SandboxDashboardScreen(viewModel = viewModel)
            }
        }
    }

    private fun requestHardwarePermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            @Suppress("DEPRECATION")
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }
}
