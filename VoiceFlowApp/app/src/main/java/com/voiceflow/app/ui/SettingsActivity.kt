package com.voiceflow.app.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.voiceflow.app.config.AppConfig
import com.voiceflow.app.databinding.ActivitySettingsBinding

/**
 * Settings screen — configure OpenAI model/URL, edit system prompt, select language.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppConfig.init(this)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupOpenAiConfig()
        setupSystemPrompt()
        setupLanguageSelector()
        setupListeners()
    }

    private fun setupOpenAiConfig() {
        binding.etModel.setText(AppConfig.openAiModel)
        binding.etApiUrl.setText(AppConfig.openAiApiUrl)
    }

    private fun setupSystemPrompt() {
        binding.etSystemPrompt.setText(AppConfig.systemPrompt)
    }

    private fun setupLanguageSelector() {
        val languages = AppConfig.AVAILABLE_LANGUAGES
        val displayNames = languages.values.toList()
        val languageCodes = languages.keys.toList()

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayNames)
        binding.actvLanguage.setAdapter(adapter)

        // Set current selection
        val currentIndex = languageCodes.indexOf(AppConfig.speechLanguage)
        if (currentIndex >= 0) {
            binding.actvLanguage.setText(displayNames[currentIndex], false)
        }

        binding.actvLanguage.setOnItemClickListener { _, _, position, _ ->
            AppConfig.speechLanguage = languageCodes[position]
        }
    }

    private fun setupListeners() {
        binding.btnResetPrompt.setOnClickListener {
            binding.etSystemPrompt.setText(AppConfig.DEFAULT_SYSTEM_PROMPT)
            Toast.makeText(this, "Prompt reset to default", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        // Save OpenAI config
        val model = binding.etModel.text?.toString()?.trim() ?: AppConfig.DEFAULT_MODEL
        val apiUrl = binding.etApiUrl.text?.toString()?.trim() ?: AppConfig.DEFAULT_API_URL
        AppConfig.openAiModel = model.ifBlank { AppConfig.DEFAULT_MODEL }
        AppConfig.openAiApiUrl = apiUrl.ifBlank { AppConfig.DEFAULT_API_URL }

        // Save system prompt
        val prompt = binding.etSystemPrompt.text?.toString() ?: AppConfig.DEFAULT_SYSTEM_PROMPT
        AppConfig.systemPrompt = prompt

        Toast.makeText(this, "Settings saved ✓", Toast.LENGTH_SHORT).show()
        finish()
    }
}
