package com.example.accident_detection3.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object FirebaseManager {
    
    private var isInitialized = false
    
    val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }
    
    val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance()
    }
    
    fun initialize(context: Context) {
        if (!isInitialized) {
            FirebaseApp.initializeApp(context)
            // setPersistenceEnabled must be called before any DatabaseReference is created.
            // Calling it here (before the lazy 'database' property is first accessed) is safe.
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            isInitialized = true
        }
    }
    
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
    
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }
    
    fun signOut() {
        auth.signOut()
    }
}
