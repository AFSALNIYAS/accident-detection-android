package com.example.accident_detection3.data

import android.content.Context
import android.content.SharedPreferences
import com.example.accident_detection3.util.Constants

class SpeedLimitPrefs(context: Context) {
    private val prefs: SharedPreferences = run {
        val currentUser = PhoneAuthPrefs(context).getCurrentUser()
        val prefsName = if (currentUser != null) "${Constants.PREFS_NAME}_$currentUser" else Constants.PREFS_NAME
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }
    
    var speedLimit: Float
        get() = prefs.getFloat(Constants.KEY_SPEED_LIMIT, Constants.DEFAULT_SPEED_LIMIT)
        set(value) = prefs.edit().putFloat(Constants.KEY_SPEED_LIMIT, value).apply()
}
