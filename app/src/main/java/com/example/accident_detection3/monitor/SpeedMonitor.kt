package com.example.accident_detection3.monitor

import com.example.accident_detection3.data.SpeedLimitPrefs

class SpeedMonitor(private val speedLimitPrefs: SpeedLimitPrefs) {
    
    private var isOverspeedAlertActive = false
    // Timestamp of the last alert — prevents re-triggering immediately on service restart
    // while the vehicle is still over the limit.
    private var lastAlertTimeMs = 0L
    private val ALERT_COOLDOWN_MS = 60_000L // 60 seconds between alerts

    fun checkSpeed(currentSpeed: Float): Boolean {
        val speedLimit = speedLimitPrefs.speedLimit
        val isOverspeed = currentSpeed > speedLimit
        
        if (isOverspeed && !isOverspeedAlertActive) {
            val now = System.currentTimeMillis()
            if (now - lastAlertTimeMs < ALERT_COOLDOWN_MS) return false
            isOverspeedAlertActive = true
            lastAlertTimeMs = now
            return true
        } else if (!isOverspeed) {
            isOverspeedAlertActive = false
        }
        
        return false
    }
    
    fun resetAlert() {
        isOverspeedAlertActive = false
    }
}
