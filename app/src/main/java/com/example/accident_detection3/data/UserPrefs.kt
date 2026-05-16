package com.example.accident_detection3.data

import android.content.Context
import android.content.SharedPreferences
import com.example.accident_detection3.util.Constants

class UserPrefs(context: Context) {
    private val prefs: SharedPreferences = run {
        // Store data per-user so each account has its own profile
        val currentUser = PhoneAuthPrefs(context).getCurrentUser()
        val prefsName = if (currentUser != null) "${Constants.PREFS_NAME}_$currentUser" else Constants.PREFS_NAME
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    var userName: String
        get() = prefs.getString(Constants.KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(Constants.KEY_USER_NAME, value).apply()

    var mobileNumber: String
        get() = prefs.getString(Constants.KEY_MOBILE_NUMBER, "") ?: ""
        set(value) = prefs.edit().putString(Constants.KEY_MOBILE_NUMBER, value).apply()

    var bloodGroup: String
        get() = prefs.getString(Constants.KEY_BLOOD_GROUP, "") ?: ""
        set(value) = prefs.edit().putString(Constants.KEY_BLOOD_GROUP, value).apply()

    var isSetupComplete: Boolean
        get() = prefs.getBoolean(Constants.KEY_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(Constants.KEY_SETUP_COMPLETE, value).apply()

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
