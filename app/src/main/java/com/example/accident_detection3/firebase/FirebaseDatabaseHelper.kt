package com.example.accident_detection3.firebase

import com.example.accident_detection3.data.EmergencyContact
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FirebaseDatabaseHelper(
    private val database: FirebaseDatabase = FirebaseManager.database
) {
    
    private val usersRef: DatabaseReference = database.getReference("users")
    private val emergencyContactsRef: DatabaseReference = database.getReference("emergencyContacts")
    private val emergenciesRef: DatabaseReference = database.getReference("emergencies")
    
    // User Data
    data class UserData(
        val name: String = "",
        val phone: String = "",
        val bloodGroup: String = "",
        val speedLimit: Float = 80f
    )
    
    fun saveUserData(userId: String, userData: UserData, callback: (Boolean, String?) -> Unit) {
        usersRef.child(userId).setValue(userData)
            .addOnSuccessListener {
                callback(true, null)
            }
            .addOnFailureListener { e ->
                callback(false, e.message)
            }
    }
    
    fun getUserData(userId: String, callback: (UserData?) -> Unit) {
        usersRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userData = snapshot.getValue(UserData::class.java)
                callback(userData)
            }
            
            override fun onCancelled(error: DatabaseError) {
                callback(null)
            }
        })
    }
    
    // Emergency Contacts
    fun saveEmergencyContacts(
        userId: String,
        contacts: List<EmergencyContact>,
        callback: (Boolean, String?) -> Unit
    ) {
        val contactsMap = contacts.mapIndexed { index, contact ->
            "contact$index" to mapOf(
                "name" to contact.name,
                "phoneNumber" to contact.phoneNumber,
                "relationship" to contact.relationship
            )
        }.toMap()
        
        emergencyContactsRef.child(userId).setValue(contactsMap)
            .addOnSuccessListener {
                callback(true, null)
            }
            .addOnFailureListener { e ->
                callback(false, e.message)
            }
    }
    
    fun getEmergencyContacts(userId: String, callback: (List<EmergencyContact>) -> Unit) {
        emergencyContactsRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val contacts = mutableListOf<EmergencyContact>()
                snapshot.children.forEach { child ->
                    val name = child.child("name").getValue(String::class.java) ?: ""
                    val phoneNumber = child.child("phoneNumber").getValue(String::class.java) ?: ""
                    val relationship = child.child("relationship").getValue(String::class.java) ?: "Other"
                    if (name.isNotEmpty() && phoneNumber.isNotEmpty()) {
                        contacts.add(EmergencyContact(name, phoneNumber, relationship))
                    }
                }
                callback(contacts)
            }
            
            override fun onCancelled(error: DatabaseError) {
                callback(emptyList())
            }
        })
    }
    
    // Emergency Events
    data class EmergencyEvent(
        val userId: String = "",
        val timestamp: Long = System.currentTimeMillis(),
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val speed: Float = 0f,
        val type: String = "", // "overspeed" or "accident"
        val resolved: Boolean = false
    )
    
    fun saveEmergencyEvent(
        userId: String,
        event: EmergencyEvent,
        callback: (Boolean, String?) -> Unit
    ) {
        val emergencyId = emergenciesRef.push().key ?: return
        emergenciesRef.child(emergencyId).setValue(event)
            .addOnSuccessListener {
                callback(true, emergencyId)
            }
            .addOnFailureListener { e ->
                callback(false, e.message)
            }
    }
    
    fun getEmergencyHistory(userId: String, callback: (List<EmergencyEvent>) -> Unit) {
        emergenciesRef.orderByChild("userId").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val events = mutableListOf<EmergencyEvent>()
                    snapshot.children.forEach { child ->
                        child.getValue(EmergencyEvent::class.java)?.let { event ->
                            events.add(event)
                        }
                    }
                    callback(events.sortedByDescending { it.timestamp })
                }
                
                override fun onCancelled(error: DatabaseError) {
                    callback(emptyList())
                }
            })
    }
    
    fun markEmergencyResolved(emergencyId: String, callback: (Boolean) -> Unit) {
        emergenciesRef.child(emergencyId).child("resolved").setValue(true)
            .addOnSuccessListener {
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }
}
