package com.jarvis.app.llm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import org.json.JSONObject

/**
 * Parses structured intent JSON from OpenAI GPT and launches
 * the corresponding Android intent(s).
 *
 * Supports both single intents and sequential intents (JSON array).
 */
object IntentLauncher {

    private const val TAG = "JarvisIntentLauncher"
    private const val SEQUENTIAL_DELAY_MS = 1500L

    /**
     * Parse the GPT response JSON and launch the Android intent(s).
     *
     * Handles two formats:
     * 1. Single intent: {"action": "...", "data": "...", ...}
     * 2. Sequential: {"intents": [{...}, {...}], "response": "..."}
     *
     * @param intentJson JSON with intent details
     * @param context Android context to launch from
     * @return The "response" text, or an error message
     */
    fun launch(intentJson: JSONObject, context: Context): String {
        // Check for sequential intents
        val intentsArray = intentJson.optJSONArray("intents")
        if (intentsArray != null && intentsArray.length() > 0) {
            return launchSequential(intentsArray, intentJson.optString("response", "Done"), context)
        }

        // Single intent
        return launchSingle(intentJson, context)
    }

    /**
     * Launch a single Android intent from the JSON.
     */
    private fun launchSingle(intentJson: JSONObject, context: Context): String {
        val action = intentJson.optString("action", "").ifBlank { null }
        val responseText = intentJson.optString("response", "Done")

        // No action = conversational response only
        if (action == null || action == "null") {
            Log.d(TAG, "No intent action, conversational response: $responseText")
            return "💬 $responseText"
        }

        try {
            val intent = Intent(action)

            // Data URI
            val data = intentJson.optString("data", "").ifBlank { null }
            if (data != null && data != "null") {
                intent.data = Uri.parse(data)
            }

            // MIME type
            val type = intentJson.optString("type", "").ifBlank { null }
            if (type != null && type != "null") {
                if (data != null && data != "null") {
                    intent.setDataAndType(Uri.parse(data), type)
                } else {
                    intent.type = type
                }
            }

            // Package
            val pkg = intentJson.optString("package", "").ifBlank { null }
            if (pkg != null && pkg != "null") {
                intent.setPackage(pkg)
            }

            // Category
            val category = intentJson.optString("category", "").ifBlank { null }
            if (category != null && category != "null") {
                intent.addCategory(category)
            } else if (action == "android.intent.action.SET_ALARM" || 
                       action == "android.intent.action.SET_TIMER" || 
                       action == "android.intent.action.SHOW_ALARMS") {
                // Ensure implicit resolution works for clock apps
                intent.addCategory(Intent.CATEGORY_DEFAULT)
            }

            // Extras
            val extras = intentJson.optJSONObject("extras")
            if (extras != null) {
                val keys = extras.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = extras.get(key)

                    when (value) {
                        is String -> intent.putExtra(key, value)
                        is Int -> intent.putExtra(key, value)
                        is Long -> intent.putExtra(key, value)
                        is Double -> intent.putExtra(key, value.toInt()) // JSON numbers
                        is Boolean -> intent.putExtra(key, value)
                        else -> intent.putExtra(key, value.toString())
                    }
                }
            }

            // Always add FLAG_ACTIVITY_NEW_TASK since we're launching from a service
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Resolve target app name for toast
            val appName = resolveAppName(action, data ?: "", intentJson, context)

            // Show toast: launching app
            showToast(context, "🚀 Launching: $appName")

            // Check if any app can handle this intent
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.d(TAG, "Launched intent: $action → $appName")
            } else {
                // Try launching anyway — some intents work without resolveActivity
                try {
                    context.startActivity(intent)
                    Log.d(TAG, "Force-launched intent: $action → $appName")
                } catch (e: Exception) {
                    Log.e(TAG, "No app found for action: $action", e)
                    return "❌ No app found to handle: $responseText"
                }
            }

            return "✅ $responseText"
        } catch (e: Exception) {
            Log.e(TAG, "Error launching intent", e)
            return "❌ Error: ${e.message}"
        }
    }

    /**
     * Launch multiple intents sequentially with a delay between them.
     */
    private fun launchSequential(
        intentsArray: org.json.JSONArray,
        overallResponse: String,
        context: Context
    ): String {
        val handler = Handler(Looper.getMainLooper())
        val results = mutableListOf<String>()

        for (i in 0 until intentsArray.length()) {
            val intentJson = intentsArray.getJSONObject(i)
            handler.postDelayed({
                val result = launchSingle(intentJson, context)
                results.add(result)
                Log.d(TAG, "Sequential intent $i result: $result")
            }, i * SEQUENTIAL_DELAY_MS)
        }

        return "✅ $overallResponse (${intentsArray.length()} actions)"
    }

    /**
     * Resolve a user-friendly app name from the intent JSON for display in Toasts.
     */
    private fun resolveAppName(action: String, data: String, intentJson: JSONObject, context: Context): String {
        // First try top-level package, fallback to extras (legacy)
        var pkg = intentJson.optString("package", "")
        if (pkg.isBlank() || pkg == "null") {
            val extras = intentJson.optJSONObject("extras")
            pkg = extras?.optString("package", "") ?: ""
        }

        // Known package → app name
        if (pkg.isNotBlank()) {
            return try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                pkg.substringAfterLast(".")
            }
        }

        // Infer from data URI scheme / action
        return when {
            data.startsWith("tel:") -> "Phone Dialer"
            data.startsWith("smsto:") || data.startsWith("sms:") -> "Messages"
            data.startsWith("mailto:") -> "Email"
            data.startsWith("geo:") || data.startsWith("google.navigation:") -> "Maps"
            action.contains("SET_ALARM") || action.contains("SHOW_ALARMS") -> "Clock/Alarm"
            action.contains("SET_TIMER") -> "Clock/Timer"
            action == Intent.ACTION_INSERT && data.contains("events") -> "Calendar"
            action.contains("IMAGE_CAPTURE") -> "Camera"
            action.contains("SETTINGS") -> "Settings"
            action.contains("WEB_SEARCH") -> "Web Search"
            data.contains("youtube.com") -> "YouTube"
            data.contains("spotify") -> "Spotify"
            data.startsWith("https://") || data.startsWith("http://") -> "Browser"
            else -> "App"
        }
    }

    private fun showToast(context: Context, message: String) {
        try {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not show toast", e)
        }
    }
}
