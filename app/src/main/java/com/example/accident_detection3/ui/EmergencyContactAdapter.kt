package com.example.accident_detection3.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.accident_detection3.data.EmergencyContact
import com.example.accident_detection3.databinding.ItemEmergencyContactBinding

class EmergencyContactAdapter(
    private val contacts: List<EmergencyContact>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<EmergencyContactAdapter.ContactViewHolder>() {
    
    inner class ContactViewHolder(private val binding: ItemEmergencyContactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(contact: EmergencyContact, position: Int) {
            binding.tvContactName.text = contact.name
            binding.tvContactPhone.text = contact.phoneNumber
            binding.tvContactRelationship.text = contact.relationship
            binding.btnDelete.setOnClickListener {
                onDeleteClick(position)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemEmergencyContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(contacts[position], position)
    }
    
    override fun getItemCount() = contacts.size
}
