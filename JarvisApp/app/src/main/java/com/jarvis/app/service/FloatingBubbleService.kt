package com.jarvis.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.jarvis.app.JarvisApplication
import com.jarvis.app.R
import com.jarvis.app.config.AppConfig
import com.jarvis.app.core.JarvisOrchestrator
import com.jarvis.app.ui.MainActivity

/**
 * Foreground service that shows a floating bubble overlay for Jarvis.
 * Tap the bubble to start recording, tap again to stop and process.
 * The bubble can also be dragged around.
 *
 * Pipeline: Tap → Record → Tap → Whisper → GPT Intent → Launch Android Intent
 */
class FloatingBubbleService : Service() {

    companion object {
        private const val TAG = "JarvisBubble"
        private const val NOTIFICATION_ID = 1
        private const val BUBBLE_SIZE_DP = 56
        private const val MOVE_THRESHOLD_PX = 10

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: FrameLayout
    private lateinit var orchestrator: JarvisOrchestrator
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    private var iconView: ImageView? = null
    private var statusTextView: TextView? = null
    private var progressView: ProgressBar? = null
    private var bubbleBackground: GradientDrawable? = null

    // Tap-to-toggle state
    private var isRecordingActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        isRunning = true

        AppConfig.init(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Initialize the orchestrator
        orchestrator = JarvisOrchestrator(this)
        orchestrator.setStateListener(object : JarvisOrchestrator.StateListener {
            override fun onStateChanged(state: JarvisOrchestrator.State, message: String) {
                updateBubbleAppearance(state, message)
                // Reset toggle flag when pipeline finishes (or errors)
                if (state == JarvisOrchestrator.State.IDLE) {
                    isRecordingActive = false
                }
            }
        })

        // Create the floating bubble view
        createBubbleView()

        // Setup window layout params
        val bubbleSizePx = (BUBBLE_SIZE_DP * resources.displayMetrics.density).toInt()
        layoutParams = WindowManager.LayoutParams(
            bubbleSizePx,
            bubbleSizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        // Add bubble to window
        windowManager.addView(bubbleView, layoutParams)

        // Setup touch handling (tap-to-toggle + drag)
        setupTouchListener()

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        Log.d(TAG, "Jarvis bubble service started")
    }

    private fun createBubbleView() {
        val bubbleSizePx = (BUBBLE_SIZE_DP * resources.displayMetrics.density).toInt()

        bubbleView = FrameLayout(this).apply {
            // Circular gradient background
            bubbleBackground = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#6750A4")) // Primary purple
                setStroke(2, Color.parseColor("#FFFFFF"))
            }
            background = bubbleBackground
            elevation = 8f * resources.displayMetrics.density
        }

        // Star icon (Jarvis branding — distinct from VoiceFlow's mic icon)
        iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.btn_star_big_on)
            setColorFilter(Color.WHITE)
            val padding = (14 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val iconParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        bubbleView.addView(iconView, iconParams)

        // Processing spinner (hidden by default)
        progressView = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        val progressParams = FrameLayout.LayoutParams(
            (28 * resources.displayMetrics.density).toInt(),
            (28 * resources.displayMetrics.density).toInt()
        ).apply {
            gravity = Gravity.CENTER
        }
        bubbleView.addView(progressView, progressParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var hasMoved = false

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    hasMoved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > MOVE_THRESHOLD_PX || Math.abs(dy) > MOVE_THRESHOLD_PX) {
                        hasMoved = true
                    }
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    try {
                        windowManager.updateViewLayout(bubbleView, layoutParams)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating view layout", e)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!hasMoved) {
                        // This was a tap — toggle recording on/off
                        toggleRecording()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> true

                else -> false
            }
        }
    }

    /**
     * Toggle recording: tap once to start, tap again to stop and process.
     */
    private fun toggleRecording() {
        val currentState = orchestrator.getCurrentState()
        if (!isRecordingActive && currentState == JarvisOrchestrator.State.IDLE) {
            Log.d(TAG, "Tap detected — starting recording")
            isRecordingActive = true
            orchestrator.start()
        } else if (isRecordingActive && currentState == JarvisOrchestrator.State.LISTENING) {
            Log.d(TAG, "Tap detected — stopping recording and processing")
            isRecordingActive = false
            orchestrator.stop()
        }
    }

    /**
     * Update the bubble's visual appearance based on state.
     */
    private fun updateBubbleAppearance(state: JarvisOrchestrator.State, message: String) {
        try {
            when (state) {
                JarvisOrchestrator.State.IDLE -> {
                    bubbleBackground?.setColor(Color.parseColor("#6750A4")) // Purple
                    iconView?.setImageResource(android.R.drawable.btn_star_big_on)
                    iconView?.visibility = View.VISIBLE
                    iconView?.clearAnimation()
                    progressView?.visibility = View.GONE
                }

                JarvisOrchestrator.State.LISTENING -> {
                    bubbleBackground?.setColor(Color.parseColor("#EF4444")) // Red
                    iconView?.setImageResource(android.R.drawable.ic_btn_speak_now)
                    iconView?.visibility = View.VISIBLE
                    progressView?.visibility = View.GONE

                    // Pulsing animation
                    val pulseAnim = ScaleAnimation(
                        1.0f, 1.15f, 1.0f, 1.15f,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f
                    ).apply {
                        duration = 600
                        repeatCount = Animation.INFINITE
                        repeatMode = Animation.REVERSE
                    }
                    bubbleView.startAnimation(pulseAnim)
                }

                JarvisOrchestrator.State.PROCESSING -> {
                    bubbleBackground?.setColor(Color.parseColor("#FFA726")) // Orange
                    iconView?.visibility = View.GONE
                    progressView?.visibility = View.VISIBLE
                    bubbleView.clearAnimation()
                }

                JarvisOrchestrator.State.LAUNCHING -> {
                    bubbleBackground?.setColor(Color.parseColor("#4CAF50")) // Green
                    iconView?.setImageResource(android.R.drawable.ic_menu_send)
                    iconView?.visibility = View.VISIBLE
                    progressView?.visibility = View.GONE
                    bubbleView.clearAnimation()
                }

                JarvisOrchestrator.State.ERROR -> {
                    bubbleBackground?.setColor(Color.parseColor("#EF4444")) // Red
                    iconView?.setImageResource(android.R.drawable.ic_dialog_alert)
                    iconView?.visibility = View.VISIBLE
                    progressView?.visibility = View.GONE
                    bubbleView.clearAnimation()

                    // Brief flash animation for error
                    val flashAnim = AlphaAnimation(1.0f, 0.3f).apply {
                        duration = 200
                        repeatCount = 3
                        repeatMode = Animation.REVERSE
                    }
                    bubbleView.startAnimation(flashAnim)
                }
            }

            // Force redraw
            bubbleView.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating bubble appearance", e)
        }
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, JarvisApplication.CHANNEL_ID)
            .setContentTitle("Jarvis is active")
            .setContentText("Tap the ⭐ bubble to give a voice command")
            .setSmallIcon(android.R.drawable.btn_star_big_on)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        try {
            windowManager.removeView(bubbleView)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing bubble view", e)
        }
        orchestrator.destroy()
        Log.d(TAG, "Jarvis bubble service stopped")
        super.onDestroy()
    }
}
