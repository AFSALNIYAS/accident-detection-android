package com.example.accident_detection3.data

import android.content.Context

/**
 * Persists the active accident alert state so NotificationActivity
 * can show a live countdown even after the full-screen alert is dismissed.
 */
class AlertStatePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("alert_state", Context.MODE_PRIVATE)

    fun setAlertActive(startTimeMs: Long, latitude: Double, longitude: Double, speed: Float) {
        prefs.edit()
            .putLong("start_time", startTimeMs)
            .putLong("lat_bits", java.lang.Double.doubleToRawLongBits(latitude))
            .putLong("lon_bits", java.lang.Double.doubleToRawLongBits(longitude))
            .putFloat("speed", speed)
            .putBoolean("active", true)
            .apply()
    }

    fun clearAlert() {
        prefs.edit().putBoolean("active", false).apply()
    }

    fun isAlertActive(): Boolean = prefs.getBoolean("active", false)

    fun getStartTime(): Long = prefs.getLong("start_time", 0L)

    fun getLatitude(): Double =
        java.lang.Double.longBitsToDouble(prefs.getLong("lat_bits", 0L))

    fun getLongitude(): Double =
        java.lang.Double.longBitsToDouble(prefs.getLong("lon_bits", 0L))

    fun getSpeed(): Float = prefs.getFloat("speed", 0f)
}
