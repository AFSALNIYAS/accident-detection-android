package com.example.accident_detection3.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class FirebaseAuthHelper(private val auth: FirebaseAuth = FirebaseManager.auth) {
    
    interface AuthCallback {
        fun onSuccess(userId: String)
        fun onFailure(error: String)
        fun onCodeSent(verificationId: String)
    }
    
    fun sendVerificationCode(
        phoneNumber: String,
        activity: android.app.Activity,
        callback: AuthCallback
    ) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithCredential(credential, callback)
                }
                
                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    callback.onFailure(e.message ?: "Verification failed")
                }
                
                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    callback.onCodeSent(verificationId)
                }
            })
            .build()
        
        PhoneAuthProvider.verifyPhoneNumber(options)
    }
    
    fun verifyCode(
        verificationId: String,
        code: String,
        callback: AuthCallback
    ) {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        signInWithCredential(credential, callback)
    }
    
    private fun signInWithCredential(
        credential: PhoneAuthCredential,
        callback: AuthCallback
    ) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                result.user?.uid?.let { userId ->
                    callback.onSuccess(userId)
                } ?: callback.onFailure("User ID not found")
            }
            .addOnFailureListener { e ->
                callback.onFailure(e.message ?: "Sign in failed")
            }
    }
    
    fun signOut() {
        auth.signOut()
    }
    
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
    
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }
}
