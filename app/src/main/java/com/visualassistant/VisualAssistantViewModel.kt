package com.visualassistant

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.visualassistant.assistant.AssistantEngine
import com.visualassistant.camera.CameraManager
import com.visualassistant.memory.SpatialMemory
import com.visualassistant.perception.PerceptionPipeline
import com.visualassistant.perception.hazard.HazardDetector
import com.visualassistant.perception.object_detection.ObjectDetector
import com.visualassistant.perception.ocr.OCRProcessor
import com.visualassistant.speech.speech_to_text.SpeechRecognitionManager
import com.visualassistant.speech.text_to_speech.TTSManager
import com.visualassistant.world.WorldModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * VisualAssistantViewModel coordinates all subsystems.
 *
 * It owns the lifecycle of:
 * - TTSManager
 * - SpeechRecognitionManager
 * - ObjectDetector (TFLite)
 * - OCRProcessor (ML Kit)
 * - HazardDetector
 * - SpatialMemory
 * - PerceptionPipeline
 * - AssistantEngine
 *
 * The UI (MainActivity / MainScreen) observes StateFlows from this ViewModel.
 */
class VisualAssistantViewModel(application: Application) : AndroidViewModel(application) {

    // ─── State ───────────────────────────────────────────────────────────────────

    private val _isAssistantActive = MutableStateFlow(false)
    val isAssistantActive: StateFlow<Boolean> = _isAssistantActive

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _statusText = MutableStateFlow("Press START to begin")
    val statusText: StateFlow<String> = _statusText

    private val _lastSpokenText = MutableStateFlow("")
    val lastSpokenText: StateFlow<String> = _lastSpokenText

    private val _worldModel = MutableStateFlow(WorldModel())
    val worldModel: StateFlow<WorldModel> = _worldModel

    // ─── Subsystems ──────────────────────────────────────────────────────────────

    val ttsManager = TTSManager(application)

    private val spatialMemory = SpatialMemory(application)

    val assistantEngine = AssistantEngine(
        context = application,
        ttsManager = ttsManager,
        spatialMemory = spatialMemory
    )

    private val objectDetector = ObjectDetector(application)
    private val ocrProcessor = OCRProcessor()
    private val hazardDetector = HazardDetector()
    private val cloudVisionService = com.visualassistant.perception.cloud.CloudVisionService("xx")

    private var lastFrame: Bitmap? = null

    private val perceptionPipeline = PerceptionPipeline(
        objectDetector = objectDetector,
        ocrProcessor = ocrProcessor,
        hazardDetector = hazardDetector,
        spatialMemory = spatialMemory,
        scope = viewModelScope,
        onWorldModelUpdated = { worldModel ->
            _worldModel.value = worldModel

            // Extract hazards for the assistant
            val currentHazards = hazardDetector.detectHazards(worldModel.trackedObjects).toMutableList()
            hazardDetector.detectApproachingPerson(worldModel.trackedObjects)?.let { currentHazards.add(it) }
            hazardDetector.detectObstacleInPath(worldModel.trackedObjects)?.let { currentHazards.add(it) }

            assistantEngine.onWorldModelUpdated(worldModel, currentHazards, _isListening.value)
        }
    )

    lateinit var speechRecognitionManager: SpeechRecognitionManager

    /**
     * Initialize speech recognition (requires context — called from Activity after permission granted).
     */
    fun initSpeechRecognition(context: android.content.Context = getApplication()) {
        speechRecognitionManager = SpeechRecognitionManager(
            context = context,
            onResult = { spokenText ->
                _statusText.value = "You said: $spokenText"
                assistantEngine.onUserSpeech(spokenText)
            },
            onError = { errorMsg ->
                _statusText.value = errorMsg
                Log.w(TAG, "STT Error: $errorMsg")
                if (_isAssistantActive.value) {
                    ttsManager.speakNow(errorMsg)
                }
            },
            onListeningStarted = {
                _isListening.value = true
                _statusText.value = "Listening..."
            },
            onListeningStopped = {
                _isListening.value = false
                if (_isAssistantActive.value) {
                    _statusText.value = "Assistant active. Tap to speak."
                }
            }
        )

        assistantEngine.onCloudDescriptionRequested = {
            val frame = lastFrame
            if (frame != null) {
                viewModelScope.launch(Dispatchers.Main) {
                    val result = cloudVisionService.describeImage(frame, "Describe this scene in detail for a visually impaired person. Focus on important objects, layout, and any text.")
                    if (result != null) {
                        ttsManager.speakNow(result)
                        _statusText.value = result
                    } else {
                        ttsManager.speakNow("Sorry, I couldn't reach the cloud service.")
                    }
                }
            } else {
                ttsManager.speakNow("I don't have a clear view right now.")
            }
        }
    }

    /**
     * Process a camera frame through the perception pipeline.
     */
    fun processFrame(bitmap: Bitmap) {
        if (!_isAssistantActive.value) return
        lastFrame = bitmap
        perceptionPipeline.processFrame(bitmap)
    }

    /**
     * Start the visual assistant.
     */
    fun startAssistant() {
        _isAssistantActive.value = true
        _statusText.value = "Assistant active. Tap to speak."
        assistantEngine.start()
        perceptionPipeline.reset()
        Log.d(TAG, "Assistant started")
    }

    /**
     * Stop the visual assistant.
     */
    fun stopAssistant() {
        _isAssistantActive.value = false
        _statusText.value = "Press START to begin"
        _isListening.value = false
        assistantEngine.stop()
        if (::speechRecognitionManager.isInitialized) {
            speechRecognitionManager.stopListening()
        }
        Log.d(TAG, "Assistant stopped")
    }

    /**
     * Toggle start/stop.
     */
    fun toggleAssistant() {
        if (_isAssistantActive.value) stopAssistant() else startAssistant()
    }

    /**
     * Start listening for a voice command.
     */
    fun startListening() {
        if (!_isAssistantActive.value) return
        if (::speechRecognitionManager.isInitialized) {
            // Stop TTS while listening
            ttsManager.stopSpeaking()
            speechRecognitionManager.startListening()
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
        objectDetector.close()
        ocrProcessor.close()
        if (::speechRecognitionManager.isInitialized) {
            speechRecognitionManager.destroy()
        }
    }

    companion object {
        private const val TAG = "VisualAssistantVM"
    }
}
