package com.example.accident_detection3.util

import android.location.Location
import android.util.Log
import kotlin.math.abs

/**
 * Hybrid Speed Calculator with Stop Detection
 * 
 * Features:
 * 1. Reads raw GPS speed from location object
 * 2. Calculates speed using distance and time between GPS updates
 * 3. Uses hybrid logic: GPS speed for fast movement, calculated for slow/stationary
 * 4. Maintains buffer of last 4 speed values for smoothing
 * 5. Updates immediately with accurate readings for both walking and driving
 * 6. Stop detection: Immediately sets speed to 0 when user stops moving
 * 
 * Hybrid Logic:
 * - If GPS speed > 1 km/h → Use GPS speed (faster, more accurate for vehicles)
 * - If GPS speed ≤ 1 km/h → Use calculated speed (better for walking/stationary)
 * 
 * Stop Detection Logic:
 * - Track distance moved over last 2 seconds
 * - If distance < 1 meter for 2 seconds → Immediately set speed to 0
 * - If movement detected again → Resume normal calculation
 */
class ImprovedSpeedCalculator {
    
    private val TAG = "HybridSpeedCalc"
    
    // Configuration
    private val GPS_SPEED_THRESHOLD = 2.0f  // km/h - raised: avoids GPS drift noise at low speed
    private val SPEED_BUFFER_SIZE = 2       // reduced from 4: less lag, faster response
    private val MIN_TIME_INTERVAL = 0.3f    // seconds - minimum time between updates
    private val MAX_TIME_INTERVAL = 5.0f    // seconds - maximum time between updates
    private val MAX_SPEED_JUMP = 80f        // km/h - raised: allow valid hard acceleration/braking
    private val MAX_ACCURACY_THRESHOLD = 40f // meters - relaxed from 25m: fewer dropped updates
    private val MIN_DISTANCE_THRESHOLD = 1.5f // meters - ignore GPS noise below this

    // Stop detection configuration
    private val STOP_DETECTION_DISTANCE = 2.5f // meters - raised from 1m: accounts for GPS drift
    private val STOP_DETECTION_DURATION = 2000L // milliseconds - 2 seconds
    
    // State variables
    private var previousLocation: Location? = null
    private var previousTime: Long = 0L
    private val speedBuffer = mutableListOf<Float>()
    private var lastCalculatedSpeed: Float = 0f
    
    // Stop detection state
    private var stopDetectionStartLocation: Location? = null
    private var stopDetectionStartTime: Long = 0L
    private var isStoppedState: Boolean = false
    
    // Statistics
    private var totalUpdates = 0
    private var gpsSpeedUsed = 0
    private var calculatedSpeedUsed = 0
    private var filteredUpdates = 0
    private var stopDetections = 0
    
    /**
     * Process new GPS location and calculate speed using hybrid method
     * 
     * @param location New GPS location
     * @return Calculated speed in km/h, or null if update should be ignored
     */
    fun calculateSpeed(location: Location): Float? {
        totalUpdates++
        
        // Check 1: Accuracy filter
        if (location.accuracy > MAX_ACCURACY_THRESHOLD) {
            Log.d(TAG, "❌ Rejected: Low accuracy ${String.format("%.1f", location.accuracy)}m")
            filteredUpdates++
            return null
        }
        
        // First location - initialize
        if (previousLocation == null) {
            previousLocation = location
            previousTime = System.currentTimeMillis()
            Log.d(TAG, "🎯 First location initialized")
            return 0f
        }
        
        val currentTime = System.currentTimeMillis()
        val timeDiffSeconds = (currentTime - previousTime) / 1000f
        
        // Check 2: Time interval filter
        if (timeDiffSeconds < MIN_TIME_INTERVAL) {
            return lastCalculatedSpeed // Return last known speed
        }
        
        if (timeDiffSeconds > MAX_TIME_INTERVAL) {
            Log.d(TAG, "⏱️ Too long: ${String.format("%.1f", timeDiffSeconds)}s - resetting")
            previousLocation = location
            previousTime = currentTime
            speedBuffer.clear()
            return 0f
        }
        
        // Get raw GPS speed (m/s to km/h)
        val gpsSpeedKmh = if (location.hasSpeed()) {
            location.speed * 3.6f
        } else {
            0f
        }
        
        // Calculate distance-based speed
        val distance = previousLocation!!.distanceTo(location)
        val calculatedSpeedMps = distance / timeDiffSeconds
        val calculatedSpeedKmh = calculatedSpeedMps * 3.6f
        
        // Stop Detection Logic
        // Check if user has stopped moving (distance < 1m for 2 seconds)
        if (stopDetectionStartLocation == null) {
            // Initialize stop detection tracking
            stopDetectionStartLocation = location
            stopDetectionStartTime = currentTime
        } else {
            // Calculate total distance moved since stop detection started
            val totalDistanceMoved = stopDetectionStartLocation!!.distanceTo(location)
            val timeSinceStopDetection = currentTime - stopDetectionStartTime
            
            if (totalDistanceMoved < STOP_DETECTION_DISTANCE && 
                timeSinceStopDetection >= STOP_DETECTION_DURATION) {
                // User has been stationary for 2 seconds - immediately set speed to 0
                if (!isStoppedState) {
                    isStoppedState = true
                    stopDetections++
                    Log.d(TAG, "🛑 STOP DETECTED: Distance moved ${String.format("%.2f", totalDistanceMoved)}m " +
                            "in ${String.format("%.1f", timeSinceStopDetection / 1000f)}s → Speed = 0 km/h")
                }
                
                // Clear buffer and set speed to 0
                speedBuffer.clear()
                speedBuffer.add(0f)
                previousLocation = location
                previousTime = currentTime
                // Reset anchor to current location so that when movement resumes,
                // distance is measured from the actual stop point, not the old anchor.
                stopDetectionStartLocation = location
                stopDetectionStartTime = currentTime
                lastCalculatedSpeed = 0f
                
                return 0f
            } else if (totalDistanceMoved >= STOP_DETECTION_DISTANCE) {
                // Movement detected - reset stop detection
                if (isStoppedState) {
                    Log.d(TAG, "🚶 MOVEMENT RESUMED: Distance ${String.format("%.2f", totalDistanceMoved)}m → " +
                            "Resuming normal speed calculation")
                    isStoppedState = false
                }
                stopDetectionStartLocation = location
                stopDetectionStartTime = currentTime
            }
        }
        
        // Hybrid logic: Choose best speed source
        val selectedSpeed: Float
        val speedSource: String
        
        if (gpsSpeedKmh > GPS_SPEED_THRESHOLD) {
            // Use GPS speed for fast movement (vehicles)
            selectedSpeed = gpsSpeedKmh
            speedSource = "GPS"
            gpsSpeedUsed++
        } else {
            // Use calculated speed for slow movement (walking/stationary)
            if (distance < MIN_DISTANCE_THRESHOLD) {
                // Too small movement - likely stationary
                selectedSpeed = 0f
                speedSource = "CALC (stationary)"
            } else {
                selectedSpeed = calculatedSpeedKmh
                speedSource = "CALC"
            }
            calculatedSpeedUsed++
        }
        
        Log.d(TAG, "🔄 Hybrid: GPS=${String.format("%.2f", gpsSpeedKmh)} km/h, " +
                "CALC=${String.format("%.2f", calculatedSpeedKmh)} km/h " +
                "(dist=${String.format("%.1f", distance)}m, time=${String.format("%.1f", timeDiffSeconds)}s)")
        Log.d(TAG, "✅ Selected: ${String.format("%.2f", selectedSpeed)} km/h [$speedSource]")
        
        // Check 3: Spike filter (reject unrealistic changes)
        if (speedBuffer.isNotEmpty()) {
            val avgSpeed = speedBuffer.average().toFloat()
            val speedJump = abs(selectedSpeed - avgSpeed)
            
            if (speedJump > MAX_SPEED_JUMP) {
                Log.d(TAG, "⚠️ Speed spike rejected: ${String.format("%.2f", avgSpeed)} → " +
                        "${String.format("%.2f", selectedSpeed)} km/h (jump: ${String.format("%.2f", speedJump)} km/h)")
                filteredUpdates++
                previousLocation = location
                previousTime = currentTime
                return lastCalculatedSpeed
            }
        }
        
        // Valid speed - add to buffer (4 values)
        speedBuffer.add(selectedSpeed)
        if (speedBuffer.size > SPEED_BUFFER_SIZE) {
            speedBuffer.removeAt(0)
        }
        
        // Calculate averaged speed for smoothing
        val averagedSpeed = speedBuffer.average().toFloat()
        
        Log.d(TAG, "📊 Final: raw=${String.format("%.2f", selectedSpeed)} km/h, " +
                "averaged=${String.format("%.2f", averagedSpeed)} km/h " +
                "(buffer: ${speedBuffer.size}/$SPEED_BUFFER_SIZE)")
        
        // Update state
        previousLocation = location
        previousTime = currentTime
        lastCalculatedSpeed = averagedSpeed
        
        return averagedSpeed
    }
    
    /**
     * Get current speed without new calculation
     */
    fun getCurrentSpeed(): Float {
        return lastCalculatedSpeed
    }

    /**
     * Expose previous location for raw speed calculation in TrackingService
     */
    fun getPreviousLocation(): Location? = previousLocation

    /**
     * Expose previous timestamp for raw speed calculation in TrackingService
     */
    fun getPreviousTime(): Long = previousTime
    
    /**
     * Get averaged speed from buffer
     */
    fun getAveragedSpeed(): Float {
        return if (speedBuffer.isNotEmpty()) {
            speedBuffer.average().toFloat()
        } else {
            lastCalculatedSpeed
        }
    }
    
    /**
     * Check if speed data is stable (buffer is full)
     */
    fun isSpeedStable(): Boolean {
        return speedBuffer.size >= SPEED_BUFFER_SIZE
    }
    
    /**
     * Get buffer size
     */
    fun getBufferSize(): Int {
        return speedBuffer.size
    }
    
    /**
     * Reset calculator state
     */
    fun reset() {
        previousLocation = null
        previousTime = 0L
        speedBuffer.clear()
        lastCalculatedSpeed = 0f
        stopDetectionStartLocation = null
        stopDetectionStartTime = 0L
        isStoppedState = false
        totalUpdates = 0
        gpsSpeedUsed = 0
        calculatedSpeedUsed = 0
        filteredUpdates = 0
        stopDetections = 0
        Log.d(TAG, "🔄 Hybrid calculator reset (with stop detection)")
    }
    
    /**
     * Get statistics
     */
    fun getStatistics(): Map<String, Any> {
        val gpsPercentage = if (totalUpdates > 0) (gpsSpeedUsed * 100f / totalUpdates) else 0f
        val calcPercentage = if (totalUpdates > 0) (calculatedSpeedUsed * 100f / totalUpdates) else 0f
        
        return mapOf(
            "totalUpdates" to totalUpdates,
            "gpsSpeedUsed" to gpsSpeedUsed,
            "calculatedSpeedUsed" to calculatedSpeedUsed,
            "filteredUpdates" to filteredUpdates,
            "stopDetections" to stopDetections,
            "isStoppedState" to isStoppedState,
            "gpsPercentage" to gpsPercentage,
            "calcPercentage" to calcPercentage,
            "bufferSize" to speedBuffer.size,
            "isStable" to isSpeedStable(),
            "currentSpeed" to lastCalculatedSpeed
        )
    }
    
    /**
     * Log statistics
     */
    fun logStatistics() {
        val stats = getStatistics()
        Log.d(TAG, "📈 Hybrid Statistics:")
        Log.d(TAG, "   Total updates: ${stats["totalUpdates"]}")
        Log.d(TAG, "   GPS speed used: ${stats["gpsSpeedUsed"]} " +
                "(${String.format("%.1f", stats["gpsPercentage"])}%)")
        Log.d(TAG, "   Calculated speed used: ${stats["calculatedSpeedUsed"]} " +
                "(${String.format("%.1f", stats["calcPercentage"])}%)")
        Log.d(TAG, "   Filtered: ${stats["filteredUpdates"]}")
        Log.d(TAG, "   Stop detections: ${stats["stopDetections"]}")
        Log.d(TAG, "   Currently stopped: ${stats["isStoppedState"]}")
        Log.d(TAG, "   Buffer: ${stats["bufferSize"]}/$SPEED_BUFFER_SIZE, " +
                "Stable: ${stats["isStable"]}")
        Log.d(TAG, "   Current speed: ${String.format("%.2f", stats["currentSpeed"])} km/h")
    }
}
