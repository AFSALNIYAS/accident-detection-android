package com.example.accident_detection3

import android.location.Location
import org.junit.Test
import org.junit.Assert.*

/**
 * Speed monitoring logic tests
 */
class SpeedMonitoringTest {
    
    @Test
    fun testSpeedConversion_MetersPerSecondToKmh() {
        // 10 m/s should equal 36 km/h
        val speedMps = 10f
        val speedKmh = speedMps * 3.6f
        assertEquals(36f, speedKmh, 0.01f)
    }
    
    @Test
    fun testSpeedConversion_ZeroSpeed() {
        val speedMps = 0f
        val speedKmh = speedMps * 3.6f
        assertEquals(0f, speedKmh, 0.01f)
    }
    
    @Test
    fun testSpeedSmoothing() {
        val speedHistory = mutableListOf(10f, 12f, 11f, 13f, 12f)
        val average = speedHistory.average().toFloat()
        assertEquals(11.6f, average, 0.01f)
    }
    
    @Test
    fun testDistanceCalculation() {
        // Moving 100 meters in 10 seconds = 10 m/s = 36 km/h
        val distance = 100f // meters
        val time = 10f // seconds
        val speedMps = distance / time
        val speedKmh = speedMps * 3.6f
        assertEquals(36f, speedKmh, 0.01f)
    }
    
    @Test
    fun testSpeedHistoryLimit() {
        val speedHistory = mutableListOf<Float>()
        val maxSize = 5
        
        // Add 10 items
        for (i in 1..10) {
            speedHistory.add(i.toFloat())
            if (speedHistory.size > maxSize) {
                speedHistory.removeAt(0)
            }
        }
        
        // Should only have last 5 items
        assertEquals(5, speedHistory.size)
        assertEquals(6f, speedHistory[0], 0.01f)
        assertEquals(10f, speedHistory[4], 0.01f)
    }
}
