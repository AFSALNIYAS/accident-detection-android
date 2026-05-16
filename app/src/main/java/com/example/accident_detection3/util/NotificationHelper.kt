package com.example.accident_detection3.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.accident_detection3.MainActivity
import com.example.accident_detection3.R

object NotificationHelper {

    private const val SPEED_WARNING_CHANNEL_ID = "speed_warning_channel"
    private const val SPEED_WARNING_NOTIFICATION_ID = 1002

    // Full-screen accident alert — must be a separate high-priority channel
    const val ACCIDENT_ALERT_CHANNEL_ID = "accident_alert_channel_v3"
    const val ACCIDENT_ALERT_NOTIFICATION_ID = 1003

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Foreground service channel (low importance, silent)
            val serviceChannel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows speed monitoring status"
            }

            // Speed warning channel (high importance, makes sound)
            val warningChannel = NotificationChannel(
                SPEED_WARNING_CHANNEL_ID,
                "Speed Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when speed limit is exceeded"
            }

            // Accident alert channel — IMPORTANCE_MAX required for full-screen intent + action buttons
            // Channel ID is versioned so it gets recreated fresh (Android caches channel settings)
            val accidentChannel = NotificationChannel(
                ACCIDENT_ALERT_CHANNEL_ID,
                "Accident Alerts",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Full-screen alert when a possible accident is detected"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                enableLights(true)
                setBypassDnd(true)   // show even in Do Not Disturb mode
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(warningChannel)
            notificationManager.createNotificationChannel(accidentChannel)
        }
    }

    /**
     * Launch AccidentAlertActivity via a full-screen intent notification.
     * Also shows "I'm Safe" and "Send Help" action buttons directly on the notification
     * so the user can respond without opening the full-screen activity.
     */
    fun showAccidentAlert(
        context: Context,
        latitude: Double,
        longitude: Double,
        speed: Float
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            ACCIDENT_ALERT_NOTIFICATION_ID,
            buildAccidentNotification(context, latitude, longitude, speed, 30)
        )
    }

    /**
     * Update the notification with the current countdown seconds.
     * Called every second from AccidentAlertActivity while the timer runs.
     */
    fun updateAccidentNotificationCountdown(
        context: Context,
        latitude: Double,
        longitude: Double,
        speed: Float,
        secondsRemaining: Int
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            ACCIDENT_ALERT_NOTIFICATION_ID,
            buildAccidentNotification(context, latitude, longitude, speed, secondsRemaining)
        )
    }

    private fun buildAccidentNotification(
        context: Context,
        latitude: Double,
        longitude: Double,
        speed: Float,
        secondsRemaining: Int
    ): android.app.Notification {
        // Tap notification → open AccidentAlertActivity
        val activityIntent = Intent(
            context,
            com.example.accident_detection3.emergency.AccidentAlertActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Constants.EXTRA_LATITUDE, latitude)
            putExtra(Constants.EXTRA_LONGITUDE, longitude)
            putExtra(Constants.EXTRA_SPEED, speed)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            ACCIDENT_ALERT_NOTIFICATION_ID,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "I'm Safe" action button
        val imSafeIntent = android.content.Intent(
            com.example.accident_detection3.emergency.NotificationActionReceiver.ACTION_IM_SAFE
        ).apply { setPackage(context.packageName) }
        val imSafePendingIntent = PendingIntent.getBroadcast(
            context, 1001, imSafeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Send Help" action button — carries location so the receiver can send SMS
        val sendHelpIntent = android.content.Intent(
            com.example.accident_detection3.emergency.NotificationActionReceiver.ACTION_SEND_HELP
        ).apply {
            setPackage(context.packageName)
            putExtra(Constants.EXTRA_LATITUDE, latitude)
            putExtra(Constants.EXTRA_LONGITUDE, longitude)
            putExtra(Constants.EXTRA_SPEED, speed)
        }
        val sendHelpPendingIntent = PendingIntent.getBroadcast(
            context, 1002, sendHelpIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, ACCIDENT_ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚠️ Possible Accident Detected")
            .setContentText("Are you safe? Sending emergency in $secondsRemaining seconds...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "I'm Safe", imSafePendingIntent)
            .addAction(android.R.drawable.ic_dialog_alert, "Send Help", sendHelpPendingIntent)
            .build()
    }

    /**
     * Dismiss the accident alert notification once the user has responded.
     */
    fun dismissAccidentAlert(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ACCIDENT_ALERT_NOTIFICATION_ID)
    }

    /**
     * Show a heads-up notification warning that the speed limit has been reached.
     * This is NOT an emergency alert — it is a simple warning only.
     */
    fun showSpeedLimitWarning(context: Context, currentSpeed: Float, speedLimit: Float) {
        val intent = Intent(context, com.example.accident_detection3.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, SPEED_WARNING_CHANNEL_ID)
            .setContentTitle("⚠️ Speed Limit Reached")
            .setContentText("You are driving at ${currentSpeed.toInt()} km/h (limit: ${speedLimit.toInt()} km/h)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(SPEED_WARNING_NOTIFICATION_ID, notification)
    }
    
    fun createForegroundNotification(context: Context, speed: Float): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Accident Detection Active")
            .setContentText("Current Speed: ${speed.toInt()} km/h")
            // Use ic_launcher instead of ic_launcher_foreground to avoid potential crash
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
