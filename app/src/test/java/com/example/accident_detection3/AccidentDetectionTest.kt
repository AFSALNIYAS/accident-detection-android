package com.example.accident_detection3

import com.example.accident_detection3.detector.AccidentDetector
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Comprehensive tests for accident detection logic
 */
class AccidentDetectionTest {
    
    private lateinit var detector: AccidentDetector
    
    @Before
    fun setup() {
        detector = AccidentDetector()
    }
    
    @Test
    fun testLayer1_LowSpeedFiltering() {
        // Speed < 15 km/h should not trigger detection
        val result = detector.checkForSuddenDeceleration(10f)
        assertFalse("Low speed should not trigger detection", result)
    }
    
    @Test
    fun testLayer2_SuddenDeceleration_HighToLow() {
        // Simulate high speed
        detector.checkForSuddenDeceleration(50f)
        Thread.sleep(1000) // Wait 1 second
        
        // Sudden drop to low speed
        val result = detector.checkForSuddenDeceleration(3f)
        assertTrue("Sudden deceleration should be detected", result)
    }
    
    @Test
    fun testLayer2_NoDetection_SlowDeceleration() {
        // Gradual slowdown should not trigger
        detector.checkForSuddenDeceleration(50f)
        Thread.sleep(1000)
        detector.checkForSuddenDeceleration(40f)
        Thread.sleep(1000)
        detector.checkForSuddenDeceleration(30f)
        Thread.sleep(1000)
        val result = detector.checkForSuddenDeceleration(20f)
        
        assertFalse("Gradual deceleration should not trigger", result)
    }
    
    @Test
    fun testLayer3_ImpactDetection_HighGForce() {
        // Simulate high G-force impact (3g)
        val result = detector.checkForImpact(0f, 0f, 29.43f) // 3g in z-axis
        assertTrue("High G-force should trigger impact detection", result)
    }
    
    @Test
    fun testLayer3_NoImpact_LowGForce() {
        // Normal acceleration (1.5g)
        val result = detector.checkForImpact(0f, 0f, 14.715f)
        assertFalse("Low G-force should not trigger impact", result)
    }
    
    @Test
    fun testLayer4_StillnessDetection() {
        // Start monitoring stillness
        detector.checkForSuddenDeceleration(50f)
        Thread.sleep(1000)
        detector.checkForSuddenDeceleration(3f) // Triggers candidate crash
        
        // Simulate stillness for 5+ seconds
        val startTime = System.currentTimeMillis()
        var stillnessConfirmed = false
        
        while (System.currentTimeMillis() - startTime < 6000) {
            stillnessConfirmed = detector.checkForStillness(2f, 0.3f)
            Thread.sleep(100)
        }
        
        assertTrue("Stillness should be confirmed after 5 seconds", stillnessConfirmed)
    }
    
    @Test
    fun testScoringModel_HighSpeedCrash() {
        // Simulate high-speed crash scenario
        detector.checkForSuddenDeceleration(60f) // High speed
        Thread.sleep(1000)
        detector.checkForSuddenDeceleration(2f) // Sudden stop
        detector.checkForImpact(0f, 0f, 35f) // High G-force (3.5g)
        
        // Wait for stillness
        Thread.sleep(5500)
        detector.checkForStillness(1f, 0.2f)
        
        val score = detector.calculateAccidentScore()
        assertTrue("High-speed crash should have score >= 7", score >= 7)
    }
    
    @Test
    fun testScoringModel_ModerateCrash() {
        // Simulate moderate crash
        detector.checkForSuddenDeceleration(45f)
        Thread.sleep(1000)
        detector.checkForSuddenDeceleration(3f)
        detector.checkForImpact(0f, 0f, 27f) // 2.75g
        
        Thread.sleep(5500)
        detector.checkForStillness(2f, 0.3f)
        
        val score = detector.calculateAccidentScore()
        assertTrue("Moderate crash should have score >= 7", score >= 7)
    }
    
    @Test
    fun testScoringModel_FalsePositive_HardBraking() {
        // Hard braking but not a crash
        detector.checkForSuddenDeceleration(50f)
        Thread.sleep(1000)
        detector.checkForSuddenDeceleration(15f) // Still moving
        detector.checkForImpact(0f, 0f, 20f) // Moderate G-force (2g)
        
        val score = detector.calculateAccidentScore()
        assertTrue("Hard braking should have score < 7", score < 7)
    }
    
    @Test
    fun testFinalConfirmation_AllLayersSatisfied() {
        // Simulate complete accident scenario
        detector.checkForSuddenDeceleration(55f)
        Thread.sleep(1500)
        detector.checkForSuddenDeceleration(2f) // Layer 2: Sudden deceleration
        detector.checkForImpact(0f, 0f, 30f) // Layer 3: Impact
        
        // Layer 4: Stillness
        Thread.sleep(5500)
        detector.checkForStillness(1f, 0.2f)
        
        val confirmed = detector.confirmAccident()
        assertTrue("Accident should be confirmed when all layers satisfied", confirmed)
    }
    
    @Test
    fun testFinalConfirmation_MissingLayer() {
        // Missing impact layer
        detector.checkForSuddenDeceleration(55f)
        Thread.sleep(1500)
        detector.checkForSuddenDeceleration(2f) // Layer 2 only
        
        Thread.sleep(5500)
        detector.checkForStillness(1f, 0.2f)
        
        val confirmed = detector.confirmAccident()
        assertFalse("Accident should not be confirmed without all layers", confirmed)
    }
    
    @Test
    fun testResetAlert() {
        // Trigger detection
        detector.checkForSuddenDeceleration(50f)
        Thread.sleep(1000)
        detector.checkForSuddenDeceleration(2f)
        detector.checkForImpact(0f, 0f, 30f)
        
        // Reset
        detector.resetAlert()
        
        // Should not confirm after reset
        val confirmed = detector.confirmAccident()
        assertFalse("Alert should be reset", confirmed)
    }
    
    @Test
    fun testDetectionState() {
        detector.checkForSuddenDeceleration(50f)
        Thread.sleep(1000)
        detector.checkForSuddenDeceleration(2f)
        detector.checkForImpact(0f, 0f, 30f)
        
        val state = detector.getDetectionState()
        assertNotNull("Detection state should be available", state)
        assertTrue("State should contain detection info", state.contains("Detection State"))
    }
}
