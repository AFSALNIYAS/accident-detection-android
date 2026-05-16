package com.example.accident_detection3.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.accident_detection3.MainActivity
import com.example.accident_detection3.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AccidentDetectionMessagingService : FirebaseMessagingService() {

    companion object {
        // Monotonically increasing ID avoids overflow and collision with other notification IDs
        private val notificationIdCounter = java.util.concurrent.atomic.AtomicInteger(2000)
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        remoteMessage.notification?.let { notification ->
            showNotification(
                notification.title ?: "Accident Detection",
                notification.body ?: ""
            )
        }
        
        remoteMessage.data.isNotEmpty().let {
            handleDataPayload(remoteMessage.data)
        }
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Send token to your server or save to Firebase Database
        saveTokenToDatabase(token)
    }
    
    private fun handleDataPayload(data: Map<String, String>) {
        val type = data["type"]
        when (type) {
            "emergency" -> {
                val userId = data["userId"]
                val location = data["location"]
                showNotification(
                    "Emergency Alert",
                    "Emergency detected for user at $location"
                )
            }
            "overspeed" -> {
                showNotification(
                    "Overspeed Alert",
                    data["message"] ?: "Overspeed detected"
                )
            }
            "accident" -> {
                showNotification(
                    "Accident Alert",
                    data["message"] ?: "Accident detected"
                )
            }
        }
    }
    
    private fun showNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "accident_detection_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Accident Detection Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency and accident alerts"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        notificationManager.notify(notificationIdCounter.getAndIncrement(), notification)
    }
    
    private fun saveTokenToDatabase(token: String) {
        val userId = FirebaseManager.getCurrentUserId() ?: return
        val database = FirebaseManager.database
        database.getReference("users").child(userId).child("fcmToken").setValue(token)
    }
}
