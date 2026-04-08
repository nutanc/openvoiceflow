package com.jarvis.app.llm

import android.util.Log
import com.jarvis.app.config.AppConfig
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
 * Sends transcribed text to OpenAI GPT to extract structured Android intents.
 *
 * GPT returns JSON with:
 * - action: Android intent action (e.g. "android.intent.action.SENDTO") or null
 * - data: URI string or null
 * - extras: map of intent extras or null
 * - type: MIME type or null
 * - response: human-readable confirmation text
 *
 * Or for multiple intents:
 * - intents: array of intent objects
 * - response: overall confirmation
 */
class OpenAiIntentExtractor {

    companion object {
        private const val TAG = "JarvisIntentExtractor"
        private const val TEMPERATURE = 0.1
        private const val MAX_TOKENS = 500
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
     * Extract Android intent(s) from the user's spoken command.
     *
     * @param transcript The transcribed speech
     * @return Parsed JSON object with intent details, or null on failure
     */
    suspend fun extractIntent(transcript: String): JSONObject? = withContext(Dispatchers.IO) {
        if (transcript.isBlank()) return@withContext null

        val apiKey = AppConfig.openAiApiKey
        if (apiKey.isBlank()) {
            Log.e(TAG, "No OpenAI API key")
            return@withContext null
        }

        try {
            Log.d(TAG, "Extracting intent from: '$transcript'")

            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", AppConfig.systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", transcript)
                })
            }

            val requestBody = JSONObject().apply {
                put("model", AppConfig.openAiModel)
                put("messages", messagesArray)
                put("temperature", TEMPERATURE)
                put("max_tokens", MAX_TOKENS)
            }

            val request = Request.Builder()
                .url(AppConfig.openAiApiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "GPT API error: ${response.code} $errorBody")
                return@withContext null
            }

            val responseBody = response.body?.string() ?: return@withContext null
            val json = JSONObject(responseBody)
            val content = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            Log.d(TAG, "GPT response: $content")

            // Parse the JSON response (strip markdown fences if present)
            val cleanContent = content
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            JSONObject(cleanContent)
        } catch (e: Exception) {
            Log.e(TAG, "Intent extraction failed", e)
            null
        }
    }
}
