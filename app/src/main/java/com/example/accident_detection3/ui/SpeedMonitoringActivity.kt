package com.example.accident_detection3.ui

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.example.accident_detection3.R
import com.example.accident_detection3.data.SpeedLimitPrefs
import com.example.accident_detection3.data.UserPrefs
import com.example.accident_detection3.databinding.ActivitySpeedMonitoringBinding
import com.example.accident_detection3.emergency.EmergencyInfoActivity
import com.example.accident_detection3.login.LoginActivity
import com.example.accident_detection3.monitor.SpeedMonitor
import com.example.accident_detection3.notification.EmergencyNotifier
import com.example.accident_detection3.service.TrackingService
import com.example.accident_detection3.util.Constants
import com.example.accident_detection3.util.NotificationHelper
import com.google.android.material.navigation.NavigationView

class SpeedMonitoringActivity : AppCompatActivity() {
    
    private val TAG = "SpeedMonitoringActivity"
    private lateinit var binding: ActivitySpeedMonitoringBinding
    private lateinit var speedLimitPrefs: SpeedLimitPrefs
    private lateinit var speedMonitor: SpeedMonitor
    private lateinit var emergencyNotifier: EmergencyNotifier
    
    private var isMonitoring = false
    private var currentLatitude = 0.0
    private var currentLongitude = 0.0
    
    private val speedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_SPEED_UPDATE -> {
                    Log.d(TAG, "Broadcast received: ${intent.action}")
                    
                    val speed = intent.getFloatExtra(Constants.EXTRA_SPEED, 0f)
                    currentLatitude = intent.getDoubleExtra(Constants.EXTRA_LATITUDE, 0.0)
                    currentLongitude = intent.getDoubleExtra(Constants.EXTRA_LONGITUDE, 0.0)
                    
                    Log.d(TAG, "Speed received: $speed km/h, Location: ($currentLatitude, $currentLongitude)")
                    
                    updateSpeedDisplay(speed)
                }
                Constants.ACTION_ACCIDENT_CONFIRMED -> {
                    // TrackingService already calls startActivity() + posts the full-screen
                    // notification. Only update the UI chip here — do NOT launch the activity
                    // again or it will appear twice (double countdown screen).
                    Log.d(TAG, "Accident confirmed — updating status chip")
                    currentLatitude = intent.getDoubleExtra(Constants.EXTRA_LATITUDE, 0.0)
                    currentLongitude = intent.getDoubleExtra(Constants.EXTRA_LONGITUDE, 0.0)
                    binding.chipStatus.text = "🚨 Accident Detected"
                }
                Constants.ACTION_ACCIDENT_CANCELLED -> {
                    Log.d(TAG, "Accident cancelled — resetting status chip")
                    if (isMonitoring) binding.chipStatus.text = "Active"
                }
                Constants.ACTION_EMERGENCY_TRIGGERED -> {
                    Log.d(TAG, "Emergency triggered — updating status chip")
                    binding.chipStatus.text = "🚨 Emergency Sent"
                }
            }
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // On Android 10+, request background location separately
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocationIfNeeded()
            } else {
                startMonitoring()
            }
        } else {
            val deniedPermissions = permissions.filter { !it.value }.keys
            Toast.makeText(
                this, 
                "Required permissions denied: ${deniedPermissions.joinToString(", ")}", 
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startMonitoring()
        } else {
            // Can still monitor without background location, but warn user
            val warnDialog = AlertDialog.Builder(this)
                .setTitle("Background Location")
                .setMessage("Background location permission is recommended for continuous monitoring. Continue without it?")
                .setPositiveButton("Continue") { _, _ ->
                    startMonitoring()
                }
                .setNegativeButton("Cancel", null)
                .show()
            warnDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.accent))
            warnDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeedMonitoringBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Restore isMonitoring across rotation/recreation by checking if the service is running
        isMonitoring = isTrackingServiceRunning()

        setupToolbar()
        setupNavigationDrawer()
        initializeComponents()
        setupListeners()
        initializeButtonStates()
        displayGreeting()
    }
    
    private fun setupToolbar() {
        // Do NOT call setSupportActionBar — it hijacks menu inflation.
        // Use the toolbar directly so inflateMenu works correctly.
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.btnNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }

        // Scale the bell icon to match the text size (16dp)
        val bellSize = (16 * resources.displayMetrics.density).toInt()
        val bell = ContextCompat.getDrawable(this, android.R.drawable.ic_popup_reminder)
        bell?.setBounds(0, 0, bellSize, bellSize)
        binding.btnNotifications.setCompoundDrawables(null, null, bell, null)
    }
    
    private fun setupNavigationDrawer() {
        // Update header with user info
        val userPrefs = UserPrefs(this)
        val headerView = binding.navigationView.getHeaderView(0)
        val navHeaderName = headerView.findViewById<TextView>(R.id.navHeaderName)
        val navHeaderPhone = headerView.findViewById<TextView>(R.id.navHeaderPhone)
        
        navHeaderName.text = userPrefs.userName.ifEmpty { "User Name" }
        navHeaderPhone.text = userPrefs.mobileNumber.ifEmpty { "Phone Number" }

        // Style logout item with accent red to make it clearly visible
        val logoutItem = binding.navigationView.menu.findItem(R.id.nav_logout)
        val accentColor = ContextCompat.getColor(this, R.color.accent)
        val redStateList = android.content.res.ColorStateList.valueOf(accentColor)
        logoutItem?.let { item ->
            val title = android.text.SpannableString(item.title)
            title.setSpan(
                android.text.style.ForegroundColorSpan(accentColor),
                0, title.length,
                android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
            item.title = title
            item.icon?.setTintList(redStateList)
        }

        // Handle navigation item clicks
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_edit_profile -> {
                    startActivity(Intent(this, UserInfoActivity::class.java).apply {
                        putExtra("EDIT_MODE", true)
                    })
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_edit_contacts -> {
                    startActivity(Intent(this, EmergencyContactsActivity::class.java).apply {
                        putExtra("EDIT_MODE", true)
                    })
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_edit_speed_limit -> {
                    startActivity(Intent(this, SpeedLimitActivity::class.java).apply {
                        putExtra("EDIT_MODE", true)
                    })
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_logout -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Log Out")
                        .setMessage("Are you sure you want to log out?")
                        .setPositiveButton("Yes") { _, _ ->
                            stopMonitoring()
                            com.example.accident_detection3.data.PhoneAuthPrefs(this).logout()
                            startActivity(Intent(this, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        }
                        .setNegativeButton("No", null)
                        .show()
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(ContextCompat.getColor(this, R.color.accent))
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                    true
                }
                else -> false
            }
        }
    }
    
    private fun displayGreeting() {
        val userPrefs = com.example.accident_detection3.data.UserPrefs(this)
        val userName = userPrefs.userName
        binding.tvGreeting.text = if (userName.isNotEmpty()) {
            "Hello, $userName!"
        } else {
            "Hello, User!"
        }
        
        // Update navigation header as well
        updateNavigationHeader()
    }
    
    private fun updateNavigationHeader() {
        val userPrefs = UserPrefs(this)
        val headerView = binding.navigationView.getHeaderView(0)
        val navHeaderName = headerView.findViewById<TextView>(R.id.navHeaderName)
        val navHeaderPhone = headerView.findViewById<TextView>(R.id.navHeaderPhone)
        
        navHeaderName.text = userPrefs.userName.ifEmpty { "User Name" }
        navHeaderPhone.text = userPrefs.mobileNumber.ifEmpty { "Phone Number" }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh user info when returning from edit screens
        displayGreeting()
        // Re-register receiver so speed updates and accident broadcasts are received
        if (isMonitoring) {
            registerSpeedReceiver()
        }
    }

    override fun onPause() {
        super.onPause()
        // Keep receiver active while paused so ACTION_ACCIDENT_CONFIRMED is never missed.
        // Only unregister on stop (activity fully off screen) to avoid missing the alert launch.
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(speedReceiver) } catch (e: Exception) { /* not registered */ }
    }
    
    private fun initializeComponents() {
        speedLimitPrefs = SpeedLimitPrefs(this)
        speedMonitor = SpeedMonitor(speedLimitPrefs)
        emergencyNotifier = EmergencyNotifier(this)
    }
    
    private fun initializeButtonStates() {
        // Reflect actual service state — handles rotation and activity recreation
        binding.btnStartMonitoring.isEnabled = !isMonitoring
        binding.btnStopMonitoring.isEnabled = isMonitoring
        updateStatusUI(isMonitoring)
    }

    @Suppress("DEPRECATION")
    private fun isTrackingServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == TrackingService::class.java.name
        }
    }
    
    private fun updateStatusUI(isMonitoring: Boolean) {
        if (isMonitoring) {
            binding.tvStatusText.text = "Monitoring Active"
            binding.chipStatus.text = "Active"
            binding.chipStatus.setChipBackgroundColorResource(R.color.success)
            binding.tvSpeedInfo.text = "GPS tracking active"
            binding.statusIndicator.setBackgroundResource(R.drawable.bg_status_indicator_active)
        } else {
            binding.tvStatusText.text = "Monitoring Stopped"
            binding.chipStatus.text = "Idle"
            binding.chipStatus.setChipBackgroundColorResource(R.color.text_secondary)
            binding.tvSpeedInfo.text = "Start monitoring to track speed"
            binding.statusIndicator.setBackgroundResource(R.drawable.bg_status_indicator)
        }
    }
    
    private fun setupListeners() {
        binding.btnStartMonitoring.setOnClickListener {
            requestPermissionsAndStart()
        }

        binding.btnStopMonitoring.setOnClickListener {
            stopMonitoring()
        }

        binding.tvSpeed.setOnLongClickListener {
            showDebugMenu()
            true
        }
    }
    
    private fun showDebugMenu() {
        val options = arrayOf(
            "� Simulate Driving crash (60 km/h)",
            "�️ Simulate Highway crash (100 km/h)",
            "🧪 Simulate Full Accident (65 km/h)"
        )

        AlertDialog.Builder(this)
            .setTitle("Accident Simulation")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> triggerAccidentSimulation(60f, "Driving")
                    1 -> triggerAccidentSimulation(100f, "Highway")
                    2 -> triggerAccidentSimulation(65f, "Full Test")
                }
            }
            .show()
    }

    private fun triggerAccidentSimulation(speed: Float, label: String) {
        if (!isMonitoring) {
            Toast.makeText(this, "Start monitoring first, then simulate", Toast.LENGTH_SHORT).show()
            return
        }
        sendBroadcast(Intent(Constants.ACTION_SIMULATE_ACCIDENT).apply {
            setPackage(packageName)
            putExtra(Constants.EXTRA_SPEED, speed)
            putExtra("extra_label", label)
        })
        Toast.makeText(this, "[$label] Simulating crash from ${speed.toInt()} km/h — alert in ~12s", Toast.LENGTH_LONG).show()
    }
    
    private fun requestPermissionsAndStart() {
        // Build list of permissions to request (excluding background location for Android 10+)
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS
        )
        
        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Check which permissions are not granted
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (notGranted.isEmpty()) {
            // All basic permissions granted, check background location
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocationIfNeeded()
            } else {
                startMonitoring()
            }
        } else {
            // Request missing permissions
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }
    
    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Show rationale before requesting
                val bgDialog = AlertDialog.Builder(this)
                    .setTitle("Background Location Permission")
                    .setMessage(
                        "To monitor your speed continuously even when the app is in the background or screen is off, " +
                        "we need background location permission.\n\n" +
                        "Please select 'Allow all the time' on the next screen."
                    )
                    .setPositiveButton("Continue") { _, _ ->
                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        startMonitoring()
                    }
                    .show()
                bgDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(this, R.color.accent))
                bgDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            } else {
                startMonitoring()
            }
        } else {
            startMonitoring()
        }
    }
    
    private fun startMonitoring() {
        Log.d(TAG, "Starting monitoring...")

        // SYSTEM_ALERT_WINDOW (overlay) — lets the foreground service call startActivity()
        // from background. Without this, the alert only shows via full-screen notification.
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage(
                    "To show the emergency alert screen over other apps when an accident is detected, " +
                    "please enable 'Display over other apps' for this app in Settings."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
                .setNegativeButton("Skip") { _, _ ->
                    checkFullScreenIntentAndStart()
                }
                .show()
                .also { dialog ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(ContextCompat.getColor(this, R.color.accent))
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                }
            return
        }

        checkFullScreenIntentAndStart()
    }

    private fun checkFullScreenIntentAndStart() {
        // Android 14+: USE_FULL_SCREEN_INTENT is a special runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                AlertDialog.Builder(this)
                    .setTitle("Full-Screen Alert Permission")
                    .setMessage(
                        "To show the emergency alert screen when an accident is detected, " +
                        "please grant the 'Full-screen notifications' permission in Settings."
                    )
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        doStartMonitoring()
                    }
                    .show()
                    .also { dialog ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            .setTextColor(ContextCompat.getColor(this, R.color.accent))
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                            .setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                    }
                return
            }
        }
        doStartMonitoring()
    }

    private fun doStartMonitoring() {
        isMonitoring = true
        updateStatusUI(true)
        binding.btnStartMonitoring.isEnabled = false
        binding.btnStopMonitoring.isEnabled = true
        
        val serviceIntent = Intent(this, TrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        registerSpeedReceiver()
        
        Log.d(TAG, "Monitoring started, receiver registered")
        Toast.makeText(this, "Speed monitoring started", Toast.LENGTH_SHORT).show()
    }

    private fun registerSpeedReceiver() {
        // Unregister first to prevent double-registration
        try { unregisterReceiver(speedReceiver) } catch (e: Exception) { /* not registered */ }
        val filter = IntentFilter().apply {
            addAction(Constants.ACTION_SPEED_UPDATE)
            addAction(Constants.ACTION_ACCIDENT_CONFIRMED)
            addAction(Constants.ACTION_ACCIDENT_CANCELLED)
            addAction(Constants.ACTION_EMERGENCY_TRIGGERED)
        }
        ContextCompat.registerReceiver(
            this,
            speedReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    
    private fun stopMonitoring() {
        isMonitoring = false
        updateStatusUI(false)
        binding.btnStartMonitoring.isEnabled = true
        binding.btnStopMonitoring.isEnabled = false
        stopService(Intent(this, TrackingService::class.java))
        // Unregister receiver now — service is stopped so no more broadcasts will arrive
        try { unregisterReceiver(speedReceiver) } catch (e: Exception) { /* not registered */ }
        // Reset speed display so it doesn't freeze on the last value
        binding.tvSpeed.text = "0"
        binding.tvSpeedInfo.text = "Start monitoring to track speed"
    }
    
    private fun updateSpeedDisplay(speed: Float) {
        Log.d(TAG, "Updating speed display: $speed km/h")
        if (isFinishing || isDestroyed) return
        // Already on main looper — no need for runOnUiThread
        binding.tvSpeed.text = speed.toInt().toString()
        // Keep the info line useful — show speed limit while monitoring
        val limit = speedLimitPrefs.speedLimit.toInt()
        binding.tvSpeedInfo.text = "Speed limit: $limit km/h"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Receiver is managed by onResume/onStop — nothing extra to clean up here
    }
    
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
