package com.visualassistant.perception

import android.graphics.Bitmap
import android.util.Log
import com.visualassistant.memory.SpatialMemory
import com.visualassistant.perception.hazard.HazardDetector
import com.visualassistant.perception.hazard.HazardEvent
import com.visualassistant.perception.object_detection.ObjectDetector
import com.visualassistant.perception.object_detection.ObjectTracker
import com.visualassistant.perception.ocr.OCRProcessor
import com.visualassistant.perception.ocr.TextRegion
import com.visualassistant.utils.Constants
import com.visualassistant.world.TrackedObject
import com.visualassistant.world.WorldModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * PerceptionPipeline orchestrates all perception modules.
 *
 * Frame scheduling:
 * - Object detection: every ~200ms (~5 FPS)
 * - Hazard detection: same pass as object detection
 * - Depth: not implemented in this milestone (uses bbox-based distance proxy)
 * - OCR: on-demand only
 *
 * Updates WorldModel and calls back onWorldModelUpdated.
 */
class PerceptionPipeline(
    private val objectDetector: ObjectDetector,
    private val ocrProcessor: OCRProcessor,
    private val hazardDetector: HazardDetector,
    private val spatialMemory: SpatialMemory,
    private val scope: CoroutineScope,
    private val onWorldModelUpdated: (WorldModel) -> Unit
) {
    private val tracker = ObjectTracker()

    // Timestamps for throttling
    private val lastObjectDetectionMs = AtomicLong(0)
    private val lastOCRMs = AtomicLong(0)

    // Current world model (thread-safe read)
    @Volatile private var currentWorldModel = WorldModel()

    // Cached last OCR results (avoid re-running every frame)
    private var cachedTextRegions: List<TextRegion> = emptyList()

    /**
     * Process a camera frame. Called from the camera's analysis use case.
     * This is called ~30 FPS; internal throttling limits expensive operations.
     *
     * @param bitmap Current camera frame
     * @param isOCRRequested True if the user just asked to read text
     */
    fun processFrame(bitmap: Bitmap, isOCRRequested: Boolean = false) {
        val now = System.currentTimeMillis()
        val runOD = (now - lastObjectDetectionMs.get()) >= Constants.OBJECT_DETECTION_INTERVAL_MS
        val runOCR = isOCRRequested || (now - lastOCRMs.get()) >= 10_000L  // OCR auto every 10s in nav mode

        if (!runOD && !runOCR) return

        scope.launch(Dispatchers.Default) {
            try {
                var trackedObjects: List<TrackedObject> = tracker.getCurrentTracks()
                var textRegions: List<TextRegion> = cachedTextRegions
                var hazards: List<HazardEvent> = emptyList()

                // Object Detection
                if (runOD) {
                    lastObjectDetectionMs.set(now)
                    val detections = objectDetector.detect(bitmap)
                    trackedObjects = tracker.update(detections)

                    // Hazard detection runs in the same pass
                    val baseHazards = hazardDetector.detectHazards(trackedObjects).toMutableList()
                    hazardDetector.detectApproachingPerson(trackedObjects)?.let { baseHazards.add(it) }
                    hazardDetector.detectObstacleInPath(trackedObjects)?.let { baseHazards.add(it) }
                    hazards = baseHazards.distinctBy { it.type }
                }

                // OCR
                if (runOCR) {
                    lastOCRMs.set(now)
                    textRegions = ocrProcessor.recognizeText(bitmap, bitmap.width, bitmap.height)
                    cachedTextRegions = textRegions

                    // Auto-store room numbers in memory
                    textRegions.filter { it.isRoomNumber }.forEach { region ->
                        val nearbyElevator = trackedObjects.find { it.label.equals("elevator", ignoreCase = true) }
                        spatialMemory.storeRoom(
                            roomNumber = region.text,
                            relativePosition = "ahead",
                            nearbyLandmark = nearbyElevator?.label
                        )
                        Log.d(TAG, "Stored room in memory: ${region.text}")
                    }

                    // Auto-store landmarks
                    textRegions.filter { it.isSign && !it.isRoomNumber }.forEach { region ->
                        spatialMemory.storeLandmark(
                            label = region.text.lowercase(),
                            relativePosition = "ahead"
                        )
                    }
                }

                // Build navigation targets
                val navTargets = trackedObjects.filter {
                    it.label in setOf("door", "elevator", "exit", "stairs", "escalator")
                }

                // Also auto-store navigation landmarks in memory
                navTargets.forEach { target ->
                    spatialMemory.storeLandmark(
                        label = target.label,
                        relativePosition = target.position.toDescription(),
                        contextDescription = "${target.distanceCategory.name.lowercase()} distance"
                    )
                }

                // Update world model
                val newWorldModel = WorldModel(
                    timestampMs = now,
                    trackedObjects = trackedObjects,
                    detectedText = textRegions,
                    hazards = trackedObjects.filter { obj ->
                        hazards.any { h ->
                            com.visualassistant.perception.hazard.HazardType.fromLabel(obj.label) != null
                        }
                    },
                    navigationTargets = navTargets
                )

                currentWorldModel = newWorldModel

                // Notify on main thread
                withContext(Dispatchers.Main) {
                    onWorldModelUpdated(newWorldModel)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Perception pipeline error: ${e.message}")
            }
        }
    }

    /**
     * Trigger an immediate OCR pass on the next frame.
     */
    fun requestOCR() {
        lastOCRMs.set(0)  // Force OCR on next frame
    }

    /**
     * Get the current world model.
     */
    fun getCurrentWorldModel(): WorldModel = currentWorldModel

    /**
     * Reset all tracking state.
     */
    fun reset() {
        tracker.reset()
        cachedTextRegions = emptyList()
        currentWorldModel = WorldModel()
        lastObjectDetectionMs.set(0)
        lastOCRMs.set(0)
    }

    private fun com.visualassistant.world.SpatialPosition.toDescription() = when (this) {
        com.visualassistant.world.SpatialPosition.LEFT -> "to your left"
        com.visualassistant.world.SpatialPosition.CENTER -> "ahead"
        com.visualassistant.world.SpatialPosition.RIGHT -> "to your right"
        else -> "nearby"
    }

    companion object {
        private const val TAG = "PerceptionPipeline"
    }
}
