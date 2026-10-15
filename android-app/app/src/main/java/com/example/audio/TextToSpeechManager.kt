package com.example.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Text-to-Speech output for visually impaired users.
 *
 * Responsibilities:
 * - Automatically speaks the AI answer upon receipt
 * - Routes speech output to Bluetooth earbud when connected, otherwise phone speaker
 * - Manages speech rate, pitch, and playback lifecycle
 */
class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeech: String? = null
    private var pendingCallback: (() -> Unit)? = null
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    utteranceId?.let { id ->
                        callbacks.remove(id)?.invoke()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    utteranceId?.let { id ->
                        callbacks.remove(id)?.invoke()
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    Log.w("TTS", "Error speaking utterance $utteranceId, code $errorCode")
                    utteranceId?.let { id ->
                        callbacks.remove(id)?.invoke()
                    }
                }
            })

            isInitialized = true
            pendingSpeech?.let { text ->
                val cb = pendingCallback
                pendingSpeech = null
                pendingCallback = null
                speak(text, cb)
            }
        } else {
            Log.e("TTS", "TextToSpeech initialization failed with status $status")
            val cb = pendingCallback
            pendingSpeech = null
            pendingCallback = null
            cb?.invoke()
        }
    }

    /**
     * Speaks the provided text out loud.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        if (!isInitialized) {
            pendingSpeech = text
            pendingCallback = onDone
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        if (onDone != null) {
            callbacks[utteranceId] = onDone
        }
        val params = Bundle()

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    /**
     * Stops current speech output.
     */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        callbacks.clear()
    }

    /**
     * Shuts down the TTS engine.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _isSpeaking.value = false
        callbacks.clear()
    }
}
