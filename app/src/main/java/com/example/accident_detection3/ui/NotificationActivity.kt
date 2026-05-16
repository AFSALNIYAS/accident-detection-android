package com.example.accident_detection3.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.accident_detection3.data.AlertStatePrefs
import com.example.accident_detection3.data.NotificationPrefs
import com.example.accident_detection3.databinding.ActivityNotificationBinding
import com.example.accident_detection3.emergency.AccidentAlertActivity
import com.example.accident_detection3.notification.EmergencyNotifier
import com.example.accident_detection3.util.Constants

class NotificationActivity : AppCompatActivity() {

    private val TAG = "NotificationActivity"
    private lateinit var binding: ActivityNotificationBinding
    private lateinit var notificationPrefs: NotificationPrefs
    private lateinit var alertStatePrefs: AlertStatePrefs
    private lateinit var emergencyNotifier: EmergencyNotifier

    private var liveCountdownTimer: CountDownTimer? = null
    private var emergencyTriggered = false

    // Listen for alert resolved from the full-screen activity or notification buttons
    private val alertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_ACCIDENT_CANCELLED,
                Constants.ACTION_EMERGENCY_TRIGGERED -> {
                    Log.d(TAG, "Alert resolved (${intent.action}) — hiding live banner")
                    liveCountdownTimer?.cancel()
                    hideLiveAlertBanner()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        notificationPrefs = NotificationPrefs(this)
        alertStatePrefs = AlertStatePrefs(this)
        emergencyNotifier = EmergencyNotifier(this)

        setupToolbar()
        loadNotifications()
        setupRecyclerView()
        setupLiveAlertBanner()

        val filter = IntentFilter().apply {
            addAction(Constants.ACTION_ACCIDENT_CANCELLED)
            addAction(Constants.ACTION_EMERGENCY_TRIGGERED)
        }
        ContextCompat.registerReceiver(this, alertReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Notifications"
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadNotifications() {
        val notifications = notificationPrefs.getNotifications()
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)

        if (notifications.isEmpty()) {
            binding.rvNotifications.visibility = View.GONE
            binding.tvEmptyState.visibility = View.VISIBLE
        } else {
            binding.rvNotifications.visibility = View.VISIBLE
            binding.tvEmptyState.visibility = View.GONE
            binding.rvNotifications.adapter = NotificationAdapter(notifications)
        }
    }

    private fun setupRecyclerView() {
        // layoutManager is already set in loadNotifications() — nothing extra needed here
    }

    // ── Live alert banner ──────────────────────────────────────────────────────

    private fun setupLiveAlertBanner() {
        if (!alertStatePrefs.isAlertActive()) return

        val startTime = alertStatePrefs.getStartTime()
        val elapsed = System.currentTimeMillis() - startTime
        val remaining = AccidentAlertActivity.COUNTDOWN_DURATION - elapsed

        if (remaining <= 0) {
            // Timer already expired — clear stale state
            alertStatePrefs.clearAlert()
            return
        }

        showLiveAlertBanner(remaining)
    }

    private fun showLiveAlertBanner(remainingMs: Long) {
        binding.cardLiveAlert.visibility = View.VISIBLE

        binding.btnLiveImSafe.setOnClickListener {
            liveCountdownTimer?.cancel()
            alertStatePrefs.clearAlert()
            sendBroadcast(Intent(Constants.ACTION_ACCIDENT_CANCELLED).apply {
                setPackage(packageName)
            })
            hideLiveAlertBanner()
            Log.d(TAG, "I'm Safe pressed from NotificationActivity")
        }

        binding.btnLiveSendHelp.setOnClickListener {
            if (emergencyTriggered) return@setOnClickListener
            emergencyTriggered = true
            liveCountdownTimer?.cancel()
            alertStatePrefs.clearAlert()
            triggerEmergency()
        }

        liveCountdownTimer = object : CountDownTimer(remainingMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = (millisUntilFinished / 1000).toInt()
                binding.tvLiveCountdown.text = secs.toString()
            }

            override fun onFinish() {
                binding.tvLiveCountdown.text = "0"
                if (!emergencyTriggered) {
                    emergencyTriggered = true
                    alertStatePrefs.clearAlert()
                    triggerEmergency()
                }
            }
        }.start()
    }

    private fun hideLiveAlertBanner() {
        binding.cardLiveAlert.visibility = View.GONE
        liveCountdownTimer?.cancel()
    }

    private fun triggerEmergency() {
        val lat = alertStatePrefs.getLatitude()
        val lon = alertStatePrefs.getLongitude()

        Log.d(TAG, "Triggering emergency from NotificationActivity — lat=$lat lon=$lon")

        try {
            emergencyNotifier.sendEmergencyAlertSMS(lat, lon)
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed: ${e.message}", e)
        }

        sendBroadcast(Intent(Constants.ACTION_EMERGENCY_TRIGGERED).apply {
            setPackage(packageName)
            putExtra(Constants.EXTRA_LATITUDE, lat)
            putExtra(Constants.EXTRA_LONGITUDE, lon)
        })

        hideLiveAlertBanner()
    }

    override fun onDestroy() {
        super.onDestroy()
        liveCountdownTimer?.cancel()
        try {
            unregisterReceiver(alertReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "alertReceiver already unregistered")
        }
    }
}
