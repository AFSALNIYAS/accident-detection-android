package com.example.accident_detection3.detector

import android.location.Location
import android.util.Log

/**
 * Pattern-Based Accident Detection System with Stop Confirmation
 *
 * Algorithm:
 * 1. HIGH_SPEED: speed > 35 km/h
 * 2. DECELERATION: peak speed in last 5s > 35 km/h AND drop > 25 km/h
 *    (no minimum current-speed requirement — stop confirmation handles that)
 * 3. STOP_CONFIRMATION: monitor for 5 seconds
 *    - speed stays below 5 km/h AND distance moved < 5 meters -> CONFIRM
 *    - distance >= 5 meters -> CANCEL (vehicle still moving)
 *
 * The 5-second deceleration window handles real-world gradual braking
 * (e.g. 60 → 42 → 18 → 0 over 3-4 GPS ticks at 1s intervals).
 */
class AccidentDetector {

    interface AccidentListener {
        fun onAccidentDetected(speedBefore: Float, speedAfter: Float)
    }

    var listener: AccidentListener? = null

    private val TAG = "AccidentDetector"

    private val SPEED_BUFFER_SIZE = 8
    private val DECELERATION_TIME_WINDOW = 5000L  // extended: covers gradual braking over ~4 GPS ticks
    private val STOP_CONFIRMATION_DURATION = 5000L
    private val LOW_SPEED_THRESHOLD = 5f
    private val STOP_CONFIRMATION_DISTANCE = 10f  // meters — raised from 5: real GPS drifts 3-8m stationary

    private val highSpeedThreshold = 30f   // km/h — lowered from 35: catches real crashes at city speeds
    private val decelerationThreshold = 20f // km/h — lowered from 25: GPS doppler reads ~10% low

    private data class SpeedReading(val speed: Float, val timestamp: Long, val location: Location?)

    private val speedBuffer = mutableListOf<SpeedReading>()

    private var detectionState = DetectionState.MONITORING
    private var highSpeedDetected = false
    private var suddenDecelerationDetected = false
    private var stopConfirmed = false

    private var crashCandidateTime: Long = 0L
    private var crashCandidateLocation: Location? = null
    private var preDecelSpeed: Float = 0f
    private var postDecelSpeed: Float = 0f

    private var stopConfirmationStartTime: Long = 0L
    private var stopConfirmationStartLocation: Location? = null
    private var totalDistanceMoved: Float = 0f
    private var lastMonitoringLocation: Location? = null
    private var maxSpeedDuringConfirmation: Float = 0f

    private var isAccidentAlertActive = false

    enum class DetectionState {
        MONITORING, HIGH_SPEED_DETECTED, DECELERATION_DETECTED,
        STOP_CONFIRMATION, ACCIDENT_CONFIRMED
    }

    fun processGPSUpdate(speed: Float, location: Location?): Boolean {
        // Reject physically impossible speeds — GPS errors can produce spikes
        if (speed > 250f) return isAccidentAlertActive

        val currentTime = System.currentTimeMillis()
        addSpeedToBuffer(speed, currentTime, location)

        when (detectionState) {
            DetectionState.MONITORING -> checkForHighSpeed(speed)

            DetectionState.HIGH_SPEED_DETECTED -> {
                if (speed > highSpeedThreshold) preDecelSpeed = speed
                checkForSuddenDeceleration(speed, currentTime, location)
            }

            DetectionState.DECELERATION_DETECTED -> {
                // Only start the confirmation window — do NOT also monitor on this same tick.
                // startStopConfirmation() transitions state to STOP_CONFIRMATION; the next
                // GPS update will enter the STOP_CONFIRMATION branch and begin monitoring.
                startStopConfirmation(currentTime, location)
            }

            DetectionState.STOP_CONFIRMATION ->
                handleConfirmationResult(monitorStopConfirmation(speed, currentTime, location))

            DetectionState.ACCIDENT_CONFIRMED -> { /* waiting for user */ }
        }

        return isAccidentAlertActive
    }

    private fun handleConfirmationResult(result: Boolean?) {
        when (result) {
            true  -> confirmAccident()
            false -> cancelAccidentDetection("conditions not met")
            null  -> { }
        }
    }

    private fun addSpeedToBuffer(speed: Float, timestamp: Long, location: Location?) {
        speedBuffer.add(SpeedReading(speed, timestamp, location))
        if (speedBuffer.size > SPEED_BUFFER_SIZE) speedBuffer.removeAt(0)
    }

    private fun checkForHighSpeed(currentSpeed: Float) {
        if (currentSpeed > highSpeedThreshold) {
            highSpeedDetected = true
            preDecelSpeed = currentSpeed
            detectionState = DetectionState.HIGH_SPEED_DETECTED
            Log.d(TAG, "Layer 1: High speed ${String.format("%.1f", currentSpeed)} km/h")
        }
    }

    private fun checkForSuddenDeceleration(currentSpeed: Float, currentTime: Long, location: Location?) {
        if (speedBuffer.size < 2) return

        // Look at the highest speed reading within the extended window.
        // Using the full window (5s) handles gradual real-world braking like
        // 60 → 42 → 18 → 0 spread across 3-4 GPS ticks.
        val windowStart = currentTime - DECELERATION_TIME_WINDOW
        val peakReading = speedBuffer
            .filter { it.timestamp in windowStart until (currentTime - 50) }
            .maxByOrNull { it.speed }
            ?: return

        val speedDrop = peakReading.speed - currentSpeed
        val timeDiff = currentTime - peakReading.timestamp

        Log.d(TAG, "Decel check: ${String.format("%.1f", peakReading.speed)} -> " +
                "${String.format("%.1f", currentSpeed)} km/h in ${timeDiff}ms (drop ${String.format("%.1f", speedDrop)})")

        // Removed the strict currentSpeed < LOW_SPEED_THRESHOLD requirement here.
        // The stop confirmation phase (5s window) already verifies the vehicle
        // has come to a full stop — requiring it here caused misses when the
        // final GPS tick landed at a non-zero speed (e.g. 2-3 km/h GPS drift).
        if (peakReading.speed > highSpeedThreshold &&
            speedDrop > decelerationThreshold) {

            suddenDecelerationDetected = true
            detectionState = DetectionState.DECELERATION_DETECTED
            crashCandidateTime = currentTime
            crashCandidateLocation = location
            preDecelSpeed = peakReading.speed
            postDecelSpeed = currentSpeed

            Log.d(TAG, "Layer 2: Sudden decel ${String.format("%.1f", preDecelSpeed)} -> " +
                    "${String.format("%.1f", postDecelSpeed)} km/h in ${timeDiff}ms")
        }
    }

    private fun startStopConfirmation(currentTime: Long, location: Location?) {
        detectionState = DetectionState.STOP_CONFIRMATION
        stopConfirmationStartTime = currentTime
        stopConfirmationStartLocation = location
        lastMonitoringLocation = location
        totalDistanceMoved = 0f
        maxSpeedDuringConfirmation = 0f
        Log.d(TAG, "=== STOP CONFIRMATION STARTED — monitoring 5 seconds ===")
    }

    private fun monitorStopConfirmation(currentSpeed: Float, currentTime: Long, location: Location?): Boolean? {
        val elapsedTime = currentTime - stopConfirmationStartTime

        if (currentSpeed > maxSpeedDuringConfirmation) maxSpeedDuringConfirmation = currentSpeed

        if (location != null) {
            if (lastMonitoringLocation != null) {
                totalDistanceMoved += lastMonitoringLocation!!.distanceTo(location)
            }
            lastMonitoringLocation = location
        }

        Log.d(TAG, "Confirmation: ${String.format("%.1f", elapsedTime / 1000f)}s/5s " +
                "speed=${String.format("%.1f", currentSpeed)} dist=${String.format("%.1f", totalDistanceMoved)}m")

        if (totalDistanceMoved >= STOP_CONFIRMATION_DISTANCE) {
            Log.d(TAG, "Vehicle moved ${String.format("%.1f", totalDistanceMoved)}m — cancelling")
            return false
        }

        if (elapsedTime < STOP_CONFIRMATION_DURATION) return null

        return if (maxSpeedDuringConfirmation < LOW_SPEED_THRESHOLD &&
            totalDistanceMoved < STOP_CONFIRMATION_DISTANCE) {
            stopConfirmed = true
            Log.d(TAG, "Stop confirmed: maxSpeed=${String.format("%.1f", maxSpeedDuringConfirmation)} " +
                    "dist=${String.format("%.1f", totalDistanceMoved)}m")
            true
        } else {
            Log.d(TAG, "Conditions not met after 5s — cancelling")
            false
        }
    }

    private fun confirmAccident() {
        if (isAccidentAlertActive) return
        if (!highSpeedDetected || !suddenDecelerationDetected || !stopConfirmed) return

        isAccidentAlertActive = true
        detectionState = DetectionState.ACCIDENT_CONFIRMED
        listener?.onAccidentDetected(preDecelSpeed, postDecelSpeed)

        Log.d(TAG, "=== ACCIDENT CONFIRMED ===")
        Log.d(TAG, "Speed: ${String.format("%.1f", preDecelSpeed)} -> ${String.format("%.1f", postDecelSpeed)} km/h")
        Log.d(TAG, "Drop: ${String.format("%.1f", preDecelSpeed - postDecelSpeed)} km/h")
        Log.d(TAG, "Max during confirmation: ${String.format("%.1f", maxSpeedDuringConfirmation)} km/h")
        Log.d(TAG, "Distance moved: ${String.format("%.1f", totalDistanceMoved)} m")
    }

    private fun cancelAccidentDetection(reason: String) {
        Log.d(TAG, "Detection cancelled — $reason. Returning to MONITORING.")
        resetDetection()
    }

    fun isAccidentConfirmed(): Boolean = isAccidentAlertActive
    fun getCrashLocation(): Location? = crashCandidateLocation

    fun getCrashDetails(): Map<String, Any> = mapOf(
        "preDecelSpeed"  to preDecelSpeed,
        "postDecelSpeed" to postDecelSpeed,
        "speedDrop"      to (preDecelSpeed - postDecelSpeed),
        "crashTime"      to crashCandidateTime,
        "detectionState" to detectionState.name
    )

    fun getDetectionState(): String = """
        State: $detectionState
        Buffer: ${speedBuffer.size}/$SPEED_BUFFER_SIZE
        HighSpeed: $highSpeedDetected (${String.format("%.1f", preDecelSpeed)} km/h)
        Decel: $suddenDecelerationDetected (drop ${String.format("%.1f", preDecelSpeed - postDecelSpeed)} km/h)
        StopConfirmed: $stopConfirmed
        AlertActive: $isAccidentAlertActive
    """.trimIndent()

    fun resetAlert() {
        Log.d(TAG, "Alert reset")
        isAccidentAlertActive = false
        speedBuffer.clear()   // clear stale readings so next detection starts fresh
        resetDetection()
    }

    fun reset() {
        speedBuffer.clear()
        resetDetection()
        isAccidentAlertActive = false
        Log.d(TAG, "AccidentDetector fully reset")
    }

    private fun resetDetection() {
        detectionState = DetectionState.MONITORING
        highSpeedDetected = false
        suddenDecelerationDetected = false
        stopConfirmed = false
        crashCandidateTime = 0L
        crashCandidateLocation = null
        preDecelSpeed = 0f
        postDecelSpeed = 0f
        stopConfirmationStartTime = 0L
        stopConfirmationStartLocation = null
        totalDistanceMoved = 0f
        lastMonitoringLocation = null
        maxSpeedDuringConfirmation = 0f
    }
}
