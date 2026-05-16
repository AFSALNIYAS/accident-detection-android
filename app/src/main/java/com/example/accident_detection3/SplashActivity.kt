package com.example.accident_detection3

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.accident_detection3.data.PhoneAuthPrefs
import com.example.accident_detection3.firebase.FirebaseConnectionTest
import com.example.accident_detection3.login.LoginActivity

class SplashActivity : AppCompatActivity() {
    
    private lateinit var phoneAuthPrefs: PhoneAuthPrefs
    private val splashHandler = Handler(Looper.getMainLooper())
    private val navigateRunnable = Runnable { checkAuthStatus() }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        phoneAuthPrefs = PhoneAuthPrefs(this)
        
        // Test Firebase connection
        testFirebaseConnection()
        
        splashHandler.postDelayed(navigateRunnable, 1500)
    }
    
    private fun testFirebaseConnection() {
        val status = FirebaseConnectionTest.testConnection(this)
        Log.d("SplashActivity", "Firebase Status: ${status.getStatusMessage()}")
    }
    
    private fun checkAuthStatus() {
        if (isFinishing || isDestroyed) return
        val intent = if (phoneAuthPrefs.isLoggedIn()) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        splashHandler.removeCallbacks(navigateRunnable)
    }
}
