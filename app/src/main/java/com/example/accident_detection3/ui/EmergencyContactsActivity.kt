package com.example.accident_detection3.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.accident_detection3.data.EmergencyContact
import com.example.accident_detection3.data.EmergencyContactManager
import com.example.accident_detection3.databinding.ActivityEmergencyContactsBinding

class EmergencyContactsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityEmergencyContactsBinding
    private lateinit var contactManager: EmergencyContactManager
    private var isEditMode = false
    
    private val relationships = arrayOf(
        "Parent",
        "Spouse",
        "Sibling",
        "Child",
        "Friend",
        "Colleague",
        "Neighbor",
        "Other"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Check if we're in edit mode (coming from menu) or setup mode
        isEditMode = intent.getBooleanExtra("EDIT_MODE", false)
        
        contactManager = EmergencyContactManager(this)

        setupRelationshipSpinner()
        setupRecyclerView()
        setupListeners()
        updateButtonVisibility()
    }
    
    private fun updateButtonVisibility() {
        if (isEditMode) {
            // In edit mode, hide Next button and style Back as a filled Done button
            binding.btnNext.visibility = android.view.View.GONE
            binding.btnBack.text = "Done"
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
    
    private fun setupRelationshipSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, relationships)
        binding.spinnerRelationship.setAdapter(adapter)
        binding.spinnerRelationship.setText(relationships[0], false) // Default to "Parent"
    }
    
    private fun setupRecyclerView() {
        binding.rvContacts.layoutManager = LinearLayoutManager(this)
        refreshContactsList()
    }
    
    private fun refreshContactsList() {
        val contacts = contactManager.getContacts()
        binding.rvContacts.adapter = EmergencyContactAdapter(contacts) { position ->
            contactManager.removeContact(position)
            refreshContactsList()
        }
    }
    
    private fun setupListeners() {
        binding.btnAddContact.setOnClickListener {
            addContact()
        }
        
        binding.btnNext.setOnClickListener {
            if (contactManager.getContacts().isEmpty()) {
                Toast.makeText(this, "Please add at least one emergency contact", Toast.LENGTH_LONG).show()
            } else {
                startActivity(Intent(this, SpeedLimitActivity::class.java))
            }
        }
        
        binding.btnBack.setOnClickListener {
            if (isEditMode && contactManager.getContacts().isEmpty()) {
                Toast.makeText(this, "Please add at least one emergency contact", Toast.LENGTH_LONG).show()
            } else {
                finish()
            }
        }
    }
    
    private fun addContact() {
        val name = binding.etContactName.text.toString().trim()
        val phone = binding.etContactPhone.text.toString().trim()
        val relationship = binding.spinnerRelationship.text.toString().trim()
        
        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please enter name and phone", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (relationship.isEmpty()) {
            Toast.makeText(this, "Please select a relationship", Toast.LENGTH_SHORT).show()
            return
        }
        
        val contact = EmergencyContact(name, phone, relationship)
        val result = contactManager.addContact(contact)
        when {
            result -> {
                binding.etContactName.text?.clear()
                binding.etContactPhone.text?.clear()
                binding.spinnerRelationship.setText(relationships[0], false)
                refreshContactsList()
                Toast.makeText(this, "Contact added", Toast.LENGTH_SHORT).show()
            }
            contactManager.getContacts().size >= 3 -> {
                Toast.makeText(this, "Maximum 3 contacts allowed", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "This phone number is already added", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
