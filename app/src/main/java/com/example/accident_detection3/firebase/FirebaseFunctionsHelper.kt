package com.example.accident_detection3.firebase

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase

/**
 * Helper class for calling Firebase Cloud Functions
 * Provides server-side emergency notification capabilities
 */
class FirebaseFunctionsHelper {
    
    private val TAG = "FirebaseFunctionsHelper"
    private val functions: FirebaseFunctions = Firebase.functions
    
    /**
     * Call emergency services via Cloud Function
     * This provides a backup notification system that works even if device is damaged
     */
    fun notifyEmergencyServices(
        userId: String,
        emergencyId: String,
        latitude: Double,
        longitude: Double,
        callback: (Boolean, String?) -> Unit
    ) {
        Log.d(TAG, "Calling notifyEmergencyServices cloud function")
        
        val parameters = hashMapOf(
            "userId" to userId,
            "emergencyId" to emergencyId,
            "latitude" to latitude,
            "longitude" to longitude
        )
        
        functions
            .getHttpsCallable("notifyEmergencyServices")
            .call(parameters)
            .addOnSuccessListener { result: HttpsCallableResult ->
                Log.d(TAG, "Emergency services notified successfully")
                // Using getData() explicitly because the 'data' property can sometimes be 
                // resolved to a private field in certain Kotlin/Firebase SDK combinations.
                val response = result.getData() as? Map<*, *>
                val message = response?.get("message") as? String ?: "Success"
                callback(true, message)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to notify emergency services: ${e.message}", e)
                callback(false, e.message)
            }
    }
    
    /**
     * Test cloud function connection
     * Use this to verify Firebase Functions are deployed and working
     */
    fun testConnection(callback: (Boolean, String?) -> Unit) {
        Log.d(TAG, "Testing cloud function connection")
        
        // Simple test call
        functions
            .getHttpsCallable("notifyEmergencyServices")
            .call(hashMapOf("test" to true))
            .addOnSuccessListener {
                Log.d(TAG, "Cloud Functions connection successful")
                callback(true, "Connected")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Cloud Functions connection failed: ${e.message}", e)
                callback(false, e.message)
            }
    }
}
