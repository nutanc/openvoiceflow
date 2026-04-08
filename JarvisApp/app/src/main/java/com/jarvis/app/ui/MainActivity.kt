package com.jarvis.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.app.config.AppConfig
import com.jarvis.app.databinding.ActivityMainBinding
import com.jarvis.app.service.FloatingBubbleService

/**
 * Main landing screen — enter API key, start/stop Jarvis, manage permissions.
 * Accessibility service is no longer required (Jarvis launches intents, not pastes text).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updatePermissionStatuses()
        if (!granted) {
            Toast.makeText(this, "Microphone permission is required for voice input", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            toggleService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppConfig.init(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        updateApiKeyStatus()
        updatePermissionStatuses()
        updateServiceStatus()

        // Pre-fill API key if saved
        val savedKey = AppConfig.openAiApiKey
        if (savedKey.isNotBlank()) {
            binding.etApiKey.setText(savedKey)
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        updateServiceStatus()
        updateApiKeyStatus()
    }

    private fun setupListeners() {
        binding.btnSaveApiKey.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim() ?: ""
            if (key.isBlank()) {
                Toast.makeText(this, "Please enter an API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppConfig.openAiApiKey = key
            updateApiKeyStatus()
            Toast.makeText(this, "API key saved ✓", Toast.LENGTH_SHORT).show()
        }

        binding.btnToggleService.setOnClickListener {
            toggleService()
        }

        binding.btnOverlayPermission.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        binding.btnMicPermission.setOnClickListener {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun toggleService() {
        if (FloatingBubbleService.isRunning) {
            FloatingBubbleService.stop(this)
            updateServiceStatus()
        } else {
            // Check prerequisites
            if (AppConfig.openAiApiKey.isBlank()) {
                Toast.makeText(this, "Please set your OpenAI API key first", Toast.LENGTH_SHORT).show()
                return
            }

            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
                return
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Please grant microphone permission first", Toast.LENGTH_SHORT).show()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }

            FloatingBubbleService.start(this)
            updateServiceStatus()
            moveTaskToBack(true)
        }
    }

    private fun updateApiKeyStatus() {
        val hasKey = AppConfig.openAiApiKey.isNotBlank()
        binding.tvApiKeyStatus.text = if (hasKey) {
            "✅ API key configured (${AppConfig.openAiModel})"
        } else {
            "⬜ Enter your OpenAI API key to enable Jarvis"
        }
    }

    private fun updateServiceStatus() {
        if (FloatingBubbleService.isRunning) {
            binding.tvServiceStatus.text = "Jarvis is running ✓"
            binding.btnToggleService.text = "Stop Jarvis"
        } else {
            binding.tvServiceStatus.text = "Jarvis is stopped"
            binding.btnToggleService.text = "Start Jarvis"
        }
    }

    private fun updatePermissionStatuses() {
        val hasOverlay = Settings.canDrawOverlays(this)
        binding.tvOverlayStatus.text = if (hasOverlay) "✅ Overlay Permission" else "⬜ Overlay Permission"
        binding.btnOverlayPermission.isEnabled = !hasOverlay

        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        binding.tvMicStatus.text = if (hasMic) "✅ Microphone Permission" else "⬜ Microphone Permission"
        binding.btnMicPermission.isEnabled = !hasMic
    }
}
