package com.voiceflow.app.speech

import android.util.Log
import com.voiceflow.app.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sends recorded audio to the OpenAI Whisper API for transcription.
 * Equivalent to transcriber.py from OpenVoiceFlow.
 *
 * Uses the same API key as the LLM corrector.
 */
class WhisperTranscriber {

    companion object {
        private const val TAG = "WhisperTranscriber"
        private const val WHISPER_URL = "https://api.openai.com/v1/audio/transcriptions"
        private const val WHISPER_MODEL = "whisper-1"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)  // Whisper can take a while for long audio
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Transcribe WAV audio bytes using OpenAI Whisper API.
     *
     * @param wavBytes WAV file content
     * @return Transcribed text, or null on failure
     */
    suspend fun transcribe(wavBytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val apiKey = AppConfig.openAiApiKey
        if (apiKey.isBlank()) {
            Log.e(TAG, "No API key set")
            return@withContext null
        }

        try {
            Log.d(TAG, "Sending ${wavBytes.size} bytes to Whisper API...")

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "recording.wav",
                    wavBytes.toRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("model", WHISPER_MODEL)
                .addFormDataPart("response_format", "json")
                .addFormDataPart("language", AppConfig.speechLanguage.substringBefore("-"))
                .build()

            val request = Request.Builder()
                .url(WHISPER_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Whisper API error: ${response.code} $errorBody")
                return@withContext null
            }

            val responseBody = response.body?.string() ?: return@withContext null
            val json = JSONObject(responseBody)
            val text = json.getString("text").trim()

            Log.d(TAG, "Whisper transcription: '$text'")
            text.ifBlank { null }
        } catch (e: Exception) {
            Log.e(TAG, "Whisper transcription failed", e)
            null
        }
    }
}
