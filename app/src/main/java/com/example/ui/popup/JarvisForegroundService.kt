@file:Suppress("DEPRECATION")
package com.example.ui.popup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import kotlin.math.abs

class JarvisForegroundService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        showFloatingBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val popupIntent = Intent(this, JarvisPopupActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            popupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, "jarvis_channel")
            .setContentTitle("Jarvis is awake")
            .setContentText("Tap notification or floating bubble to call Jarvis.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
                if (type != 0) {
                    startForeground(1, notification, type)
                } else {
                    startForeground(1, notification)
                }
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            Log.e("JarvisService", "Failed to start service in foreground", e)
            try {
                stopSelf()
            } catch (ex: Exception) {}
        }
        
        return START_STICKY
    }

    private fun showFloatingBubble() {
        if (!android.provider.Settings.canDrawOverlays(this)) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val sizeInPx = (56 * resources.displayMetrics.density).toInt()
        val paddingInPx = (14 * resources.displayMetrics.density).toInt()
        
        // We use a simple ImageView as the bubble to avoid needing a complex XML layout
        val imageView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#041424"))
                setStroke((2 * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#00E5FF"))
            }
            setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)
            adjustViewBounds = true
            layoutParams = android.view.ViewGroup.LayoutParams(sizeInPx, sizeInPx)
            elevation = 10f * resources.displayMetrics.density
        }
        
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        bubbleView = imageView

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        imageView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager?.updateViewLayout(view, layoutParams)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.performClick()
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    // If it was a small movement, consider it a click
                    if (diffX < 10 && diffY < 10) {
                        val popupIntent = Intent(this, JarvisPopupActivity::class.java).apply {
                            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(popupIntent)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(bubbleView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        bubbleView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "jarvis_channel",
                "Jarvis Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, JarvisForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
