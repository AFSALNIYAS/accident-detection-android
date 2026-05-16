package com.example.accident_detection3.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.accident_detection3.MainActivity
import com.example.accident_detection3.data.PhoneAuthPrefs
import com.example.accident_detection3.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var phoneAuthPrefs: PhoneAuthPrefs
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        phoneAuthPrefs = PhoneAuthPrefs(this)

        binding.btnRegister.setOnClickListener {
            val phoneNumber = binding.etPhoneNumber.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()
            
            if (validateInput(phoneNumber, password, confirmPassword)) {
                registerUser(phoneNumber, password)
            }
        }
        
        binding.tvBackToLogin.setOnClickListener {
            finish()
        }
    }
    
    private fun validateInput(phoneNumber: String, password: String, confirmPassword: String): Boolean {
        // Remove spaces and country code for validation
        val cleanPhone = phoneNumber.replace(" ", "").replace("+91", "").replace("+", "")
        
        if (cleanPhone.isEmpty()) {
            binding.etPhoneNumber.error = "Phone number is required"
            return false
        }
        if (cleanPhone.length != 10) {
            binding.etPhoneNumber.error = "Phone number must be 10 digits"
            return false
        }
        if (!cleanPhone.all { it.isDigit() }) {
            binding.etPhoneNumber.error = "Phone number must contain only digits"
            return false
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            return false
        }
        if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            return false
        }
        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            return false
        }
        return true
    }
    
    private fun registerUser(phoneNumber: String, password: String) {
        binding.btnRegister.isEnabled = false
        
        // Clean the phone number and ensure it has country code
        val cleanPhone = phoneNumber.replace(" ", "").replace("+91", "").replace("+", "")
        val fullPhoneNumber = "+91$cleanPhone"
        
        if (phoneAuthPrefs.registerUser(fullPhoneNumber, password)) {
            Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
            // Auto-login so the user doesn't have to type credentials again
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        } else {
            binding.btnRegister.isEnabled = true
            Toast.makeText(
                this,
                "Registration failed: Phone number already registered",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
