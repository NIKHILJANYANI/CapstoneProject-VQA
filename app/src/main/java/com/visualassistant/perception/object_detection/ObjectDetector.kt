package com.visualassistant.perception.object_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.visualassistant.utils.Constants
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ObjectDetector wraps a TFLite EfficientDet-Lite0 / SSD model.
 *
 * Input: RGB Bitmap
 * Output: List<DetectedObject>
 *
 * Model must be placed at: app/src/main/assets/efficientdet_lite0.tflite
 * Labels file:             app/src/main/assets/coco_labels.txt
 *
 * If the model file is missing, returns an empty list (graceful degradation).
 */
class ObjectDetector(context: Context) {

    private var interpreter: Interpreter? = null
    // Held as AutoCloseable so we never reference the GpuDelegate class directly
    // (avoids NoClassDefFoundError on devices/builds where the GPU delegate .so is absent)
    private var gpuDelegateHandle: AutoCloseable? = null
    private val labels: List<String>
    private val inputSize = Constants.OBJECT_DETECTION_INPUT_SIZE

    // EfficientDet-Lite0 output shapes
    // Output 0: [1, 25, 4]  - boxes (y1, x1, y2, x2 normalized)
    // Output 1: [1, 25]     - class indices
    // Output 2: [1, 25]     - scores
    // Output 3: [1]         - num detections
    private val MAX_DETECTIONS = 25

    init {
        labels = loadLabels(context)
        interpreter = buildInterpreter(context)
    }

    private fun loadLabels(context: Context): List<String> {
        return try {
            context.assets.open(Constants.OBJECT_DETECTION_LABELS).bufferedReader().readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load labels: ${e.message}")
            emptyList()
        }
    }

    private fun buildInterpreter(context: Context): Interpreter? {
        return try {
            val modelBuffer = FileUtil.loadMappedFile(context, Constants.OBJECT_DETECTION_MODEL)

            val options = Interpreter.Options().apply {
                numThreads = 4
                // Load GpuDelegate dynamically — catches NoClassDefFoundError (Error subclass)
                // which is NOT caught by a plain `catch (e: Exception)` block.
                try {
                    val delegateClass = Class.forName("org.tensorflow.lite.gpu.GpuDelegate")
                    val delegate = delegateClass.getDeclaredConstructor().newInstance()
                    // GpuDelegate implements Delegate and AutoCloseable
                    val addDelegateMethod = Interpreter.Options::class.java
                        .getMethod("addDelegate", Class.forName("org.tensorflow.lite.Delegate"))
                    addDelegateMethod.invoke(this, delegate)
                    gpuDelegateHandle = delegate as AutoCloseable
                    Log.d(TAG, "GPU delegate enabled via reflection")
                } catch (t: Throwable) {
                    // Throwable covers both Exception and Error (e.g. NoClassDefFoundError)
                    Log.w(TAG, "GPU delegate not available, using CPU: ${t.message}")
                }
            }
            Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model '${Constants.OBJECT_DETECTION_MODEL}': ${e.message}")
            null
        }
    }

    /**
     * Run object detection on a bitmap.
     * @param bitmap Input bitmap (any size — will be resized internally)
     * @param confidenceThreshold Minimum confidence to include a detection
     * @return List of detected objects
     */
    fun detect(bitmap: Bitmap, confidenceThreshold: Float = Constants.OBJECT_DETECTION_CONFIDENCE): List<DetectedObject> {
        val interp = interpreter ?: return emptyList()
        if (labels.isEmpty()) return emptyList()

        return try {
            val inputBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = bitmapToByteBuffer(inputBitmap)

            // Output tensors
            val outputBoxes = Array(1) { Array(MAX_DETECTIONS) { FloatArray(4) } }
            val outputClasses = Array(1) { FloatArray(MAX_DETECTIONS) }
            val outputScores = Array(1) { FloatArray(MAX_DETECTIONS) }
            val numDetections = FloatArray(1)

            val outputs = mapOf(
                0 to outputBoxes,
                1 to outputClasses,
                2 to outputScores,
                3 to numDetections
            )

            interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

            val results = mutableListOf<DetectedObject>()
            val count = numDetections[0].toInt().coerceAtMost(MAX_DETECTIONS)

            for (i in 0 until count) {
                val score = outputScores[0][i]
                if (score < confidenceThreshold) continue

                val classIdx = outputClasses[0][i].toInt()
                val label = if (classIdx < labels.size) labels[classIdx] else "unknown"

                // EfficientDet outputs boxes as [y1, x1, y2, x2] normalized
                val box = outputBoxes[0][i]
                val rectF = RectF(
                    box[1].coerceIn(0f, 1f),   // left = x1
                    box[0].coerceIn(0f, 1f),   // top = y1
                    box[3].coerceIn(0f, 1f),   // right = x2
                    box[2].coerceIn(0f, 1f)    // bottom = y2
                )

                results.add(
                    DetectedObject(
                        label = label,
                        confidence = score,
                        boundingBox = rectF,
                        frameWidth = bitmap.width,
                        frameHeight = bitmap.height
                    )
                )
            }
            if (results.isNotEmpty()) {
                Log.d(TAG, "Detected ${results.size} objects: ${results.map { "${it.label} (${(it.confidence * 100).toInt()}%)" }}")
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Detection error: ${e.message}")
            emptyList()
        }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3) // UINT8: 1 byte per RGB channel
        byteBuffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            byteBuffer.put(r.toByte())
            byteBuffer.put(g.toByte())
            byteBuffer.put(b.toByte())
        }
        return byteBuffer
    }

    /**
     * Release TFLite resources.
     */
    fun close() {
        interpreter?.close()
        gpuDelegateHandle?.close()
        interpreter = null
        gpuDelegateHandle = null
    }

    companion object {
        private const val TAG = "ObjectDetector"
    }
}
