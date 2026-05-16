package com.example.accident_detection3.util

import android.location.Location
import android.os.SystemClock

/**
 * Mock location provider for testing speed monitoring without actual GPS
 */
object MockLocationProvider {
    
    private var mockEnabled = false
    private var currentMockSpeed = 0f // km/h
    private var mockLatitude = 37.7749 // San Francisco
    private var mockLongitude = -122.4194
    
    fun enableMockMode(enabled: Boolean) {
        mockEnabled = enabled
    }
    
    fun isMockEnabled(): Boolean = mockEnabled
    
    fun setMockSpeed(speedKmh: Float) {
        currentMockSpeed = speedKmh
    }
    
    fun getMockSpeed(): Float = currentMockSpeed
    
    fun createMockLocation(): Location {
        return Location("mock").apply {
            latitude = mockLatitude
            longitude = mockLongitude
            
            // Set speed in m/s (convert from km/h)
            speed = currentMockSpeed / 3.6f
            
            // Set other required fields
            accuracy = 10f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            
            // Move location slightly to simulate movement
            mockLatitude += 0.0001
            mockLongitude += 0.0001
        }
    }
    
    /**
     * Simulate different driving scenarios
     */
    fun simulateWalking() {
        setMockSpeed(5f) // 5 km/h
    }
    
    fun simulateCycling() {
        setMockSpeed(20f) // 20 km/h
    }
    
    fun simulateDriving() {
        setMockSpeed(60f) // 60 km/h
    }
    
    fun simulateHighway() {
        setMockSpeed(100f) // 100 km/h
    }
    
    fun simulateStationary() {
        setMockSpeed(0f)
    }
}
