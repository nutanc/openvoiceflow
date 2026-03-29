package com.voiceflow.app.config

import android.content.Context
import android.content.SharedPreferences

/**
 * App configuration manager using SharedPreferences.
 * Mirrors config.py from OpenVoiceFlow.
 */
object AppConfig {

    private const val PREFS_NAME = "voiceflow_prefs"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"
    private const val KEY_LANGUAGE = "speech_language"
    private const val KEY_OPENAI_API_KEY = "openai_api_key"
    private const val KEY_OPENAI_MODEL = "openai_model"
    private const val KEY_OPENAI_API_URL = "openai_api_url"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
        set(value) = prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()

    var speechLanguage: String
        get() = prefs.getString(KEY_LANGUAGE, "en-US") ?: "en-US"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var openAiApiKey: String
        get() = prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENAI_API_KEY, value).apply()

    var openAiModel: String
        get() = prefs.getString(KEY_OPENAI_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_OPENAI_MODEL, value).apply()

    var openAiApiUrl: String
        get() = prefs.getString(KEY_OPENAI_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL
        set(value) = prefs.edit().putString(KEY_OPENAI_API_URL, value).apply()

    fun getFormattedPrompt(appContext: String): String {
        return systemPrompt.replace("{window_title}", appContext)
    }

    /**
     * Available languages for speech recognition.
     */
    val AVAILABLE_LANGUAGES = mapOf(
        "en-US" to "English (US)",
        "en-GB" to "English (UK)",
        "en-IN" to "English (India)",
        "es-ES" to "Spanish",
        "fr-FR" to "French",
        "de-DE" to "German",
        "it-IT" to "Italian",
        "pt-BR" to "Portuguese (Brazil)",
        "ja-JP" to "Japanese",
        "ko-KR" to "Korean",
        "zh-CN" to "Chinese (Simplified)",
        "hi-IN" to "Hindi"
    )

    const val DEFAULT_MODEL = "gpt-4o-mini"
    const val DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions"

    /**
     * Default system prompt — from OpenVoiceFlow config.py.
     */
    const val DEFAULT_SYSTEM_PROMPT = """You are a transcription correction assistant.
The user dictated text using voice-to-text while working in: "{window_title}".

Your job:
1. Fix speech-to-text errors (misheard words, grammar, punctuation)
2. Remove filler words: "uh", "um", "like" (when filler), "you know", "I mean"
3. Remove false starts and repetitions: "I want to — I need to" → "I need to"
4. Handle self-corrections:
   - "scratch that" → remove previous clause
   - "no no" / "I meant" → use the correction that follows
   - "actually" (as correction) → use what follows
   - "start over" → remove everything before
5. Add proper punctuation and capitalization
6. "period" → .  "comma" → ,  "question mark" → ?  "new line" → line break

Context-aware formatting:
- Chat/messaging apps: casual tone, short sentences
- Email apps: proper sentences, paragraphs
- Code editors: interpret as code when clearly intended
- Search bars: concise keywords, no punctuation

Rules:
- Return ONLY the corrected text, nothing else
- Don't add content the user didn't say
- Preserve the user's vocabulary and style
- When uncertain, preserve the original"""

}
