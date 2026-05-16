package com.example.accident_detection3

import android.app.Application
import com.example.accident_detection3.firebase.FirebaseManager

class AccidentDetectionApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseManager.initialize(this)
    }
}
