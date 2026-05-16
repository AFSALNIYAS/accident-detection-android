package com.example.accident_detection3.emergency

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.accident_detection3.data.EmergencyContactManager
import com.example.accident_detection3.data.UserPrefs
import com.example.accident_detection3.databinding.ActivityEmergencyInfoBinding
import com.example.accident_detection3.util.Constants

class EmergencyInfoActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityEmergencyInfoBinding
    private lateinit var userPrefs: UserPrefs
    private lateinit var contactManager: EmergencyContactManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Show over lock screen and keep screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        userPrefs = UserPrefs(this)
        contactManager = EmergencyContactManager(this)
        
        displayEmergencyInfo()
        
        binding.btnDismiss.setOnClickListener {
            finish()
        }
    }
    
    private fun displayEmergencyInfo() {
        val latitude = intent.getDoubleExtra(Constants.EXTRA_LATITUDE, 0.0)
        val longitude = intent.getDoubleExtra(Constants.EXTRA_LONGITUDE, 0.0)
        val speed = intent.getFloatExtra(Constants.EXTRA_SPEED, 0f)

        // Display user info
        val name = userPrefs.userName.ifBlank { "—" }
        binding.tvUserName.text = "Name: $name"
        binding.tvMobileNumber.text = "Mobile: ${userPrefs.mobileNumber.ifBlank { "—" }}"
        binding.tvBloodGroup.text = "Blood Group: ${userPrefs.bloodGroup.ifBlank { "—" }}"

        // Display location + speed
        val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"
        val speedText = if (speed > 0f) " • Speed: ${"%.1f".format(speed)} km/h" else ""
        binding.tvLocation.text = "Location: $mapsLink$speedText"

        val contacts = contactManager.getContacts()
        if (contacts.isNotEmpty()) {
            val contactsText = contacts.joinToString("\n") { "${it.name}: ${it.phoneNumber}" }
            binding.tvEmergencyContacts.text = "Emergency Contacts:\n$contactsText\n\nEmergency SMS has been sent to all contacts."
        } else {
            binding.tvEmergencyContacts.text = "No emergency contacts configured."
        }
    }
}
