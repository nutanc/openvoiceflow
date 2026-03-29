package com.voiceflow.app.llm

import android.util.Log
import com.voiceflow.app.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * LLM Corrector using the OpenAI Chat Completions API.
 * Mirrors llm_corrector.py from OpenVoiceFlow.
 *
 * Uses the configured API key and model to send transcription
 * corrections through GPT-4o-mini (or whichever model is configured).
 */
class OpenAiLlmCorrector {

    companion object {
        private const val TAG = "OpenAiLlmCorrector"
        private const val DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val DEFAULT_MODEL = "gpt-4o-mini"
        private const val TEMPERATURE = 0.3
        private const val MAX_TOKENS = 1024
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Check if the API key is configured.
     */
    fun isConfigured(): Boolean {
        return AppConfig.openAiApiKey.isNotBlank()
    }

    /**
     * Correct transcription using the OpenAI API.
     *
     * @param rawText    Raw transcription from Android speech recognizer
     * @param appContext Name/title of the app where text will be pasted
     * @return Corrected text
     */
    suspend fun correct(rawText: String, appContext: String): String = withContext(Dispatchers.IO) {
        if (rawText.isBlank()) return@withContext ""

        val apiKey = AppConfig.openAiApiKey
        if (apiKey.isBlank()) {
            Log.w(TAG, "API key not set, returning raw text")
            return@withContext rawText
        }

        try {
            Log.d(TAG, "Correcting with OpenAI (context: $appContext)...")

            val systemPrompt = AppConfig.getFormattedPrompt(appContext)
            val model = AppConfig.openAiModel
            val apiUrl = AppConfig.openAiApiUrl

            // Build the request JSON
            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", rawText)
                })
            }

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("temperature", TEMPERATURE)
                put("max_tokens", MAX_TOKENS)
            }

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "OpenAI API error: ${response.code} $errorBody")
                return@withContext rawText
            }

            val responseBody = response.body?.string() ?: return@withContext rawText
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")

            if (choices.length() > 0) {
                val corrected = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()

                Log.d(TAG, "Raw: '$rawText' → Corrected: '$corrected'")
                corrected.ifBlank { rawText }
            } else {
                Log.w(TAG, "No choices in response")
                rawText
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI correction failed, returning raw text", e)
            rawText
        }
    }
}
