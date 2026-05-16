package com.example.accident_detection3

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.accident_detection3.data.UserPrefs
import com.example.accident_detection3.ui.SpeedMonitoringActivity
import com.example.accident_detection3.ui.UserInfoActivity

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val userPrefs = UserPrefs(this)
        
        // Check if user has completed initial setup
        val intent = if (userPrefs.isSetupComplete) {
            // Setup complete, go directly to monitoring screen
            Intent(this, SpeedMonitoringActivity::class.java)
        } else {
            // First time setup, go to user info
            Intent(this, UserInfoActivity::class.java)
        }
        
        startActivity(intent)
        finish()
    }
}
