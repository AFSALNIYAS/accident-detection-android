package com.example.accident_detection3.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("notifications", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveNotification(message: String) {
        val list = getNotifications().toMutableList()
        val timestamp = SimpleDateFormat(
            "dd MMM yyyy, hh:mm a", Locale.getDefault()
        ).format(Date())
        list.add(0, "[$timestamp] $message")   // newest first
        if (list.size > 50) list.removeLast()  // keep max 50
        prefs.edit().putString("list", gson.toJson(list)).apply()
    }

    fun getNotifications(): List<String> {
        val json = prefs.getString("list", null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type)
    }

    fun clearNotifications() {
        prefs.edit().remove("list").apply()
    }
}