package com.example.accident_detection3.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging

object FirebaseConnectionTest {
    
    private const val TAG = "FirebaseTest"
    
    fun testConnection(context: Context): ConnectionStatus {
        val status = ConnectionStatus()
        
        // Test 1: Firebase App Initialization
        try {
            val apps = FirebaseApp.getApps(context)
            status.isInitialized = apps.isNotEmpty()
            status.appName = apps.firstOrNull()?.name ?: "None"
            Log.d(TAG, "✅ Firebase initialized: ${status.isInitialized}")
        } catch (e: Exception) {
            status.isInitialized = false
            status.initError = e.message
            Log.e(TAG, "❌ Firebase initialization failed: ${e.message}")
        }
        
        // Test 2: Firebase Authentication
        try {
            val auth = FirebaseAuth.getInstance()
            status.authAvailable = true
            status.isUserLoggedIn = auth.currentUser != null
            status.userId = auth.currentUser?.uid
            Log.d(TAG, "✅ Firebase Auth available. User logged in: ${status.isUserLoggedIn}")
        } catch (e: Exception) {
            status.authAvailable = false
            status.authError = e.message
            Log.e(TAG, "❌ Firebase Auth error: ${e.message}")
        }
        
        // Test 3: Firebase Realtime Database
        try {
            val database = FirebaseDatabase.getInstance()
            status.databaseAvailable = true
            status.databaseUrl = database.reference.toString()
            Log.d(TAG, "✅ Firebase Database available: ${status.databaseUrl}")
        } catch (e: Exception) {
            status.databaseAvailable = false
            status.databaseError = e.message
            Log.e(TAG, "❌ Firebase Database error: ${e.message}")
        }
        
        // Test 4: Firebase Cloud Messaging
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    status.fcmAvailable = true
                    status.fcmToken = task.result
                    Log.d(TAG, "✅ FCM Token: ${task.result}")
                } else {
                    status.fcmAvailable = false
                    status.fcmError = task.exception?.message
                    Log.e(TAG, "❌ FCM Token error: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            status.fcmAvailable = false
            status.fcmError = e.message
            Log.e(TAG, "❌ FCM error: ${e.message}")
        }
        
        // Print summary
        printSummary(status)
        
        return status
    }
    
    private fun printSummary(status: ConnectionStatus) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "Firebase Connection Test Summary")
        Log.d(TAG, "========================================")
        Log.d(TAG, "Initialized: ${if (status.isInitialized) "✅" else "❌"}")
        Log.d(TAG, "Auth Available: ${if (status.authAvailable) "✅" else "❌"}")
        Log.d(TAG, "Database Available: ${if (status.databaseAvailable) "✅" else "❌"}")
        Log.d(TAG, "FCM Available: ${if (status.fcmAvailable) "✅" else "❌"}")
        Log.d(TAG, "User Logged In: ${if (status.isUserLoggedIn) "✅" else "❌"}")
        Log.d(TAG, "========================================")
    }
    
    data class ConnectionStatus(
        var isInitialized: Boolean = false,
        var appName: String = "",
        var initError: String? = null,
        
        var authAvailable: Boolean = false,
        var isUserLoggedIn: Boolean = false,
        var userId: String? = null,
        var authError: String? = null,
        
        var databaseAvailable: Boolean = false,
        var databaseUrl: String = "",
        var databaseError: String? = null,
        
        var fcmAvailable: Boolean = false,
        var fcmToken: String? = null,
        var fcmError: String? = null
    ) {
        fun isFullyConnected(): Boolean {
            return isInitialized && authAvailable && databaseAvailable && fcmAvailable
        }
        
        fun getStatusMessage(): String {
            return when {
                !isInitialized -> "Firebase not initialized: $initError"
                !authAvailable -> "Firebase Auth not available: $authError"
                !databaseAvailable -> "Firebase Database not available: $databaseError"
                !fcmAvailable -> "Firebase Messaging not available: $fcmError"
                else -> "Firebase fully connected!"
            }
        }
    }
}
