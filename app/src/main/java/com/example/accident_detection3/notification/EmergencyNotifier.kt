package com.example.accident_detection3.notification

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.example.accident_detection3.data.EmergencyContact
import com.example.accident_detection3.data.EmergencyContactManager
import com.example.accident_detection3.data.NotificationPrefs
import com.example.accident_detection3.data.UserPrefs

class EmergencyNotifier(private val context: Context) {
    private val TAG = "EmergencyNotifier"
    private val contactManager = EmergencyContactManager(context)
    private val userPrefs = UserPrefs(context)
    private val notificationPrefs = NotificationPrefs(context)

    /**
     * Send emergency alert SMS with user info and Google Maps link
     * Used by AccidentAlertActivity
     */
    fun sendEmergencyAlertSMS(latitude: Double, longitude: Double) {
        val contacts = contactManager.getContacts()

        if (contacts.isEmpty()) {
            Log.w(TAG, "⚠️ No emergency contacts found. SMS not sent.")
            notificationPrefs.saveNotification("⚠️ SMS not sent — no emergency contacts added")
            return
        }

        val mapsLink = if (latitude != 0.0 || longitude != 0.0) {
            "https://maps.google.com/?q=$latitude,$longitude"
        } else {
            "Location unavailable (GPS not fixed)"
        }

        val userName = userPrefs.userName.ifEmpty { "Unknown" }
        val mobile = userPrefs.mobileNumber.ifEmpty { "N/A" }
        val blood = userPrefs.bloodGroup.ifEmpty { "N/A" }

        val message = "🚨 ACCIDENT ALERT: $userName needs help!!\n" +
                "This may be an emergency 🚨\n" +
                "Details:\n" +
                "Name: $userName\n" +
                "Mobile: $mobile\n" +
                "Blood: $blood\n" +
                "Location: $mapsLink"

        Log.d(TAG, "Sending emergency alert SMS to ${contacts.size} contacts")
        Log.d(TAG, "Message (${message.length} chars): $message")

        sendSMSToContacts(contacts, message)
    }

    /**
     * Send detailed emergency SMS with user info
     * Used for overspeed alerts
     */
    fun sendEmergencySMS(latitude: Double, longitude: Double) {
        val contacts = contactManager.getContacts()

        if (contacts.isEmpty()) {
            Log.w(TAG, "⚠️ No emergency contacts found. SMS not sent.")
            notificationPrefs.saveNotification("⚠️ SMS not sent — no emergency contacts added")
            return
        }

        val message = buildEmergencyMessage(latitude, longitude)
        Log.d(TAG, "Message length: ${message.length} characters")

        sendSMSToContacts(contacts, message)
    }

    private fun sendSMSToContacts(contacts: List<EmergencyContact>, message: String) {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        contacts.forEach { contact ->
            try {
                val sentAction = "SMS_SENT_${contact.phoneNumber}_${System.currentTimeMillis()}"
                val deliveredAction = "SMS_DELIVERED_${contact.phoneNumber}_${System.currentTimeMillis()}"

                // Sent result receiver
                val sentReceiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        when (resultCode) {
                            Activity.RESULT_OK -> {
                                Log.d(TAG, "✅ SMS SENT to ${contact.name} (${contact.phoneNumber})")
                                notificationPrefs.saveNotification("✅ SMS sent to ${contact.name} (${contact.phoneNumber})")
                            }
                            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                                Log.e(TAG, "❌ Generic failure to ${contact.phoneNumber}")
                                notificationPrefs.saveNotification("❌ SMS failed (generic error) to ${contact.name}")
                            }
                            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                                Log.e(TAG, "❌ No service to ${contact.phoneNumber}")
                                notificationPrefs.saveNotification("❌ SMS failed (no service) to ${contact.name}")
                            }
                            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                                Log.e(TAG, "❌ Radio off to ${contact.phoneNumber}")
                                notificationPrefs.saveNotification("❌ SMS failed (radio off) to ${contact.name}")
                            }
                            SmsManager.RESULT_ERROR_NULL_PDU -> {
                                Log.e(TAG, "❌ Null PDU to ${contact.phoneNumber}")
                                notificationPrefs.saveNotification("❌ SMS failed (null PDU) to ${contact.name}")
                            }
                        }
                        try { context.unregisterReceiver(this) } catch (e: Exception) {
                            Log.e(TAG, "Error unregistering sent receiver: ${e.message}")
                        }
                    }
                }

                // Delivery result receiver
                val deliveredReceiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        when (resultCode) {
                            Activity.RESULT_OK -> {
                                Log.d(TAG, "📬 SMS DELIVERED to ${contact.name} (${contact.phoneNumber})")
                                notificationPrefs.saveNotification("📬 SMS delivered to ${contact.name}")
                            }
                            Activity.RESULT_CANCELED -> {
                                Log.e(TAG, "❌ SMS NOT DELIVERED to ${contact.phoneNumber}")
                                notificationPrefs.saveNotification("❌ SMS not delivered to ${contact.name}")
                            }
                        }
                        try { context.unregisterReceiver(this) } catch (e: Exception) {
                            Log.e(TAG, "Error unregistering delivered receiver: ${e.message}")
                        }
                    }
                }

                // Register receivers with proper flags for Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(sentReceiver, IntentFilter(sentAction), Context.RECEIVER_NOT_EXPORTED)
                    context.registerReceiver(deliveredReceiver, IntentFilter(deliveredAction), Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(sentReceiver, IntentFilter(sentAction))
                    context.registerReceiver(deliveredReceiver, IntentFilter(deliveredAction))
                }

                val requestCode = contact.phoneNumber.hashCode() and 0x7FFFFFFF
                val sentIntent = PendingIntent.getBroadcast(
                    context, requestCode, Intent(sentAction), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val deliveredIntent = PendingIntent.getBroadcast(
                    context, requestCode + 1, Intent(deliveredAction), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                try {
                    // Use multipart for messages over 160 characters.
                    // Each part gets its own unique PendingIntent so the receiver fires once per part
                    // and isn't unregistered prematurely on the first part's callback.
                    val parts = smsManager.divideMessage(message)
                    if (parts.size > 1) {
                        Log.d(TAG, "Sending multipart SMS (${parts.size} parts) to ${contact.name}")
                        val sentIntents = ArrayList(parts.mapIndexed { i, _ ->
                            PendingIntent.getBroadcast(
                                context, requestCode + 2 + i, Intent(sentAction),
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                            )
                        })
                        val deliveredIntents = ArrayList(parts.mapIndexed { i, _ ->
                            PendingIntent.getBroadcast(
                                context, requestCode + 2 + parts.size + i, Intent(deliveredAction),
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                            )
                        })
                        smsManager.sendMultipartTextMessage(
                            contact.phoneNumber, null, parts, sentIntents, deliveredIntents
                        )
                    } else {
                        Log.d(TAG, "Sending single SMS to ${contact.name}")
                        smsManager.sendTextMessage(
                            contact.phoneNumber, null, message, sentIntent, deliveredIntent
                        )
                    }
                } catch (e: Exception) {
                    // Send failed — unregister receivers so they don't leak
                    try { context.unregisterReceiver(sentReceiver) } catch (_: Exception) {}
                    try { context.unregisterReceiver(deliveredReceiver) } catch (_: Exception) {}
                    throw e
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ SMS FAILED to ${contact.phoneNumber}: ${e.message}")
                notificationPrefs.saveNotification("❌ SMS exception to ${contact.name}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun buildEmergencyMessage(latitude: Double, longitude: Double): String {
        val userName = userPrefs.userName
        val mobileNumber = userPrefs.mobileNumber
        val bloodGroup = userPrefs.bloodGroup
        val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"

        return """
            🚨 EMERGENCY! ${userName.ifEmpty { "User" }} needs help!
            Mobile: $mobileNumber
            Blood: $bloodGroup
            Location: $mapsLink
        """.trimIndent()
    }
}
