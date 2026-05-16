package com.example.accident_detection3.emergency

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.accident_detection3.notification.EmergencyNotifier
import com.example.accident_detection3.util.Constants
import com.example.accident_detection3.util.NotificationHelper

/**
 * Handles "I'm Safe" and "Send Help" button taps directly from the accident
 * alert notification — without requiring the user to open the full-screen activity.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_IM_SAFE = "com.example.accident_detection3.ACTION_IM_SAFE"
        const val ACTION_SEND_HELP = "com.example.accident_detection3.ACTION_SEND_HELP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_IM_SAFE -> {
                Log.d("NotificationActionReceiver", "User tapped 'I'm Safe' from notification")
                NotificationHelper.dismissAccidentAlert(context)
                // Tell TrackingService to reset the detector and finish AccidentAlertActivity
                context.sendBroadcast(Intent(Constants.ACTION_ACCIDENT_CANCELLED).apply {
                    setPackage(context.packageName)
                })
            }

            ACTION_SEND_HELP -> {
                Log.d("NotificationActionReceiver", "User tapped 'Send Help' from notification")
                val latitude = intent.getDoubleExtra(Constants.EXTRA_LATITUDE, 0.0)
                val longitude = intent.getDoubleExtra(Constants.EXTRA_LONGITUDE, 0.0)
                val speed = intent.getFloatExtra(Constants.EXTRA_SPEED, 0f)

                // Send SMS immediately
                try {
                    EmergencyNotifier(context).sendEmergencyAlertSMS(latitude, longitude)
                } catch (e: Exception) {
                    Log.e("NotificationActionReceiver", "SMS failed: ${e.message}", e)
                }

                NotificationHelper.dismissAccidentAlert(context)

                // Broadcast so TrackingService resets and SpeedMonitoringActivity updates
                context.sendBroadcast(Intent(Constants.ACTION_EMERGENCY_TRIGGERED).apply {
                    setPackage(context.packageName)
                    putExtra(Constants.EXTRA_LATITUDE, latitude)
                    putExtra(Constants.EXTRA_LONGITUDE, longitude)
                    putExtra(Constants.EXTRA_SPEED, speed)
                })
            }
        }
    }
}
