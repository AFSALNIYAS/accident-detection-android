package com.example.accident_detection3.data

data class EmergencyContact(
    val name: String,
    val phoneNumber: String,
    val relationship: String = "Other"
)
