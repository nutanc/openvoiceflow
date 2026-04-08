package com.jarvis.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import com.jarvis.app.config.AppConfig
import com.jarvis.app.llm.IntentLauncher
import com.jarvis.app.llm.OpenAiIntentExtractor
import com.jarvis.app.speech.AudioRecorder
import com.jarvis.app.speech.WhisperTranscriber
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Orchestrates the full Jarvis pipeline:
 *   Tap → Record Audio → Tap → Whisper → GPT (intent extraction) → Launch Intent
 *
 * Pipeline: Record → Whisper Transcribe → GPT Extract Intent → Android Intent Launch
 */
class JarvisOrchestrator(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "JarvisOrchestrator"
    }

    enum class State {
        IDLE,
        LISTENING,
        PROCESSING,
        LAUNCHING,
        ERROR
    }

    interface StateListener {
        fun onStateChanged(state: State, message: String = "")
    }

    private val audioRecorder = AudioRecorder(context)
    private val whisperTranscriber = WhisperTranscriber()
    private val intentExtractor = OpenAiIntentExtractor()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ttsInitialized = false

    private var stateListener: StateListener? = null
    private var currentState = State.IDLE

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsInitialized = true
        } else {
            Log.e(TAG, "TextToSpeech initialization failed")
        }
    }

    val isApiConfigured: Boolean get() = intentExtractor.isConfigured()

    fun setStateListener(listener: StateListener) {
        this.stateListener = listener
    }

    /**
     * Start recording audio.
     * Called when the user taps the floating bubble (first tap).
     */
    fun start() {
        if (currentState != State.IDLE) {
            Log.w(TAG, "Cannot start: current state is $currentState")
            return
        }

        val started = audioRecorder.start()
        if (started) {
            updateState(State.LISTENING, "Recording...")
            showToast("🎤 Listening...")
            Log.d(TAG, "Audio recording started")
        } else {
            updateState(State.ERROR, "Could not start recording")
            showToast("Failed to start audio recording")
            updateState(State.IDLE)
        }
    }

    /**
     * Stop recording and process the audio.
     * Called when the user taps the floating bubble again (second tap).
     */
    fun stop() {
        if (currentState != State.LISTENING) return

        updateState(State.PROCESSING, "Transcribing...")
        Log.d(TAG, "Stopping recording and processing")

        val wavBytes = audioRecorder.stop()

        if (wavBytes == null || wavBytes.size < 1000) {
            Log.w(TAG, "No audio recorded (or too short)")
            showToast("No audio recorded — hold longer")
            updateState(State.IDLE)
            return
        }

        // Transcribe with Whisper, then extract intent with GPT, then launch
        processAudio(wavBytes)
    }

    /**
     * Full pipeline: Whisper transcription → GPT intent extraction → Intent launch.
     */
    private fun processAudio(wavBytes: ByteArray) {
        scope.launch {
            try {
                // Step 1: Transcribe with Whisper
                Log.d(TAG, "Sending audio to Whisper API (${wavBytes.size} bytes)...")
                val rawText = whisperTranscriber.transcribe(wavBytes)

                if (rawText.isNullOrBlank()) {
                    mainHandler.post {
                        showToast("❌ Could not transcribe audio")
                        updateState(State.IDLE)
                    }
                    return@launch
                }

                Log.d(TAG, "Whisper result: '$rawText'")
                mainHandler.post {
                    showToast("🎤 Transcription: \"$rawText\"")
                }

                // Step 2: Extract intent with GPT
                mainHandler.post { updateState(State.PROCESSING, "Extracting intent...") }

                val resultJson = intentExtractor.extractIntent(rawText)

                if (resultJson == null) {
                    mainHandler.post {
                        showToast("❌ Could not understand that command")
                        updateState(State.ERROR, "Intent extraction failed")
                        updateState(State.IDLE)
                    }
                    return@launch
                }

                Log.d(TAG, "Intent JSON: $resultJson")

                // Show extracted intent in toast
                val action = resultJson.optString("action", "")
                val intentsArray = resultJson.optJSONArray("intents")
                mainHandler.post {
                    if (intentsArray != null && intentsArray.length() > 0) {
                        showToast("🧠 Extracted ${intentsArray.length()} intents")
                    } else if (action.isNotBlank() && action != "null") {
                        val shortAction = action.substringAfterLast(".")
                        showToast("🧠 Intent: $shortAction")
                    } else {
                        showToast("💬 Conversational response")
                    }
                }

                // Small delay so user can see the intent toast
                delay(800)

                // Step 3: Launch intent(s)
                mainHandler.post {
                    updateState(State.LAUNCHING, "Launching...")
                    val responseText = IntentLauncher.launch(resultJson, context)
                    showToast(responseText)
                    speakResponse(responseText)
                    
                    // Pipeline done
                    mainHandler.postDelayed({ updateState(State.IDLE) }, 1500)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                mainHandler.post {
                    updateState(State.ERROR, e.message ?: "Unknown error")
                    val errorMsg = "❌ Error: ${e.message}"
                    showToast(errorMsg)
                    speakResponse("Error occurred")
                    mainHandler.postDelayed({ updateState(State.IDLE) }, 1000)
                }
            }
        }
    }

    private fun updateState(state: State, message: String = "") {
        currentState = state
        stateListener?.onStateChanged(state, message)
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Could not show toast", e)
        }
    }

    private fun speakResponse(message: String) {
        if (!ttsInitialized) return

        try {
            // Configure TTS language based on current settings
            val languageCode = AppConfig.speechLanguage
            val parts = languageCode.split("-")
            val locale = if (parts.size == 2) {
                Locale(parts[0], parts[1])
            } else {
                Locale(languageCode)
            }

            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS Language is not supported: ${locale.language}")
            }

            // Remove emojis and leading symbols for clean speech (e.g. from "💬 Hello" -> "Hello")
            val cleanText = message.replace(Regex("^[^a-zA-Z0-9]+"), "").trim()
            
            Log.d(TAG, "Speaking: '$cleanText'")
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "TextToSpeech error", e)
        }
    }

    fun getCurrentState(): State = currentState

    fun destroy() {
        audioRecorder.destroy()
        scope.cancel()
        tts?.stop()
        tts?.shutdown()
    }
}
