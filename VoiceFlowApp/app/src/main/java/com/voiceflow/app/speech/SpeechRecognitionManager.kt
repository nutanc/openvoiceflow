package com.voiceflow.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.voiceflow.app.config.AppConfig

/**
 * Wraps Android's SpeechRecognizer API.
 * Equivalent to recorder.py + transcriber.py from OpenVoiceFlow,
 * but uses the built-in Android speech recognition (Google STT).
 *
 * Supports delayed restart for press-and-hold mode — the recognizer
 * may auto-stop on silence, and we need to restart it cleanly.
 */
class SpeechRecognitionManager(private val context: Context) {

    companion object {
        private const val TAG = "SpeechRecognition"
        private const val RESTART_DELAY_MS = 300L
    }

    interface Listener {
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onError(errorMessage: String)
        fun onReadyForSpeech()
        fun onEndOfSpeech()
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: Listener? = null
    private val handler = Handler(Looper.getMainLooper())
    var isListening: Boolean = false
        private set

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun startListening() {
        if (isListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener?.onError("Speech recognition not available on this device")
            return
        }

        isListening = true

        // Create a fresh recognizer each time (more reliable than reusing)
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }

        val intent = createRecognizerIntent()

        try {
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Started listening (language: ${AppConfig.speechLanguage})")
        } catch (e: Exception) {
            isListening = false
            listener?.onError("Failed to start speech recognition: ${e.message}")
            Log.e(TAG, "Failed to start", e)
        }
    }

    /**
     * Restart recognizer after a delay. Used during press-and-hold when the
     * recognizer auto-stops (e.g. silence timeout or no-match error).
     * The delay prevents error 11 (restarting too fast).
     */
    fun restartAfterDelay() {
        isListening = false
        speechRecognizer?.destroy()
        speechRecognizer = null

        handler.postDelayed({
            startListening()
        }, RESTART_DELAY_MS)
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listener", e)
        }
    }

    fun destroy() {
        isListening = false
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, AppConfig.speechLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Long silence timeouts — we rely on user releasing the button,
            // but some devices ignore these, so we also handle auto-stop via restart
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 30000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 30000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
        }
    }

    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
            listener?.onReadyForSpeech()
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Can be used for visual feedback (volume level)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech")
            isListening = false
            listener?.onEndOfSpeech()
        }

        override fun onError(error: Int) {
            isListening = false
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Unknown error ($error)"
            }
            Log.e(TAG, "Recognition error: $message (code $error)")
            listener?.onError(message)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            Log.d(TAG, "Final result: $text")
            if (text.isNotBlank()) {
                listener?.onFinalResult(text)
            } else {
                listener?.onError("No speech detected")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotBlank()) {
                Log.d(TAG, "Partial result: $text")
                listener?.onPartialResult(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
