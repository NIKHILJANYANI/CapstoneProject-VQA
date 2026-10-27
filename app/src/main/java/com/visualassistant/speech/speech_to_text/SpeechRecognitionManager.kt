package com.visualassistant.speech.speech_to_text

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * SpeechRecognitionManager handles offline-capable speech-to-text.
 *
 * Uses Android's built-in SpeechRecognizer which can work offline if the
 * user has downloaded the offline speech recognition model in their device settings.
 *
 * Callback-based (not suspending) since SpeechRecognizer requires the main thread.
 *
 * Usage:
 * 1. Call startListening() when user taps / double-taps
 * 2. Receive results via onResult callback
 * 3. Errors are reported via onError callback
 */
class SpeechRecognitionManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStarted: () -> Unit = {},
    private val onListeningStopped: () -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private fun createRecognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    /**
     * Start listening for a voice command.
     * Must be called on the main thread.
     */
    fun startListening() {
        if (_isListening.value) {
            stopListening()
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "SpeechRecognizer is not available on this device.")
            onError("Speech recognition is not available on this device.")
            return
        }

        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up previous recognizer: ${e.message}")
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        onListeningStarted()
                        Log.d(TAG, "Ready for speech")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Beginning of speech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        onListeningStopped()
                        Log.d(TAG, "End of speech")
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        onListeningStopped()
                        val errorMessage = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording issue. Please check microphone."
                            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                            SpeechRecognizer.ERROR_NETWORK -> "Network issue. Please check your internet connection."
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out. Please try again."
                            SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Please try speaking again."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy. Please try again."
                            SpeechRecognizer.ERROR_SERVER -> "Recognition server error. Please try again."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything. Tap to speak again."
                            else -> "Could not recognize speech ($error). Please try again."
                        }
                        Log.w(TAG, "Recognition error: $errorMessage ($error)")
                        onError(errorMessage)
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        onListeningStopped()
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val bestResult = matches[0]
                            Log.d(TAG, "Recognized: $bestResult")
                            onResult(bestResult)
                        } else {
                            onError("I didn't catch that. Please speak again.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val partial = matches[0]
                            Log.v(TAG, "Partial: $partial")
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            speechRecognizer?.startListening(createRecognizerIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition: ${e.message}")
            onError("Failed to start speech recognition: ${e.message}")
            _isListening.value = false
            onListeningStopped()
        }
    }

    /**
     * Stop listening.
     */
    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer: ${e.message}")
        }
        _isListening.value = false
        onListeningStopped()
    }

    /**
     * Release resources.
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        _isListening.value = false
    }

    companion object {
        private const val TAG = "SpeechRecognitionMgr"
    }
}
