package com.example.accident_detection3.util

object Constants {
    // Location update intervals (faster for real-time speed like Google Maps)
    const val LOCATION_UPDATE_INTERVAL_MS = 1000L // 1 second
    const val LOCATION_FASTEST_INTERVAL_MS = 500L // 0.5 seconds
    
    // Countdown durations
    const val OVERSPEED_COUNTDOWN_SECONDS = 30
    const val ACCIDENT_COUNTDOWN_SECONDS = 30
    
    // Accident detection thresholds
    const val ACCIDENT_SPEED_HIGH_THRESHOLD_KMH = 35f // must match AccidentDetector.HIGH_SPEED_THRESHOLD
    const val ACCIDENT_SPEED_LOW_THRESHOLD_KMH = 5f
    const val ACCIDENT_SPEED_DROP_WINDOW_SECONDS = 3
    
    // Notification
    const val NOTIFICATION_CHANNEL_ID = "accident_detection_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Accident Detection"
    const val FOREGROUND_SERVICE_NOTIFICATION_ID = 1001
    
    // Broadcast actions
    const val ACTION_SPEED_UPDATE = "com.example.accident_detection3.SPEED_UPDATE"
    const val ACTION_ACCIDENT_CONFIRMED = "com.example.accident_detection3.ACCIDENT_CONFIRMED"
    const val ACTION_ACCIDENT_CANCELLED = "com.example.accident_detection3.ACCIDENT_CANCELLED"
    const val ACTION_EMERGENCY_TRIGGERED = "com.example.accident_detection3.EMERGENCY_TRIGGERED"
    const val ACTION_SIMULATE_ACCIDENT = "com.example.accident_detection3.SIMULATE_ACCIDENT"
    const val ACTION_SIMULATE_SPEED = "com.example.accident_detection3.SIMULATE_SPEED"
    const val EXTRA_SPEED = "extra_speed"
    const val EXTRA_LATITUDE = "extra_latitude"
    const val EXTRA_LONGITUDE = "extra_longitude"
    const val EXTRA_IS_ACCIDENT = "extra_is_accident"
    
    // SharedPreferences
    const val PREFS_NAME = "accident_detection_prefs"
    const val KEY_SPEED_LIMIT = "speed_limit"
    const val KEY_USER_NAME = "user_name"
    const val KEY_MOBILE_NUMBER = "mobile_number"
    const val KEY_BLOOD_GROUP = "blood_group"
    const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"
    const val KEY_SETUP_COMPLETE = "setup_complete"
    
    // Default values
    const val DEFAULT_SPEED_LIMIT = 80f
}
