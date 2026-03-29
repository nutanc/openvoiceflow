package com.voiceflow.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.voiceflow.app.llm.OpenAiLlmCorrector
import com.voiceflow.app.service.TextPasteAccessibilityService
import com.voiceflow.app.speech.AudioRecorder
import com.voiceflow.app.speech.WhisperTranscriber
import kotlinx.coroutines.*

/**
 * Orchestrates the full VoiceFlow pipeline:
 *   Press & Hold → Record Audio → Release → Whisper → GPT Correct → Paste
 *
 * Equivalent to the process() function in main.py from OpenVoiceFlow.
 */
class VoiceFlowOrchestrator(private val context: Context) {

    companion object {
        private const val TAG = "VoiceFlowOrchestrator"
    }

    enum class State {
        IDLE,
        LISTENING,
        PROCESSING,
        PASTING,
        ERROR
    }

    interface StateListener {
        fun onStateChanged(state: State, message: String = "")
    }

    private val audioRecorder = AudioRecorder(context)
    private val whisperTranscriber = WhisperTranscriber()
    private val llmCorrector = OpenAiLlmCorrector()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var stateListener: StateListener? = null
    private var currentState = State.IDLE

    val isApiConfigured: Boolean get() = llmCorrector.isConfigured()

    fun setStateListener(listener: StateListener) {
        this.stateListener = listener
    }

    /**
     * Start recording audio.
     * Called when the user presses down on the floating bubble.
     */
    fun start() {
        if (currentState != State.IDLE) {
            Log.w(TAG, "Cannot start: current state is $currentState")
            return
        }

        // Check if accessibility service is active
        if (!TextPasteAccessibilityService.isServiceActive()) {
            updateState(State.ERROR, "Accessibility service not enabled")
            showToast("Please enable VoiceFlow Accessibility Service in Settings")
            updateState(State.IDLE)
            return
        }

        val started = audioRecorder.start()
        if (started) {
            updateState(State.LISTENING, "Recording...")
            Log.d(TAG, "Audio recording started")
        } else {
            updateState(State.ERROR, "Could not start recording")
            showToast("Failed to start audio recording")
            updateState(State.IDLE)
        }
    }

    /**
     * Stop recording and process the audio.
     * Called when the user releases the floating bubble.
     */
    fun stop() {
        if (currentState != State.LISTENING) return

        updateState(State.PROCESSING, "Transcribing...")
        Log.d(TAG, "Stopping recording and processing")

        val wavBytes = audioRecorder.stop()

        if (wavBytes == null || wavBytes.size < 1000) {
            // Too short or no audio
            Log.w(TAG, "No audio recorded (or too short)")
            showToast("No audio recorded — hold longer")
            updateState(State.IDLE)
            return
        }

        // Transcribe with Whisper, then correct with GPT, then paste
        processAudio(wavBytes)
    }

    /**
     * Full pipeline: Whisper transcription → GPT correction → Paste.
     */
    private fun processAudio(wavBytes: ByteArray) {
        scope.launch {
            try {
                // Step 1: Transcribe with Whisper
                Log.d(TAG, "Sending audio to Whisper API (${wavBytes.size} bytes)...")
                val rawText = whisperTranscriber.transcribe(wavBytes)

                if (rawText.isNullOrBlank()) {
                    mainHandler.post {
                        showToast("Could not transcribe audio")
                        updateState(State.IDLE)
                    }
                    return@launch
                }

                Log.d(TAG, "Whisper result: '$rawText'")

                // Step 2: Correct with GPT
                mainHandler.post { updateState(State.PROCESSING, "Correcting with AI...") }

                val appContext = TextPasteAccessibilityService.instance?.getAppContext()
                    ?: "Unknown Application"

                Log.d(TAG, "App context: $appContext")

                val correctedText = if (llmCorrector.isConfigured()) {
                    llmCorrector.correct(rawText, appContext)
                } else {
                    Log.w(TAG, "API key not set, using raw text")
                    rawText
                }

                Log.d(TAG, "Corrected text: '$correctedText'")

                // Step 3: Paste
                mainHandler.post {
                    updateState(State.PASTING, "Pasting...")

                    val success = TextPasteAccessibilityService.instance?.pasteText(correctedText)
                        ?: false

                    if (success) {
                        showToast("✓ Text pasted")
                    } else {
                        showToast("Could not paste — no text field focused")
                    }

                    updateState(State.IDLE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                mainHandler.post {
                    updateState(State.ERROR, e.message ?: "Unknown error")
                    showToast("Error: ${e.message}")
                    updateState(State.IDLE)
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

    fun getCurrentState(): State = currentState

    fun destroy() {
        audioRecorder.destroy()
        scope.cancel()
    }
}
