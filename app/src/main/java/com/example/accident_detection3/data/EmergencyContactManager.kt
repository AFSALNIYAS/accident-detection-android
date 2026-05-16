package com.example.accident_detection3.data

import android.content.Context
import android.content.SharedPreferences
import com.example.accident_detection3.util.Constants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class EmergencyContactManager(context: Context) {
    private val prefs: SharedPreferences = run {
        val currentUser = PhoneAuthPrefs(context).getCurrentUser()
        val prefsName = if (currentUser != null) "${Constants.PREFS_NAME}_$currentUser" else Constants.PREFS_NAME
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    private val maxContacts = 3
    
    fun getContacts(): List<EmergencyContact> {
        val json = prefs.getString(Constants.KEY_EMERGENCY_CONTACTS, null) ?: return emptyList()
        val type = object : TypeToken<List<EmergencyContact>>() {}.type
        return gson.fromJson(json, type)
    }
    
    fun addContact(contact: EmergencyContact): Boolean {
        val contacts = getContacts().toMutableList()
        if (contacts.size >= maxContacts) {
            return false
        }
        // Prevent duplicate phone numbers
        if (contacts.any { it.phoneNumber == contact.phoneNumber }) {
            return false
        }
        contacts.add(contact)
        saveContacts(contacts)
        return true
    }
    
    fun removeContact(position: Int) {
        val contacts = getContacts().toMutableList()
        if (position in contacts.indices) {
            contacts.removeAt(position)
            saveContacts(contacts)
        }
    }
    
    private fun saveContacts(contacts: List<EmergencyContact>) {
        val json = gson.toJson(contacts)
        prefs.edit().putString(Constants.KEY_EMERGENCY_CONTACTS, json).apply()
    }
}
