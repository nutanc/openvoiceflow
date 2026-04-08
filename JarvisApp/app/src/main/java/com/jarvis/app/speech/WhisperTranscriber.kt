package com.jarvis.app.speech

import android.util.Log
import com.jarvis.app.config.AppConfig
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
 * Uses the same API key as the intent extractor.
 */
class WhisperTranscriber {

    companion object {
        private const val TAG = "JarvisWhisper"
        private const val WHISPER_URL = "https://api.openai.com/v1/audio/transcriptions"
        private const val WHISPER_MODEL = "whisper-1"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

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
