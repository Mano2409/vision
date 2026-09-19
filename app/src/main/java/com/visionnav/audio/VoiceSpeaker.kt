package com.visionnav.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Native Android TextToSpeech wrapper.
 * Configured with USAGE_ASSISTANCE_SONIFICATION to ensure critical warnings
 * are audible above ambient urban traffic noise.
 */
class VoiceSpeaker(
    context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private val tag = "VoiceSpeaker"
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    var onUtteranceDone: ((String) -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(tag, "Default locale missing or unsupported, falling back to Locale.US")
                tts?.setLanguage(Locale.US)
            }

            // Speed up slightly (1.1x) for BVI cognitive agility
            tts?.setSpeechRate(1.1f)
            tts?.setPitch(1.0f)

            // Audio attributes: Ensure voice guidance uses navigation/sonification channel
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    utteranceId?.let { onUtteranceDone?.invoke(it) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    Log.e(tag, "TTS playback error on utterance: $utteranceId")
                }
            })

            isInitialized = true
            onInitComplete?.invoke(true)
            Log.i(tag, "TextToSpeech successfully initialized with USAGE_ASSISTANCE_SONIFICATION")
        } else {
            Log.e(tag, "TextToSpeech initialization failed with status $status")
            isInitialized = false
            onInitComplete?.invoke(false)
        }
    }

    /**
     * Speak text using Android TTS engine.
     * @param text Text string to synthesize
     * @param queueMode TextToSpeech.QUEUE_FLUSH (cut off current) or TextToSpeech.QUEUE_ADD
     * @param utteranceId Identifier for tracking completion
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH, utteranceId: String = "utt_${System.currentTimeMillis()}") {
        if (!isInitialized || tts == null) {
            Log.w(tag, "TTS not ready yet, dropping utterance: $text")
            return
        }

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        tts?.speak(text, queueMode, params, utteranceId)
    }

    /**
     * Immediately kill any active speech synthesis.
     */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(tag, "Error shutting down TTS: ${e.message}")
        }
    }
}
