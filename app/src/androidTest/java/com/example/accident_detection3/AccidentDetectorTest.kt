package com.example.accident_detection3

import android.location.Location
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.accident_detection3.detector.AccidentDetector
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simulation-based tests for AccidentDetector.
 *
 * Each test runs a named scenario through the real detector logic
 * and asserts whether an alert should or should not fire.
 *
 * Scenarios:
 *  SIM-1  Real crash at highway speed (100 km/h)
 *  SIM-2  Real crash at city speed (60 km/h)
 *  SIM-3  Gradual braking to stop — NOT a crash
 *  SIM-4  Hard brake but drives away — NOT a crash
 *  SIM-5  Speed too low to qualify — NOT a crash
 *  SIM-6  Crash → user dismisses → second crash fires again
 *  SIM-7  Crash confirmed exactly once (no double-fire)
 *  SIM-8  Reset mid-sequence — no alert
 */
@RunWith(AndroidJUnit4::class)
class AccidentDetectorTest {

    private lateinit var detector: AccidentDetector
    private var alertFired = false
    private var speedBefore = 0f
    private var speedAfter = 0f

    @Before
    fun setup() {
        detector = AccidentDetector()
        alertFired = false
        speedBefore = 0f
        speedAfter = 0f
        detector.listener = object : AccidentDetector.AccidentListener {
            override fun onAccidentDetected(sb: Float, sa: Float) {
                alertFired = true
                speedBefore = sb
                speedAfter = sa
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun loc(lat: Double = 12.9716, lon: Double = 77.5946): Location =
        Location("gps").apply {
            latitude = lat
            longitude = lon
            accuracy = 3f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }

    /** Feed one speed tick and wait [ms] milliseconds. */
    private fun tick(speed: Float, lat: Double = 12.9716, lon: Double = 77.5946, ms: Long = 1050) {
        detector.processGPSUpdate(speed, loc(lat, lon))
        Thread.sleep(ms)
    }

    /**
     * Run a full crash simulation:
     *   ramp up to [cruiseSpeed] → cruise 2 ticks → drop to 0 → stay stopped [stoppedTicks] ticks
     */
    private fun runCrashSimulation(cruiseSpeed: Float, stoppedTicks: Int = 6) {
        val baseLat = 12.9716
        val baseLon = 77.5946
        val q = cruiseSpeed / 4f

        // Ramp up
        tick(q,       baseLat + 0.00015, baseLon)
        tick(q * 2f,  baseLat + 0.00030, baseLon)
        tick(q * 3f,  baseLat + 0.00045, baseLon)
        tick(cruiseSpeed, baseLat + 0.00060, baseLon)
        tick(cruiseSpeed, baseLat + 0.00075, baseLon)
        tick(cruiseSpeed, baseLat + 0.00090, baseLon)

        // Crash
        val crashLat = baseLat + 0.00090
        tick(0f, crashLat, baseLon)

        // Stay stopped
        repeat(stoppedTicks) { tick(0f, crashLat, baseLon) }
    }

    // ── SIM-1: Highway crash (100 km/h) ──────────────────────────────────────

    @Test
    fun sim1_highwayCrash_alertFires() {
        runCrashSimulation(cruiseSpeed = 100f)

        assertTrue("SIM-1: Highway crash should trigger alert", alertFired)
        assertTrue("SIM-1: Speed before should be ~100 km/h", speedBefore >= 90f)
        assertEquals("SIM-1: Speed after crash should be 0", 0f, speedAfter)
    }

    // ── SIM-2: City speed crash (60 km/h) ────────────────────────────────────

    @Test
    fun sim2_cityCrash_alertFires() {
        runCrashSimulation(cruiseSpeed = 60f)

        assertTrue("SIM-2: City crash should trigger alert", alertFired)
        assertTrue("SIM-2: Speed before should be ~60 km/h", speedBefore >= 50f)
        assertEquals("SIM-2: Speed after crash should be 0", 0f, speedAfter)
    }

    // ── SIM-3: Gradual braking to stop — NOT a crash ─────────────────────────

    @Test
    fun sim3_gradualBraking_noAlert() {
        val baseLat = 12.9716
        val baseLon = 77.5946

        // Gradual deceleration — speed drops slowly, never a sudden >25 km/h drop in 2s
        tick(60f, baseLat + 0.00015, baseLon)
        tick(50f, baseLat + 0.00030, baseLon)
        tick(40f, baseLat + 0.00045, baseLon)
        tick(30f, baseLat + 0.00060, baseLon)
        tick(20f, baseLat + 0.00075, baseLon)
        tick(10f, baseLat + 0.00090, baseLon)
        tick(5f,  baseLat + 0.00095, baseLon)
        tick(0f,  baseLat + 0.00095, baseLon)
        repeat(6) { tick(0f, baseLat + 0.00095, baseLon) }

        assertFalse("SIM-3: Gradual braking should NOT trigger alert", alertFired)
    }

    // ── SIM-4: Hard brake but drives away — NOT a crash ──────────────────────

    @Test
    fun sim4_hardBrakeThenDrivesAway_noAlert() {
        val baseLat = 12.9716
        val baseLon = 77.5946

        // Build speed
        tick(60f, baseLat + 0.00015, baseLon)
        tick(60f, baseLat + 0.00030, baseLon)
        tick(60f, baseLat + 0.00045, baseLon)

        // Hard brake to 0
        tick(0f, baseLat + 0.00045, baseLon)

        // Vehicle drives away — moves >5m per tick across 3 ticks
        var movingLat = baseLat + 0.00045
        repeat(3) {
            movingLat += 0.0001  // ~11m per tick
            tick(3f, movingLat, baseLon)
        }

        // Then stops
        repeat(4) { tick(0f, movingLat, baseLon) }

        assertFalse("SIM-4: Hard brake then driving away should NOT trigger alert", alertFired)
    }

    // ── SIM-5: Speed too low (under 35 km/h threshold) — NOT a crash ─────────

    @Test
    fun sim5_speedBelowThreshold_noAlert() {
        val baseLat = 12.9716
        val baseLon = 77.5946

        // Never exceeds 35 km/h
        tick(20f, baseLat + 0.00015, baseLon)
        tick(30f, baseLat + 0.00030, baseLon)
        tick(30f, baseLat + 0.00045, baseLon)
        tick(0f,  baseLat + 0.00045, baseLon)
        repeat(6) { tick(0f, baseLat + 0.00045, baseLon) }

        assertFalse("SIM-5: Speed below threshold should NOT trigger alert", alertFired)
    }

    // ── SIM-6: Crash → dismiss → second crash fires again ────────────────────

    @Test
    fun sim6_crashDismissedThenSecondCrash_bothFire() {
        // First crash
        runCrashSimulation(cruiseSpeed = 65f)
        assertTrue("SIM-6: First crash should fire alert", alertFired)

        // User taps "I'm Safe" — resets alert
        alertFired = false
        detector.resetAlert()

        // Second crash
        runCrashSimulation(cruiseSpeed = 65f)
        assertTrue("SIM-6: Second crash should fire alert after dismiss", alertFired)
    }

    // ── SIM-7: Alert fires exactly once ──────────────────────────────────────

    @Test
    fun sim7_alertFiresExactlyOnce() {
        var fireCount = 0
        detector.listener = object : AccidentDetector.AccidentListener {
            override fun onAccidentDetected(sb: Float, sa: Float) { fireCount++ }
        }

        runCrashSimulation(cruiseSpeed = 65f, stoppedTicks = 10)

        assertEquals("SIM-7: Alert should fire exactly once", 1, fireCount)
    }

    // ── SIM-8: Reset mid-sequence — no alert ─────────────────────────────────

    @Test
    fun sim8_resetMidSequence_noAlert() {
        val baseLat = 12.9716
        val baseLon = 77.5946

        // Build speed and crash
        tick(65f, baseLat + 0.00015, baseLon)
        tick(65f, baseLat + 0.00030, baseLon)
        tick(65f, baseLat + 0.00045, baseLon)
        tick(0f,  baseLat + 0.00045, baseLon)
        Thread.sleep(500)

        // Reset before confirmation window completes
        detector.reset()

        // Continue stopped ticks — should not fire
        repeat(7) { tick(0f, baseLat + 0.00045, baseLon) }

        assertFalse("SIM-8: No alert after mid-sequence reset", alertFired)
        assertFalse("SIM-8: isAccidentConfirmed should be false", detector.isAccidentConfirmed())
    }
}
