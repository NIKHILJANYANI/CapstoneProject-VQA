package com.visualassistant.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.visualassistant.utils.BitmapUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraManager sets up CameraX with a Preview use case and an ImageAnalysis use case.
 *
 * The ImageAnalysis use case provides frames for the perception pipeline.
 * Frames are provided at the analysis rate (not raw camera FPS).
 *
 * Thread safety: analysis callback is called on the analysisExecutor thread.
 * The callback is responsible for its own thread management.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrameAvailable: (Bitmap) -> Unit
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Bind CameraX to the given PreviewView.
     * Automatically starts the camera.
     */
    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(previewView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(previewView: PreviewView) {
        val provider = cameraProvider ?: return

        // Preview use case
        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        // Image Analysis use case — RGBA_8888 output for easy Bitmap conversion
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    processFrame(imageProxy)
                }
            }

        // Use back camera
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            Log.d(TAG, "Camera bound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Camera use case binding failed: ${e.message}")
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()
            // Rotate if needed (CameraX handles rotation info)
            val rotatedBitmap = if (imageProxy.imageInfo.rotationDegrees != 0) {
                BitmapUtils.rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())
            } else {
                bitmap
            }
            onFrameAvailable(rotatedBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Stop the camera and release resources.
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    /**
     * Shut down the analysis executor.
     */
    fun shutdown() {
        stopCamera()
        analysisExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraManager"
    }
}
