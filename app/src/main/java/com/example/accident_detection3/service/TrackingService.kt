package com.example.accident_detection3.service

import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo

import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.accident_detection3.data.SpeedLimitPrefs
import com.example.accident_detection3.detector.AccidentDetector
import com.example.accident_detection3.monitor.SpeedMonitor
import com.example.accident_detection3.util.Constants
import com.example.accident_detection3.util.ImprovedSpeedCalculator
import com.example.accident_detection3.util.NotificationHelper
import com.google.android.gms.location.*
import java.util.concurrent.atomic.AtomicBoolean

class TrackingService : Service() {

    private val TAG = "TrackingService"
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var accidentDetector: AccidentDetector
    private lateinit var speedMonitor: SpeedMonitor
    private lateinit var improvedSpeedCalculator: ImprovedSpeedCalculator

    // Speed tracking variables
    private var currentSpeed: Float = 0f        // averaged speed — used for UI
    private var rawSpeed: Float = 0f            // raw GPS speed — used for accident detection
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var currentLocation: Location? = null

    // Accident confirmation tracking — AtomicBoolean prevents race conditions
    // with rapid GPS updates arriving on the main looper
    private val accidentBroadcastSent = AtomicBoolean(false)

    // True while accident simulation is running — AtomicBoolean prevents GPS race
    private val isSimulation = AtomicBoolean(false)

    // Handler used by simulation ticks — kept so we can cancel on destroy
    private val simulationHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // When >= 0, overrides the GPS speed broadcast to the UI (debug speed lock)
    private var simulatedSpeedOverride: Float = -1f

    // Receiver to reset accident state when user presses "I'm Safe" OR after emergency is sent
    private val accidentCancelledReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_ACCIDENT_CANCELLED,
                Constants.ACTION_EMERGENCY_TRIGGERED -> {
                    Log.d(TAG, "Accident resolved (${intent.action}) — resetting detector")
                    accidentBroadcastSent.set(false)
                    accidentDetector.resetAlert()
                    speedMonitor.resetAlert()
                }
                Constants.ACTION_SIMULATE_ACCIDENT -> {
                    Log.d(TAG, "🧪 Simulation triggered")
                    simulatedSpeedOverride = -1f
                    val speed = intent.getFloatExtra(Constants.EXTRA_SPEED, 65f)
                    val label = intent.getStringExtra("extra_label") ?: "Full Test"
                    simulateAccidentAt(speed, label)
                }                Constants.ACTION_SIMULATE_SPEED -> {
                    val speed = intent.getFloatExtra(Constants.EXTRA_SPEED, -1f)
                    if (speed < 0f) {
                        simulatedSpeedOverride = -1f
                        Log.d(TAG, "🧪 Speed override cleared — resuming real GPS")
                    } else {
                        simulatedSpeedOverride = speed
                        Log.d(TAG, "🧪 Speed override set to $speed km/h")
                        // Use simulationHandler so ticks are cancelled in onDestroy()
                        var elapsed = 0
                        fun tick() {
                            if (simulatedSpeedOverride != speed || elapsed >= 5) {
                                if (simulatedSpeedOverride == speed) {
                                    simulatedSpeedOverride = -1f
                                    Log.d(TAG, "🧪 Speed override auto-cleared")
                                }
                                return
                            }
                            broadcastSpeedUpdate(speed, currentLatitude, currentLongitude, false)
                            elapsed++
                            simulationHandler.postDelayed({ tick() }, 1000L)
                        }
                        tick()
                    }
                }
            }
        }
    }
    
    // Statistics logging
    private var updateCount = 0
    private val STATS_LOG_INTERVAL = 60 // Log stats every 60 updates (~1 minute)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        NotificationHelper.createNotificationChannel(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        accidentDetector = AccidentDetector()
        accidentDetector.listener = object : AccidentDetector.AccidentListener {
            override fun onAccidentDetected(speedBefore: Float, speedAfter: Float) {
                if (accidentBroadcastSent.compareAndSet(false, true)) {
                    Log.d(TAG, "🚨 Accident callback fired: $speedBefore → $speedAfter km/h")
                    broadcastAccidentConfirmed()
                }
            }
        }

        speedMonitor = SpeedMonitor(SpeedLimitPrefs(this))
        improvedSpeedCalculator = ImprovedSpeedCalculator()

        // Listen for accident resolved (cancelled by user OR emergency sent) + simulation trigger
        val filter = IntentFilter().apply {
            addAction(Constants.ACTION_ACCIDENT_CANCELLED)
            addAction(Constants.ACTION_EMERGENCY_TRIGGERED)
            addAction(Constants.ACTION_SIMULATE_ACCIDENT)
            addAction(Constants.ACTION_SIMULATE_SPEED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(accidentCancelledReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(accidentCancelledReceiver, filter)
        }

        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        val notification = NotificationHelper.createForegroundNotification(this, 0f)

        val hasLocationPermission = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            Log.e(TAG, "Cannot start foreground service: Location permission not granted")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    Constants.FOREGROUND_SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(
                    Constants.FOREGROUND_SERVICE_NOTIFICATION_ID,
                    notification
                )
            }
            Log.d(TAG, "Foreground service started")
            startLocationUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error starting foreground service: ${e.message}", e)
            stopSelf()
        }

        return START_STICKY
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    processLocation(location)
                }
            }
        }
    }

    private fun processLocation(location: Location) {
        // Block all real GPS processing while simulation is running — simulation owns the UI
        if (isSimulation.get()) return

        currentLatitude = location.latitude
        currentLongitude = location.longitude
        currentLocation = location
        updateCount++

        Log.d(TAG, "📍 Location update #$updateCount: " +
                "lat=${String.format("%.6f", currentLatitude)}, " +
                "lon=${String.format("%.6f", currentLongitude)}, " +
                "accuracy=${String.format("%.1f", location.accuracy)}m")

        // Capture previous location BEFORE calculateSpeed() updates it internally
        val prevLocation = improvedSpeedCalculator.getPreviousLocation()
        val prevTime = improvedSpeedCalculator.getPreviousTime()

        // Calculate averaged speed (for UI display)
        val calculatedSpeed = improvedSpeedCalculator.calculateSpeed(location)

        if (calculatedSpeed == null) {
            Log.d(TAG, "⏭️ Speed update skipped (filtered by calculator)")
            // Still feed raw GPS speed to accident detector — a crash can happen even
            // when the accuracy is temporarily poor, and we must not miss the deceleration.
            val rawForDetector = if (location.hasSpeed() && location.speed > 0f) {
                location.speed * 3.6f
            } else 0f
            accidentDetector.processGPSUpdate(rawForDetector, location)
            return
        }

        currentSpeed = calculatedSpeed

        // Raw GPS speed — used for accident detection (unaveraged, reflects real crash dynamics)
        // Prefer location.speed (hardware GPS doppler), fall back to distance-based instant speed
        rawSpeed = if (location.hasSpeed() && location.speed > 0f) {
            location.speed * 3.6f
        } else {
            // Use prevLocation captured BEFORE calculateSpeed() updated the internal state
            if (prevLocation != null && prevTime > 0) {
                val dt = (System.currentTimeMillis() - prevTime) / 1000f
                if (dt > 0) prevLocation.distanceTo(location) / dt * 3.6f else calculatedSpeed
            } else calculatedSpeed
        }

        Log.d(TAG, "🚗 Speed: avg=${String.format("%.2f", currentSpeed)} km/h  " +
                "raw=${String.format("%.2f", rawSpeed)} km/h " +
                "(stable: ${improvedSpeedCalculator.isSpeedStable()}, " +
                "buffer: ${improvedSpeedCalculator.getBufferSize()}/4)")

        // Broadcast averaged speed to UI — skip if a simulated override is actively ticking
        if (simulatedSpeedOverride < 0f) {
            broadcastSpeedUpdate(currentSpeed, currentLatitude, currentLongitude, false)
        }

        // Update foreground notification
        updateNotification(currentSpeed)

        // Log statistics periodically
        if (updateCount % STATS_LOG_INTERVAL == 0) {
            improvedSpeedCalculator.logStatistics()
        }

        // Feed RAW speed to accident detector — averaging would mask the sudden drop
        accidentDetector.processGPSUpdate(rawSpeed, location)

        // Speed limit check — only trigger if no accident alert is already active
        if (!accidentDetector.isAccidentConfirmed() && speedMonitor.checkSpeed(currentSpeed)) {
            Log.d(TAG, "⚠️ Speed limit exceeded: ${currentSpeed.toInt()} km/h — triggering emergency alert")
            // Show warning notification
            NotificationHelper.showSpeedLimitWarning(
                this,
                currentSpeed,
                SpeedLimitPrefs(this).speedLimit
            )
            // Also launch the AccidentAlertActivity with 30s countdown
            triggerSpeedLimitAlert()
        }
    }

    private fun triggerSpeedLimitAlert() {
        Log.d(TAG, "🚨 Speed limit alert — launching AccidentAlertActivity")

        NotificationHelper.showAccidentAlert(this, currentLatitude, currentLongitude, currentSpeed)

        try {
            startActivity(Intent(
                this,
                com.example.accident_detection3.emergency.AccidentAlertActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(Constants.EXTRA_LATITUDE, currentLatitude)
                putExtra(Constants.EXTRA_LONGITUDE, currentLongitude)
                putExtra(Constants.EXTRA_SPEED, currentSpeed)
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ startActivity() failed for speed limit alert: ${e.message}")
        }
    }

    private fun broadcastAccidentConfirmed() {
        Log.d(TAG, "🚨 Launching accident alert screen")

        val crashLocation = accidentDetector.getCrashLocation()
        val lat = crashLocation?.latitude ?: currentLatitude
        val lon = crashLocation?.longitude ?: currentLongitude

        val alertIntent = Intent(
            this,
            com.example.accident_detection3.emergency.AccidentAlertActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Constants.EXTRA_LATITUDE, lat)
            putExtra(Constants.EXTRA_LONGITUDE, lon)
            putExtra(Constants.EXTRA_SPEED, currentSpeed)
        }

        // 1. Broadcast ACTION_ACCIDENT_CONFIRMED so SpeedMonitoringActivity (if in foreground)
        //    can update its UI chip and launch the alert screen directly.
        sendBroadcast(Intent(Constants.ACTION_ACCIDENT_CONFIRMED).apply {
            setPackage(packageName)
            putExtra(Constants.EXTRA_LATITUDE, lat)
            putExtra(Constants.EXTRA_LONGITUDE, lon)
            putExtra(Constants.EXTRA_SPEED, currentSpeed)
        })
        Log.d(TAG, "✅ ACTION_ACCIDENT_CONFIRMED broadcast sent")

        // 2. Always post the full-screen intent notification.
        //    On Android 10+ this is the most reliable launch path from a background service.
        NotificationHelper.showAccidentAlert(this, lat, lon, currentSpeed)
        Log.d(TAG, "✅ Full-screen intent notification posted")

        // 3. Attempt direct startActivity() — works when the service has a visible
        //    foreground notification (all Android versions) and covers the case where
        //    SpeedMonitoringActivity is not in the foreground to handle the broadcast.
        try {
            startActivity(alertIntent)
            Log.d(TAG, "✅ startActivity() called for AccidentAlertActivity")
        } catch (e: Exception) {
            Log.e(TAG, "❌ startActivity() failed: ${e.message}")
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return
        }

        // Optimized location request for immediate UI updates
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000L // Update interval: 1 second
        )
            .setMinUpdateIntervalMillis(500L)      // Fastest interval: 500ms
            .setMaxUpdateDelayMillis(1500L)        // Max delay: 1.5s (batching window)
            .setMinUpdateDistanceMeters(0f)        // No minimum distance - update on every GPS tick
            .setWaitForAccurateLocation(false)     // Don't wait for high accuracy
            .build()

        Log.d(TAG, "📡 Location request configured:")
        Log.d(TAG, "   • Interval: 1000ms (1 second)")
        Log.d(TAG, "   • Fastest interval: 500ms")
        Log.d(TAG, "   • Max delay: 1000ms")
        Log.d(TAG, "   • Min distance: 0 meters (no restriction)")
        Log.d(TAG, "   • Priority: HIGH_ACCURACY")
        Log.d(TAG, "   • Wait for accuracy: false")

        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback, Looper.getMainLooper()
        )
        // Do NOT use lastLocation on startup — it can be hours old and would feed
        // stale coordinates and a false speed=0 into the accident detector.
        // The first real GPS fix arrives within a few seconds via locationCallback.
    }

    private fun broadcastSpeedUpdate(
        speed: Float, latitude: Double, longitude: Double, isAccident: Boolean
    ) {
        val intent = Intent(Constants.ACTION_SPEED_UPDATE).apply {
            setPackage(packageName)
            putExtra(Constants.EXTRA_SPEED, speed)
            putExtra(Constants.EXTRA_LATITUDE, latitude)
            putExtra(Constants.EXTRA_LONGITUDE, longitude)
            putExtra(Constants.EXTRA_IS_ACCIDENT, isAccident)
        }
        sendBroadcast(intent)
        Log.d(TAG, "Broadcast sent: speed=$speed km/h")
    }

    private fun updateNotification(speed: Float) {
        try {
            val notification = NotificationHelper.createForegroundNotification(this, speed)
            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(Constants.FOREGROUND_SERVICE_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Notification update failed: ${e.message}")
        }
    }

    /**
     * Runs a realistic accident simulation at a given cruise speed.
     *
     * Profile (each entry is [speed, delayMs]):
     *   - Gradual ramp up with natural ±2 km/h jitter (1s ticks)
     *   - Steady cruise with minor fluctuations (1s ticks)
     *   - Driver reacts: hard braking in 3 steps (500ms ticks) — still above 0
     *   - Impact: drops to 0 (500ms tick) — triggers deceleration detection
     *   - Stopped for 6+ seconds (1s ticks) — triggers stop confirmation → alert
     *
     * Detector constraint: the drop from last non-zero speed to 0 must be
     * >25 km/h within 2 seconds. Using 500ms ticks for the brake phase keeps
     * the total brake+impact time well under 2s.
     */
    private fun simulateAccidentAt(cruiseSpeed: Float, label: String) {
        accidentDetector.reset()
        accidentBroadcastSent.set(false)
        isSimulation.set(true)

        val baseLat = if (currentLatitude != 0.0) currentLatitude else 12.9716
        val baseLon = if (currentLongitude != 0.0) currentLongitude else 77.5946

        // Build a realistic speed+delay profile
        // Each pair: [speed km/h, delay ms before next tick]
        val c = cruiseSpeed
        val profile = mutableListOf<Pair<Float, Long>>()

        // --- Ramp up (1s ticks, natural acceleration) ---
        val steps = when {
            c >= 90f -> listOf(15f, 30f, 45f, 58f, 70f, 82f, 90f, c)
            c >= 55f -> listOf(10f, 20f, 32f, 44f, 54f, c)
            else     -> listOf(10f, 20f, 35f, c)
        }
        steps.forEach { profile.add(Pair(it, 1000L)) }

        // --- Cruise (3 ticks with tiny ±1-2 km/h jitter, 1s ticks) ---
        profile.add(Pair(c + 1f, 1000L))
        profile.add(Pair(c - 1f, 1000L))
        profile.add(Pair(c,      1000L))

        // --- Hard braking phase (500ms ticks — driver reacts) ---
        // Drop in 3 steps: 75% → 50% → 30% of cruise, then impact
        // All within ~1.5s so detector sees the full drop in its 2s window
        profile.add(Pair(c * 0.75f, 500L))
        profile.add(Pair(c * 0.50f, 500L))
        profile.add(Pair(c * 0.30f, 500L))

        // --- Impact (500ms tick — sudden stop) ---
        profile.add(Pair(0f, 500L))

        // --- Stopped (1s ticks, 6 ticks = 6s > 5s confirmation window) ---
        repeat(7) { profile.add(Pair(0f, 1000L)) }

        // Guaranteed alert tick — fires after the full stop phase regardless of detector state.
        // Marked with -1f as a sentinel value.
        profile.add(Pair(-1f, 0L))

        // Index of first 0f tick — location freezes here
        val crashTickIndex = profile.indexOfFirst { it.first == 0f }

        val handler = simulationHandler
        var tick = 0
        var alertFired = false  // guard: only fire the alert once per simulation

        fun scheduleTick() {
            if (tick >= profile.size) {
                isSimulation.set(false)
                Log.d(TAG, "🧪 [$label] Simulation complete")
                return
            }

            val (speed, delay) = profile[tick]

            // Sentinel tick (-1f) = guaranteed alert after stop phase
            if (speed < 0f) {
                isSimulation.set(false)
                Log.d(TAG, "🧪 [$label] Sentinel tick — forcing alert if not already fired")
                if (!alertFired && accidentBroadcastSent.compareAndSet(false, true)) {
                    alertFired = true
                    Log.d(TAG, "🧪 [$label] Forcing alert via sentinel")
                    broadcastAccidentConfirmed()
                }
                Log.d(TAG, "🧪 [$label] Simulation complete")
                return
            }

            val lat = if (tick < crashTickIndex) baseLat + (tick * 0.00012)
                      else baseLat + (crashTickIndex * 0.00012)

            val location = android.location.Location("simulation").apply {
                latitude = lat
                longitude = baseLon
                time = System.currentTimeMillis()
                accuracy = 3f
                this.speed = speed / 3.6f
            }

            currentSpeed = speed
            broadcastSpeedUpdate(speed, lat, baseLon, false)
            Log.d(TAG, "🧪 [$label] tick $tick: ${speed.toInt()} km/h (next in ${delay}ms)")
            accidentDetector.processGPSUpdate(speed, location)

            // If the detector confirmed an accident and we haven't fired the alert yet, do it now.
            // This is a direct guard so the alert always fires during simulation regardless of
            // whether the listener callback or broadcast path is working.
            if (!alertFired && accidentDetector.isAccidentConfirmed()) {
                alertFired = true
                if (accidentBroadcastSent.compareAndSet(false, true)) {
                    Log.d(TAG, "🧪 [$label] Accident confirmed at tick $tick — launching alert")
                    broadcastAccidentConfirmed()
                }
            }

            tick++
            handler.postDelayed({ scheduleTick() }, delay)
        }

        Log.d(TAG, "🧪 [$label] Simulation started at ${cruiseSpeed.toInt()} km/h")
        Toast.makeText(this, "[$label] Simulating crash from ${cruiseSpeed.toInt()} km/h...", Toast.LENGTH_LONG).show()
        scheduleTick()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")

        // Log final statistics
        improvedSpeedCalculator.logStatistics()

        fusedLocationClient.removeLocationUpdates(locationCallback)
        improvedSpeedCalculator.reset()
        accidentBroadcastSent.set(false)
        accidentDetector.reset()
        simulationHandler.removeCallbacksAndMessages(null)
        simulatedSpeedOverride = -1f
        isSimulation.set(false)

        try {
            unregisterReceiver(accidentCancelledReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already unregistered: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}