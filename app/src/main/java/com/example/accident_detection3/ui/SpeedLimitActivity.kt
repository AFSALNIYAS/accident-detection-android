package com.example.accident_detection3.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.accident_detection3.data.SpeedLimitPrefs
import com.example.accident_detection3.data.UserPrefs
import com.example.accident_detection3.databinding.ActivitySpeedLimitBinding

class SpeedLimitActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySpeedLimitBinding
    private lateinit var speedLimitPrefs: SpeedLimitPrefs
    private lateinit var userPrefs: UserPrefs
    private var isEditMode = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeedLimitBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        speedLimitPrefs = SpeedLimitPrefs(this)
        userPrefs = UserPrefs(this)

        isEditMode = intent.getBooleanExtra("EDIT_MODE", false)
        
        loadSpeedLimit()
        setupListeners()
        updateButtonVisibility()
    }
    
    private fun updateButtonVisibility() {
        if (isEditMode) {
            // In edit mode, hide Next button and style Back as a filled Save button
            binding.btnNext.visibility = android.view.View.GONE
            binding.btnBack.text = "Save"
            binding.btnBack.strokeWidth = 0
            binding.btnBack.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, com.example.accident_detection3.R.color.accent)
            )
        } else {
            // In setup mode, show both buttons
            binding.btnNext.visibility = android.view.View.VISIBLE
            binding.btnBack.text = "Back"
        }
    }
    
    private fun loadSpeedLimit() {
        binding.etSpeedLimit.setText(speedLimitPrefs.speedLimit.toString())
    }
    
    private fun setupListeners() {
        binding.btnNext.setOnClickListener {
            if (saveSpeedLimit()) {
                // Mark setup as complete
                userPrefs.isSetupComplete = true
                
                val intent = Intent(this, SpeedMonitoringActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
        
        binding.btnBack.setOnClickListener {
            if (isEditMode) {
                // In edit mode, save and close
                if (saveSpeedLimit()) {
                    finish()
                }
            } else {
                // In setup mode, just go back
                finish()
            }
        }
    }
    
    private fun saveSpeedLimit(): Boolean {
        val speedLimit = binding.etSpeedLimit.text.toString().toFloatOrNull()
        if (speedLimit != null && speedLimit > 0 && speedLimit <= 300) {
            speedLimitPrefs.speedLimit = speedLimit
            Toast.makeText(this, "Speed limit saved", Toast.LENGTH_SHORT).show()
            return true
        } else {
            Toast.makeText(this, "Please enter a valid speed limit (1–300 km/h)", Toast.LENGTH_SHORT).show()
            return false
        }
    }
}
