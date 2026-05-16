package com.example.accident_detection3.emergency

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.accident_detection3.data.AlertStatePrefs
import com.example.accident_detection3.data.PhoneAuthPrefs
import com.example.accident_detection3.databinding.ActivityAccidentAlertBinding
import com.example.accident_detection3.firebase.FirebaseDatabaseHelper
import com.example.accident_detection3.firebase.FirebaseFunctionsHelper
import com.example.accident_detection3.notification.EmergencyNotifier
import com.example.accident_detection3.util.Constants
import com.example.accident_detection3.util.NotificationHelper

/**
 * Full-screen accident alert activity with 30-second countdown timer.
 *
 * Launched via a full-screen intent notification from TrackingService.
 * This is the correct approach for Android 10+ where background services
 * cannot call startActivity() directly.
 *
 * Shows "I'm Safe" and "Send Help" buttons.
 * Auto-triggers emergency SMS after 30 seconds if no response.
 */
class AccidentAlertActivity : AppCompatActivity() {

    private val TAG = "AccidentAlertActivity"
    private lateinit var binding: ActivityAccidentAlertBinding
    private var countDownTimer: CountDownTimer? = null
    private var emergencyTriggered = false
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null

    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var speed: Float = 0f
    private lateinit var alertStatePrefs: AlertStatePrefs

    // Listens for notification button taps that resolve the alert externally
    private val resolutionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_ACCIDENT_CANCELLED -> {
                    // "I'm Safe" was tapped from the notification — finish without re-broadcasting
                    if (!emergencyTriggered) {
                        countDownTimer?.cancel()
                        Log.d(TAG, "Alert cancelled via notification button")
                        finish()
                    }
                }
                Constants.ACTION_EMERGENCY_TRIGGERED -> {
                    // "Send Help" was tapped from the notification — finish the activity
                    if (!emergencyTriggered) {
                        emergencyTriggered = true
                        countDownTimer?.cancel()
                        Log.d(TAG, "Emergency triggered via notification button")
                        finish()
                    }
                }
            }
        }
    }

    companion object {
        const val COUNTDOWN_DURATION = 30000L
        const val COUNTDOWN_INTERVAL = 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Must set window flags BEFORE setContentView so the activity
        // appears over the lock screen and wakes the display.
        setupWindowFlags()

        binding = ActivityAccidentAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alertStatePrefs = AlertStatePrefs(this)

        latitude = intent.getDoubleExtra(Constants.EXTRA_LATITUDE, 0.0)
        longitude = intent.getDoubleExtra(Constants.EXTRA_LONGITUDE, 0.0)
        speed = intent.getFloatExtra(Constants.EXTRA_SPEED, 0f)

        // Persist alert state so NotificationActivity can show the live countdown
        alertStatePrefs.setAlertActive(System.currentTimeMillis(), latitude, longitude, speed)

        Log.d(TAG, "AccidentAlertActivity started — lat=$latitude lon=$longitude speed=$speed km/h")

        setupUI()
        startCountdown()
        startAlertFeedback()

        // Listen for resolution events from notification action buttons
        val filter = IntentFilter().apply {
            addAction(Constants.ACTION_ACCIDENT_CANCELLED)
            addAction(Constants.ACTION_EMERGENCY_TRIGGERED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(resolutionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(resolutionReceiver, filter)
        }

        // Handle back button — treat same as "I'm Safe"
        onBackPressedDispatcher.addCallback(this) {
            Log.d(TAG, "Back button pressed — cancelling alert")
            cancelAlert()
        }
    }

    // onNewIntent handles the singleInstance case where the activity is already running.
    // appcompat 1.7.0+ uses non-nullable Intent to match the platform API 35 signature.
    // Do NOT restart the countdown — it's already running from onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Only update location if a newer/better fix is provided
        val newLat = intent.getDoubleExtra(Constants.EXTRA_LATITUDE, latitude)
        val newLon = intent.getDoubleExtra(Constants.EXTRA_LONGITUDE, longitude)
        if (newLat != 0.0 || newLon != 0.0) {
            latitude = newLat
            longitude = newLon
        }
        speed = intent.getFloatExtra(Constants.EXTRA_SPEED, speed)
        Log.d(TAG, "onNewIntent — updated location: $latitude, $longitude (countdown continues)")
        binding.tvLocationInfo.text = formatLocation()
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupUI() {
        binding.tvLocationInfo.text = formatLocation()

        binding.btnImSafe.setOnClickListener {
            Log.d(TAG, "User pressed 'I'm Safe'")
            cancelAlert()
        }

        binding.btnSendHelp.setOnClickListener {
            Log.d(TAG, "User pressed 'Send Help'")
            triggerEmergency()
        }
    }

    private fun startCountdown() {
        countDownTimer = object : CountDownTimer(COUNTDOWN_DURATION, COUNTDOWN_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000).toInt()
                binding.tvCountdown.text = secondsRemaining.toString()
                val progress = ((COUNTDOWN_DURATION - millisUntilFinished).toFloat() / COUNTDOWN_DURATION * 100).toInt()
                binding.progressBar.progress = progress
                Log.d(TAG, "Countdown: $secondsRemaining s remaining")

                // Escalating beep: fast double-beep in last 10s, single beep otherwise
                if (secondsRemaining <= 10) {
                    beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)
                    binding.root.postDelayed({ beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150) }, 200)
                } else {
                    beep(ToneGenerator.TONE_PROP_BEEP, 200)
                }

                // Keep notification in sync — shows countdown + action buttons
                NotificationHelper.updateAccidentNotificationCountdown(
                    this@AccidentAlertActivity, latitude, longitude, speed, secondsRemaining
                )
            }

            override fun onFinish() {
                Log.d(TAG, "Countdown finished — auto-sending emergency alert")
                binding.tvCountdown.text = "0"
                binding.progressBar.progress = 100
                triggerEmergency()
            }
        }.start()
    }

    private fun cancelAlert() {
        if (emergencyTriggered) return

        countDownTimer?.cancel()
        stopAlertFeedback()
        alertStatePrefs.clearAlert()
        NotificationHelper.dismissAccidentAlert(this)

        Toast.makeText(this, "You're safe — no emergency alert sent.", Toast.LENGTH_LONG).show()

        sendBroadcast(Intent(Constants.ACTION_ACCIDENT_CANCELLED).apply {
            setPackage(packageName)
        })

        Log.d(TAG, "Alert cancelled by user")
        finish()
    }

    private fun triggerEmergency() {
        if (emergencyTriggered) return
        emergencyTriggered = true
        countDownTimer?.cancel()
        stopAlertFeedback()
        alertStatePrefs.clearAlert()

        binding.btnImSafe.isEnabled = false
        binding.btnSendHelp.isEnabled = false
        binding.tvTitle.text = "Sending Emergency\nAlert..."
        binding.tvCountdown.text = "!"
        binding.tvSubtitle.text = "Please wait..."

        Toast.makeText(this, "Emergency alert sent to your contacts.", Toast.LENGTH_LONG).show()

        Log.d(TAG, "Triggering emergency — sending SMS and saving to Firebase")

        // Validate coordinates before sending — (0,0) means GPS hasn't fixed yet
        val hasValidLocation = latitude != 0.0 || longitude != 0.0
        if (!hasValidLocation) {
            Log.w(TAG, "⚠️ Location is (0,0) — GPS not fixed. SMS will omit map link.")
        }

        // Send SMS with Google Maps link to all emergency contacts
        try {
            EmergencyNotifier(this).sendEmergencyAlertSMS(latitude, longitude)
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed: ${e.message}", e)
        }

        // Save to Firebase and trigger Cloud Functions
        saveEmergencyToFirebase()

        // Dismiss the ongoing notification
        NotificationHelper.dismissAccidentAlert(this)

        // Broadcast so TrackingService resets its state
        sendBroadcast(Intent(Constants.ACTION_EMERGENCY_TRIGGERED).apply {
            setPackage(packageName)
            putExtra(Constants.EXTRA_LATITUDE, latitude)
            putExtra(Constants.EXTRA_LONGITUDE, longitude)
            putExtra(Constants.EXTRA_SPEED, speed)
        })

        // Navigate to EmergencyInfoActivity after a short delay
        binding.root.postDelayed({ showEmergencyInfo() }, 2000)
    }

    private fun saveEmergencyToFirebase() {
        try {
            // Use local phone auth (app uses PhoneAuthPrefs, not Firebase Auth)
            val userId = PhoneAuthPrefs(this).getCurrentUser() ?: run {
                Log.w(TAG, "No logged-in user — skipping Firebase save")
                return
            }
            val databaseHelper = FirebaseDatabaseHelper()
            val emergency = FirebaseDatabaseHelper.EmergencyEvent(
                userId = userId,
                timestamp = System.currentTimeMillis(),
                latitude = latitude,
                longitude = longitude,
                speed = speed,
                type = "accident",
                resolved = false
            )
            databaseHelper.saveEmergencyEvent(userId, emergency) { success, emergencyId ->
                if (success && emergencyId != null) {
                    Log.d(TAG, "✅ Emergency saved: $emergencyId")
                    // Guard: activity may have been destroyed before the async callback fires
                    if (!isFinishing && !isDestroyed) {
                        callEmergencyServicesFunction(userId, emergencyId)
                    } else {
                        Log.w(TAG, "Activity destroyed before Firebase callback — skipping Cloud Function")
                    }
                } else {
                    Log.e(TAG, "❌ Firebase save failed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase exception: ${e.message}", e)
        }
    }

    private fun callEmergencyServicesFunction(userId: String, emergencyId: String) {
        try {
            FirebaseFunctionsHelper().notifyEmergencyServices(
                userId = userId,
                emergencyId = emergencyId,
                latitude = latitude,
                longitude = longitude
            ) { success, message ->
                Log.d(TAG, if (success) "✅ Cloud Function: $message" else "❌ Cloud Function failed: $message")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud Function exception: ${e.message}", e)
        }
    }

    private fun showEmergencyInfo() {
        if (isFinishing || isDestroyed) return
        startActivity(Intent(this, EmergencyInfoActivity::class.java).apply {
            putExtra(Constants.EXTRA_LATITUDE, latitude)
            putExtra(Constants.EXTRA_LONGITUDE, longitude)
            putExtra(Constants.EXTRA_SPEED, speed)
        })
        finish()
    }

    private fun startAlertFeedback() {
        // Vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Repeating pattern: wait 0ms, vibrate 500ms, pause 500ms — repeat indefinitely
        val pattern = longArrayOf(0, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        // ToneGenerator for beeps (alarm stream, full volume)
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator init failed: ${e.message}")
        }
    }

    private fun beep(tone: Int, durationMs: Int) {
        try {
            toneGenerator?.startTone(tone, durationMs)
        } catch (e: Exception) {
            Log.w(TAG, "Beep failed: ${e.message}")
        }
    }

    private fun stopAlertFeedback() {
        vibrator?.cancel()
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun formatLocation() = "${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"

    override fun onDestroy() {
        super.onDestroy()
        if (!emergencyTriggered) {
            countDownTimer?.cancel()
        }
        stopAlertFeedback()
        try {
            unregisterReceiver(resolutionReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "resolutionReceiver already unregistered")
        }
        Log.d(TAG, "AccidentAlertActivity destroyed (emergencyTriggered=$emergencyTriggered)")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Treat back button the same as "I'm Safe" — cancel cleanly
        Log.d(TAG, "Back button pressed — cancelling alert")
        cancelAlert()
    }
}
