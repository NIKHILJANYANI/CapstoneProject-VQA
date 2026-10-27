package com.visualassistant.perception.ocr

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCRProcessor uses ML Kit's offline-capable Text Recognition v2.
 *
 * ML Kit Text Recognition works fully on-device (no internet required after
 * the model is bundled or downloaded via Play Services).
 *
 * This is invoked ON-DEMAND only — not on every frame.
 * Use cases:
 * - User says "Read that"
 * - User says "What does the sign say?"
 * - Automatic detection when in room-navigation mode
 */
class OCRProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Recognize text in a bitmap. Suspending — runs asynchronously.
     *
     * @param bitmap Input bitmap (can be full resolution — ML Kit handles scaling)
     * @param frameWidth Width of the original frame (for normalizing bounding boxes)
     * @param frameHeight Height of the original frame
     * @return List of TextRegions with normalized bounding boxes
     */
    suspend fun recognizeText(bitmap: Bitmap, frameWidth: Int, frameHeight: Int): List<TextRegion> =
        suspendCancellableCoroutine { cont ->
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val regions = mutableListOf<TextRegion>()
                        for (block in visionText.textBlocks) {
                            for (line in block.lines) {
                                val lineText = line.text.trim()
                                if (lineText.length < 2) continue

                                val bbox = line.boundingBox
                                val normalizedBox = if (bbox != null && frameWidth > 0 && frameHeight > 0) {
                                    RectF(
                                        bbox.left.toFloat() / frameWidth,
                                        bbox.top.toFloat() / frameHeight,
                                        bbox.right.toFloat() / frameWidth,
                                        bbox.bottom.toFloat() / frameHeight
                                    )
                                } else {
                                    RectF(0f, 0f, 1f, 1f)
                                }

                                regions.add(TextRegion.classify(lineText, normalizedBox))
                            }
                        }
                        cont.resume(regions)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "OCR failed: ${e.message}")
                        cont.resume(emptyList())
                    }
            } catch (e: Exception) {
                Log.e(TAG, "OCR error: ${e.message}")
                if (cont.isActive) cont.resume(emptyList())
            }
        }

    /**
     * Quick check — does the bitmap contain any text?
     * Less expensive than full recognition, used in exploration mode.
     */
    suspend fun hasText(bitmap: Bitmap): Boolean {
        val results = recognizeText(bitmap, bitmap.width, bitmap.height)
        return results.isNotEmpty()
    }

    fun close() {
        recognizer.close()
    }

    companion object {
        private const val TAG = "OCRProcessor"
    }
}
