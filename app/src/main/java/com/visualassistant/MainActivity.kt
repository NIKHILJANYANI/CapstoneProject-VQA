package com.visualassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import com.visualassistant.camera.CameraManager
import com.visualassistant.ui.MainScreen
import com.visualassistant.ui.theme.VisualAssistantTheme

/**
 * MainActivity — entry point for the Visual Assistant.
 *
 * Responsibilities:
 * - Request permissions (camera, microphone)
 * - Initialize CameraManager
 * - Set up Compose UI
 * - Connect camera frames to ViewModel
 */
class MainActivity : ComponentActivity() {

    private val viewModel: VisualAssistantViewModel by viewModels()
    private lateinit var cameraManager: CameraManager
    private lateinit var previewView: PreviewView

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true

        if (cameraGranted && audioGranted) {
            onPermissionsGranted()
        } else {
            viewModel.ttsManager.speakNow(
                "Camera and microphone permissions are required for the Visual Assistant to work. " +
                "Please grant them in your device settings."
            )
            Log.e(TAG, "Required permissions not granted. Camera: $cameraGranted, Audio: $audioGranted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create PreviewView for camera feed
        previewView = PreviewView(this)

        // Set Compose content
        setContent {
            VisualAssistantTheme {
                MainScreen(
                    viewModel = viewModel,
                    previewView = previewView
                )
            }
        }

        // Check and request permissions
        if (hasRequiredPermissions()) {
            onPermissionsGranted()
        } else {
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun onPermissionsGranted() {
        // Initialize speech recognition
        viewModel.initSpeechRecognition(this)

        // Initialize camera
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            onFrameAvailable = { bitmap ->
                viewModel.processFrame(bitmap)
            }
        )
        cameraManager.startCamera(previewView)
        Log.d(TAG, "Permissions granted, camera and speech initialized")
    }

    private fun hasRequiredPermissions(): Boolean =
        REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraManager.isInitialized) {
            cameraManager.shutdown()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}
