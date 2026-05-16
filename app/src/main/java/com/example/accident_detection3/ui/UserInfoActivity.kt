package com.example.accident_detection3.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.accident_detection3.data.UserPrefs
import com.example.accident_detection3.databinding.ActivityUserInfoBinding

class UserInfoActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityUserInfoBinding
    private lateinit var userPrefs: UserPrefs
    private var isEditMode = false

    private val bloodGroups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        isEditMode = intent.getBooleanExtra("EDIT_MODE", false)
        userPrefs = UserPrefs(this)

        setupBloodGroupDropdown()
        loadUserData()
        setupListeners()
        updateButtonVisibility()
    }

    private fun setupBloodGroupDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bloodGroups)
        binding.actvBloodGroup.setAdapter(adapter)
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
    
    private fun loadUserData() {
        binding.etUserName.setText(userPrefs.userName)
        binding.etMobileNumber.setText(userPrefs.mobileNumber)
        binding.actvBloodGroup.setText(userPrefs.bloodGroup, false)
    }
    
    private fun setupListeners() {
        binding.btnNext.setOnClickListener {
            if (validateAndSaveUserData()) {
                startActivity(Intent(this, EmergencyContactsActivity::class.java))
            }
        }
        
        binding.btnBack.setOnClickListener {
            if (isEditMode) {
                // In edit mode, save and close
                if (validateAndSaveUserData()) {
                    finish()
                }
            } else {
                // In setup mode, just go back
                finish()
            }
        }
    }
    
    private fun validateAndSaveUserData(): Boolean {
        val name = binding.etUserName.text.toString().trim()
        val mobile = binding.etMobileNumber.text.toString().trim()
        val bloodGroup = binding.actvBloodGroup.text.toString().trim()
        
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (mobile.isEmpty()) {
            Toast.makeText(this, "Please enter your mobile number", Toast.LENGTH_SHORT).show()
            return false
        }

        if (bloodGroup.isNotEmpty() && bloodGroup !in bloodGroups) {
            Toast.makeText(this, "Please select a valid blood group from the list", Toast.LENGTH_SHORT).show()
            binding.actvBloodGroup.error = "Select from list"
            return false
        }
        
        userPrefs.userName = name
        userPrefs.mobileNumber = mobile
        userPrefs.bloodGroup = bloodGroup
        Toast.makeText(this, "User information saved", Toast.LENGTH_SHORT).show()
        return true
    }
}
