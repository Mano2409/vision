package com.visionnav.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Android SpeechRecognizer wrapper for VisionNav voice input.
 * Manages audio listening, volume RMS levels for UI pulse animations,
 * and robust error recovery.
 */
class VoiceRecognizer(
    private val context: Context,
    private val onSpeechResult: (String) -> Unit,
    private val onErrorOccurred: (String) -> Unit
) : RecognitionListener {

    private val tag = "VoiceRecognizer"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening() {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(this@VoiceRecognizer)
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                speechRecognizer?.startListening(intent)
                _isListening.value = true
                Log.i(tag, "Speech recognition listening started")
            } catch (e: Exception) {
                Log.e(tag, "Error starting speech recognition: ${e.message}")
                _isListening.value = false
                onErrorOccurred("Could not start microphone: ${e.message}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                _isListening.value = false
            } catch (e: Exception) {
                Log.e(tag, "Error stopping speech recognition: ${e.message}")
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                _isListening.value = false
                _rmsLevel.value = 0f
            } catch (e: Exception) {
                Log.e(tag, "Error cancelling speech recognition: ${e.message}")
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                _isListening.value = false
            } catch (e: Exception) {
                Log.e(tag, "Error destroying speech recognizer: ${e.message}")
            }
        }
    }

    // RecognitionListener Implementation

    override fun onReadyForSpeech(params: Bundle?) {
        _isListening.value = true
        Log.d(tag, "Microphone ready for speech")
    }

    override fun onBeginningOfSpeech() {
        Log.d(tag, "User began speaking")
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Expose normalized RMS volume level for UI pulse animation
        _rmsLevel.value = (rmsdB / 10f).coerceIn(0f, 1f)
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _isListening.value = false
        Log.d(tag, "User finished speaking")
    }

    override fun onError(error: Int) {
        _isListening.value = false
        _rmsLevel.value = 0f

        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please try speaking again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected within time limit."
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition."
            else -> "Speech recognition error (code $error)."
        }

        Log.w(tag, "SpeechRecognizer error: $errorMessage (code $error)")
        onErrorOccurred(errorMessage)
    }

    override fun onResults(results: Bundle?) {
        _isListening.value = false
        _rmsLevel.value = 0f

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val recognizedText = matches?.firstOrNull()

        if (!recognizedText.isNullOrBlank()) {
            Log.i(tag, "Speech recognition match: $recognizedText")
            onSpeechResult(recognizedText)
        } else {
            onErrorOccurred("Could not understand destination. Please try again.")
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
