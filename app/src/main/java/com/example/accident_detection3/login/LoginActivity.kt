package com.example.accident_detection3.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.accident_detection3.data.PhoneAuthPrefs
import com.example.accident_detection3.databinding.ActivityLoginBinding
import com.example.accident_detection3.ui.UserInfoActivity

class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var phoneAuthPrefs: PhoneAuthPrefs
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        phoneAuthPrefs = PhoneAuthPrefs(this)

        // API 31+: textCursorDrawable XML attr is ignored, must set programmatically
        // Long press on title to clear all data (for testing)
        binding.root.findViewById<android.widget.TextView>(
            resources.getIdentifier("tvWelcome", "id", packageName)
        )?.setOnLongClickListener {
            clearAllData()
            true
        }
        
        binding.btnLogin.setOnClickListener {
            val phoneNumber = binding.etPhoneNumber.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            
            if (validateInput(phoneNumber, password)) {
                loginUser(phoneNumber, password)
            }
        }
        
        binding.tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
    
    private fun clearAllData() {
        getSharedPreferences("accident_detection_prefs_auth", MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        Toast.makeText(this, "All login data cleared", Toast.LENGTH_SHORT).show()
    }
    
    private fun validateInput(phoneNumber: String, password: String): Boolean {
        // Remove spaces and country code for validation
        val cleanPhone = phoneNumber.replace(" ", "").replace("+91", "").replace("+", "")
        
        if (cleanPhone.isEmpty()) {
            binding.etPhoneNumber.error = "Phone number is required"
            binding.btnLogin.isEnabled = true
            return false
        }
        if (cleanPhone.length != 10) {
            binding.etPhoneNumber.error = "Phone number must be 10 digits"
            binding.btnLogin.isEnabled = true
            return false
        }
        if (!cleanPhone.all { it.isDigit() }) {
            binding.etPhoneNumber.error = "Phone number must contain only digits"
            binding.btnLogin.isEnabled = true
            return false
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            binding.btnLogin.isEnabled = true
            return false
        }
        return true
    }
    
    private fun loginUser(phoneNumber: String, password: String) {
        binding.btnLogin.isEnabled = false
        
        // Clean the phone number and ensure it has country code
        val cleanPhone = phoneNumber.replace(" ", "").replace("+91", "").replace("+", "")
        val fullPhoneNumber = "+91$cleanPhone"
        
        if (phoneAuthPrefs.loginUser(fullPhoneNumber, password)) {
            Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, com.example.accident_detection3.MainActivity::class.java))
            finish()
        } else {
            binding.btnLogin.isEnabled = true
            Toast.makeText(
                this,
                "Login failed: Invalid phone number or password",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
