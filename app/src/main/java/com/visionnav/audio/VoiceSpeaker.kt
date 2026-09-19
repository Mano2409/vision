package com.visionnav.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Enhanced High-Clarity, High-Volume Native Android TextToSpeech engine.
 *
 * Optimizations applied:
 * 1. USAGE_ASSISTANCE_NAVIGATION_GUIDANCE + FLAG_AUDIBILITY_ENFORCED: Forces loud, prioritized audio routing.
 * 2. Transient Audio Focus Ducking: Automatically lowers/mutes background music or apps while VisionNav speaks.
 * 3. HD Voice Selector: Queries installed TTS engine and picks the highest-quality non-network voice pack.
 * 4. Maximum Gain Parameter: Explicitly passes KEY_PARAM_VOLUME = 1.0f.
 * 5. Dynamic Minimum Volume Protection: Ensures the device audio stream is audible if the user left it low.
 * 6. Acoustic Pitch Calibration: 1.06x pitch and 1.0x rate for crisp consonant enunciation in urban noise.
 */
class VoiceSpeaker(
    private val context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private val tag = "VoiceSpeaker"
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    var onUtteranceDone: ((String) -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale.getDefault()
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(tag, "Default locale missing or unsupported, falling back to Locale.US")
                tts?.setLanguage(Locale.US)
            }

            // 1. Acoustic tuning for maximum outdoor clarity
            tts?.setSpeechRate(1.0f) // 1.0x provides crisp, distinct consonant articulation
            tts?.setPitch(1.06f)     // Slightly higher formant resonance pierces traffic rumble

            // 2. Select the highest quality HD voice installed on the device
            selectBestVoice(locale)

            // 3. Audio attributes: Navigation guidance with audibility enforcement
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            // 4. Prepare Audio Focus Request (Ducks background apps while speaking)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    releaseAudioFocus()
                    utteranceId?.let { onUtteranceDone?.invoke(it) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    releaseAudioFocus()
                    Log.e(tag, "TTS playback error on utterance: $utteranceId")
                }
            })

            isInitialized = true
            onInitComplete?.invoke(true)
            Log.i(tag, "VoiceSpeaker initialized with HD voice, max volume parameters, and audio ducking")
        } else {
            Log.e(tag, "TextToSpeech initialization failed with status $status")
            isInitialized = false
            onInitComplete?.invoke(false)
        }
    }

    /**
     * Inspects device voices and selects the highest quality local voice.
     */
    private fun selectBestVoice(targetLocale: Locale) {
        try {
            val availableVoices = tts?.voices
            if (!availableVoices.isNullOrEmpty()) {
                val matchingVoices = availableVoices.filter { voice ->
                    (voice.locale.language == targetLocale.language) && !voice.isNetworkConnectionRequired
                }

                val bestVoice = matchingVoices.maxByOrNull { voice ->
                    // Prefer QUALITY_VERY_HIGH (400) > QUALITY_HIGH (300) > QUALITY_NORMAL (200)
                    voice.quality
                }

                if (bestVoice != null) {
                    tts?.voice = bestVoice
                    Log.i(tag, "Selected High-Definition Voice: ${bestVoice.name} (Quality=${bestVoice.quality})")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not customize voice model: ${e.message}")
        }
    }

    /**
     * Speak text using Android TTS engine with maximum volume gain and audio focus.
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH, utteranceId: String = "utt_${System.currentTimeMillis()}") {
        if (!isInitialized || tts == null) {
            Log.w(tag, "TTS not ready yet, dropping utterance: $text")
            return
        }

        // Ensure device volume is audible (minimum 75% for assistive navigation)
        boostStreamVolumeIfNeeded()

        // Acquire transient audio focus to duck/silence other audio on phone
        requestAudioFocus()

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            // Maximum synthesizer output gain (1.0f = 100% full scale volume)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        tts?.speak(text, queueMode, params, utteranceId)
    }

    private fun boostStreamVolumeIfNeeded() {
        try {
            audioManager?.let { am ->
                val stream = AudioManager.STREAM_MUSIC
                val currentVol = am.getStreamVolume(stream)
                val maxVol = am.getStreamMaxVolume(stream)
                val minThreshold = (maxVol * 0.75).toInt()

                if (currentVol < minThreshold) {
                    am.setStreamVolume(stream, minThreshold, 0)
                    Log.i(tag, "Boosted stream volume to $minThreshold / $maxVol for safety audibility")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Unable to adjust stream volume: ${e.message}")
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Audio focus request failed: ${e.message}")
        }
    }

    private fun releaseAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(tag, "Audio focus release failed: ${e.message}")
        }
    }

    /**
     * Immediately kill any active speech synthesis and release audio focus.
     */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        releaseAudioFocus()
    }

    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(tag, "Error shutting down TTS: ${e.message}")
        }
    }
}
