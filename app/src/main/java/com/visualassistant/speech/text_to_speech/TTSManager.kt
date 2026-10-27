package com.visualassistant.speech.text_to_speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.visualassistant.assistant.priority.Priority
import com.visualassistant.assistant.priority.SpeechEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.PriorityBlockingQueue

/**
 * TTSManager handles all text-to-speech output with a priority queue.
 *
 * Features:
 * - Priority-based speech queue (CRITICAL messages interrupt lower-priority ones)
 * - Speech deduplication with per-event cooldowns
 * - Configurable speech rate and pitch
 * - State tracking (speaking/idle)
 */
class TTSManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    // Priority queue — highest priority events first
    private val speechQueue = PriorityBlockingQueue<SpeechEvent>()

    // Cooldown tracking — key → last spoken time
    private val cooldownMap = mutableMapOf<String, Long>()

    // Current utterance priority
    private var currentPriority: Priority = Priority.IGNORE

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    var speechRate: Float = 0.9f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    var pitch: Float = 1.0f
        set(value) {
            field = value
            tts?.setPitch(value)
        }

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val supported = trySetLanguage(Locale.US) ||
                    trySetLanguage(Locale.getDefault()) ||
                    trySetLanguage(Locale.ENGLISH)

            if (supported) {
                isInitialized = true
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(pitch)
                setupUtteranceListener()
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.w(TAG, "No supported English TTS language found, but initializing anyway")
                isInitialized = true
                setupUtteranceListener()
            }
        } else {
            Log.e(TAG, "TTS initialization failed with code $status")
        }
    }

    private fun trySetLanguage(loc: Locale): Boolean {
        val result = tts?.setLanguage(loc) ?: TextToSpeech.LANG_NOT_SUPPORTED
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                currentPriority = Priority.IGNORE
                processQueue()
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                currentPriority = Priority.IGNORE
                processQueue()
            }
        })
    }

    /**
     * Enqueue a speech event. Respects cooldown and priority.
     *
     * @param event The speech event to enqueue
     */
    fun speak(event: SpeechEvent) {
        if (!isInitialized) return

        // Check cooldown
        val lastSpoken = cooldownMap[event.cooldownKey] ?: 0L
        if (System.currentTimeMillis() - lastSpoken < event.cooldownMs) {
            Log.v(TAG, "Skipping (cooldown): ${event.text}")
            return
        }

        // If this event should interrupt and current is lower priority
        if (event.shouldInterrupt && event.priority.isHigherThan(currentPriority)) {
            tts?.stop()
            speechQueue.clear()
            speakImmediately(event)
            return
        }

        // Add to queue
        speechQueue.offer(event)
        
        // Guard against stale _isSpeaking state: check if engine is actually busy
        val isActuallySpeaking = _isSpeaking.value && (tts?.isSpeaking == true)
        if (!isActuallySpeaking) {
            _isSpeaking.value = false
            processQueue()
        }
    }

    /**
     * Convenience method: speak a string with a given priority.
     */
    fun speak(text: String, priority: Priority, cooldownKey: String? = null, cooldownMs: Long = 5000L) {
        speak(
            SpeechEvent(
                text = text,
                priority = priority,
                cooldownKey = cooldownKey ?: text.take(30),
                cooldownMs = cooldownMs
            )
        )
    }

    /**
     * Speak immediately, interrupting current speech.
     */
    fun speakNow(text: String) {
        if (!isInitialized) return
        tts?.stop()
        speechQueue.clear()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UUID.randomUUID().toString())
        }
        _isSpeaking.value = true
        currentPriority = Priority.CRITICAL
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID))
        cooldownMap["_immediate_"] = System.currentTimeMillis()
    }

    /**
     * Stop all current and queued speech.
     */
    fun stopSpeaking() {
        tts?.stop()
        speechQueue.clear()
        _isSpeaking.value = false
        currentPriority = Priority.IGNORE
    }

    /**
     * Whether the TTS is currently speaking.
     */
    fun isSpeakingNow(): Boolean = tts?.isSpeaking == true

    private fun speakImmediately(event: SpeechEvent) {
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UUID.randomUUID().toString())
        }
        currentPriority = event.priority
        cooldownMap[event.cooldownKey] = System.currentTimeMillis()
        _isSpeaking.value = true
        tts?.speak(event.text, TextToSpeech.QUEUE_FLUSH, params,
            params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID))
    }

    private fun processQueue() {
        val next = speechQueue.poll() ?: return
        speakImmediately(next)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "TTSManager"
    }
}
