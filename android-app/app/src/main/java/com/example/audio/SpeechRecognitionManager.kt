package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Manages Android native speech recognition.
 *
 * Responsibilities:
 * - Listens to user question via microphone / Bluetooth headset
 * - Emits progress updates ("Listening...", "Understanding question...")
 * - Returns recognized text string
 * - Gracefully maps and reports speech recognition errors
 */
class SpeechRecognitionManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        onStatusChanged: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isAvailable) {
            onError("Speech recognition is not available on this device.")
            return
        }

        destroy()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        onStatusChanged("Listening...")
                    }

                    override fun onBeginningOfSpeech() {
                        onStatusChanged("Listening to question...")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Volume changes if needed
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                    }

                    override fun onEndOfSpeech() {
                        onStatusChanged("Understanding question...")
                    }

                    override fun onError(error: Int) {
                        val message = getErrorMessage(error)
                        Log.w("SpeechRecognizer", "Error code $error: $message")
                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val question = matches?.firstOrNull()?.trim()
                        if (!question.isNullOrBlank()) {
                            onResult(question)
                        } else {
                            onError("No speech was recognized. Please try asking again.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            onStatusChanged("Hearing: \"$partial\"")
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {
                    }
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            onStatusChanged("Listening...")
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            onError("Failed to start voice recognition: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w("SpeechRecognizer", "Error stopping: ${e.message}")
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("SpeechRecognizer", "Error destroying: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Client speech recognition error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out during speech recognition."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly into the microphone."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Please try again."
            SpeechRecognizer.ERROR_SERVER -> "Speech server error. Please try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please press Ask SeeAssist and speak."
            else -> "Speech recognition error ($errorCode). Please try again."
        }
    }
}
