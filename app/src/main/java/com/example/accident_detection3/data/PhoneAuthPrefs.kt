package com.example.accident_detection3.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.accident_detection3.util.Constants
import java.security.MessageDigest

class PhoneAuthPrefs(context: Context) {
    private val TAG = "PhoneAuthPrefs"

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "${Constants.PREFS_NAME}_auth",
        Context.MODE_PRIVATE
    )

    fun registerUser(phoneNumber: String, password: String): Boolean {
        if (isPhoneRegistered(phoneNumber)) {
            Log.d(TAG, "Registration failed: phone already registered - $phoneNumber")
            return false
        }

        val hashedPassword = hashPassword(password)
        prefs.edit()
            .putString("user_$phoneNumber", hashedPassword)
            .putString("current_user", phoneNumber)
            .putBoolean("is_logged_in", true)   // persistent login flag
            .apply()

        Log.d(TAG, "User registered successfully: $phoneNumber")
        return true
    }

    fun loginUser(phoneNumber: String, password: String): Boolean {
        val storedHash = prefs.getString("user_$phoneNumber", null)

        if (storedHash == null) {
            Log.d(TAG, "Login failed: phone not registered - $phoneNumber")
            return false
        }

        val inputHash = hashPassword(password)

        return if (storedHash == inputHash) {
            prefs.edit()
                .putString("current_user", phoneNumber)
                .putBoolean("is_logged_in", true)   // persistent login flag
                .apply()
            Log.d(TAG, "Login successful: $phoneNumber")
            true
        } else {
            Log.d(TAG, "Login failed: wrong password for $phoneNumber")
            false
        }
    }

    fun isPhoneRegistered(phoneNumber: String): Boolean {
        return prefs.contains("user_$phoneNumber")
    }

    fun getCurrentUser(): String? {
        return prefs.getString("current_user", null)
    }

    fun logout() {
        Log.d(TAG, "User logged out: ${getCurrentUser()}")
        prefs.edit()
            .remove("current_user")
            .putBoolean("is_logged_in", false)  // clear persistent login flag
            .apply()
    }

    fun isLoggedIn(): Boolean {
        // Both flags must be true — prevents stale login state where flag is set but user was cleared
        val loggedIn = prefs.getBoolean("is_logged_in", false)
        val hasUser = prefs.getString("current_user", null) != null
        Log.d(TAG, "isLoggedIn check: flag=$loggedIn, hasUser=$hasUser, currentUser: ${getCurrentUser()}")
        return loggedIn && hasUser
    }

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}