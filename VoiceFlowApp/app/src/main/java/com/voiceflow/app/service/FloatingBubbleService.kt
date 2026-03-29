package com.voiceflow.app.service

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
import com.voiceflow.app.R
import com.voiceflow.app.VoiceFlowApplication
import com.voiceflow.app.config.AppConfig
import com.voiceflow.app.core.VoiceFlowOrchestrator
import com.voiceflow.app.ui.MainActivity

/**
 * Foreground service that shows a floating bubble overlay.
 * Press and hold the bubble to record, release to process.
 * The bubble can also be dragged around.
 *
 * This is the Android equivalent of the hotkey listener in main.py from OpenVoiceFlow.
 */
class FloatingBubbleService : Service() {

    companion object {
        private const val TAG = "FloatingBubble"
        private const val NOTIFICATION_ID = 1
        private const val BUBBLE_SIZE_DP = 56
        private const val HOLD_DELAY_MS = 150L  // Brief hold before starting recording (to distinguish from drag)
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
    private lateinit var orchestrator: VoiceFlowOrchestrator
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    private var iconView: ImageView? = null
    private var statusTextView: TextView? = null
    private var progressView: ProgressBar? = null
    private var bubbleBackground: GradientDrawable? = null

    // Press-and-hold state
    private var isHolding = false
    private var holdStartedRecording = false
    private var startRecordingRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        isRunning = true

        AppConfig.init(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Initialize the orchestrator
        orchestrator = VoiceFlowOrchestrator(this)
        orchestrator.setStateListener(object : VoiceFlowOrchestrator.StateListener {
            override fun onStateChanged(state: VoiceFlowOrchestrator.State, message: String) {
                updateBubbleAppearance(state, message)
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

        // Setup touch handling (press-and-hold + drag)
        setupTouchListener()

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        Log.d(TAG, "Floating bubble service started")
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

        // Mic icon
        iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(Color.WHITE)
            val padding = (12 * resources.displayMetrics.density).toInt()
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
                    isHolding = true
                    holdStartedRecording = false

                    // Schedule recording start after a brief delay
                    // (so quick drag gestures don't trigger recording)
                    if (orchestrator.getCurrentState() == VoiceFlowOrchestrator.State.IDLE) {
                        startRecordingRunnable = Runnable {
                            if (isHolding && !hasMoved) {
                                Log.d(TAG, "Hold detected — starting recording")
                                holdStartedRecording = true
                                orchestrator.start()
                            }
                        }
                        handler.postDelayed(startRecordingRunnable!!, HOLD_DELAY_MS)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > MOVE_THRESHOLD_PX || Math.abs(dy) > MOVE_THRESHOLD_PX) {
                        if (!hasMoved) {
                            hasMoved = true
                            // Cancel pending recording if finger moved (it's a drag)
                            cancelPendingRecording()
                        }
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

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isHolding = false
                    cancelPendingRecording()

                    // If we were recording from the hold, stop and process on release
                    if (holdStartedRecording &&
                        orchestrator.getCurrentState() == VoiceFlowOrchestrator.State.LISTENING) {
                        Log.d(TAG, "Released — stopping recording and processing")
                        orchestrator.stop()
                    }
                    holdStartedRecording = false
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Cancel any pending delayed recording start.
     */
    private fun cancelPendingRecording() {
        startRecordingRunnable?.let { handler.removeCallbacks(it) }
        startRecordingRunnable = null
    }

    /**
     * Update the bubble's visual appearance based on state.
     */
    private fun updateBubbleAppearance(state: VoiceFlowOrchestrator.State, message: String) {
        try {
            when (state) {
                VoiceFlowOrchestrator.State.IDLE -> {
                    bubbleBackground?.setColor(Color.parseColor("#6750A4")) // Purple
                    iconView?.visibility = View.VISIBLE
                    iconView?.clearAnimation()
                    progressView?.visibility = View.GONE
                }

                VoiceFlowOrchestrator.State.LISTENING -> {
                    bubbleBackground?.setColor(Color.parseColor("#EF4444")) // Red
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

                VoiceFlowOrchestrator.State.PROCESSING -> {
                    bubbleBackground?.setColor(Color.parseColor("#FFA726")) // Orange
                    iconView?.visibility = View.GONE
                    progressView?.visibility = View.VISIBLE
                    bubbleView.clearAnimation()
                }

                VoiceFlowOrchestrator.State.PASTING -> {
                    bubbleBackground?.setColor(Color.parseColor("#4CAF50")) // Green
                    iconView?.visibility = View.VISIBLE
                    progressView?.visibility = View.GONE
                    bubbleView.clearAnimation()
                }

                VoiceFlowOrchestrator.State.ERROR -> {
                    bubbleBackground?.setColor(Color.parseColor("#EF4444")) // Red
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

        return NotificationCompat.Builder(this, VoiceFlowApplication.CHANNEL_ID)
            .setContentTitle("VoiceFlow Active")
            .setContentText("Press and hold the bubble to dictate")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
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
        Log.d(TAG, "Floating bubble service stopped")
        super.onDestroy()
    }
}
