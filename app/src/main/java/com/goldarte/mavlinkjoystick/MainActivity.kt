package com.goldarte.mavlinkjoystick

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.goldarte.mavlinkjoystick.ConnectionDialogFragment
import com.goldarte.mavlinkjoystick.mavlink.MavlinkManager
import com.goldarte.mavlinkjoystick.views.ArtificialHorizonView
import com.goldarte.mavlinkjoystick.views.JoystickView
import com.goldarte.mavlinkjoystick.views.CompassView

import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var mavlink: MavlinkManager
    private lateinit var leftStick: JoystickView
    private lateinit var rightStick: JoystickView
    private lateinit var horizon: ArtificialHorizonView
    private lateinit var compass: CompassView
    private lateinit var btnArm: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var btnConnect: Button

    // Local axis values (updated by joystick callbacks)
    private var throttle = 0f
    private var yaw = 0f
    private var pitch = 0f
    private var roll = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen immersive
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        setContentView(R.layout.activity_main)

        // Bind views
        leftStick            = findViewById(R.id.leftStick)
        rightStick           = findViewById(R.id.rightStick)
        horizon              = findViewById(R.id.artificialHorizon)
        compass              = findViewById(R.id.compass)
        btnArm               = findViewById(R.id.btnArm)
        tvStatus             = findViewById(R.id.tvArmStatus)
        tvBattery            = findViewById(R.id.tvBattery)
        tvConnectionStatus   = findViewById(R.id.tvConnectionStatus)
        btnConnect           = findViewById(R.id.btnConnect)

        // ── Left stick: Throttle (Y, no spring) + Yaw (X, spring) ─────────────
        leftStick.isThrottleMode = true
        leftStick.onChanged = { x, y ->
            yaw      = x
            throttle = y
            pushChannels()
        }

        // ── Right stick: Pitch (Y) + Roll (X) ─────────────────────────────────
        rightStick.isThrottleMode = false
        rightStick.onChanged = { x, y ->
            roll  = x
            pitch = y
            pushChannels()
        }

        // ── MAVLink ──────────────────────────────────────────────────────────
        mavlink = MavlinkManager(applicationContext)
        loadSettings()

        mavlink.onStateChanged = { armed, connected ->
            runOnUiThread { updateUI(armed, connected) }
        }

        mavlink.onAttitudeReceived = { rollDeg, pitchDeg, yawDeg ->
            runOnUiThread {
                horizon.setAttitude(rollDeg, pitchDeg)
                val heading = ((yawDeg % 360f) + 360f) % 360f
                compass.setHeading(heading)
            }
        }

        mavlink.onBatteryVoltageReceived = { voltage ->
            runOnUiThread {
                tvBattery.text = String.format(Locale.US, "%.1fV", voltage)
            }
        }

        // ── ARM button ───────────────────────────────────────────────────────
        btnArm.setOnClickListener {
            if (mavlink.isArmed) {
                mavlink.sendArmCommand(false)
                leftStick.resetToDefault()
            } else {
                mavlink.sendArmCommand(true)
            }
        }

        // Long-press ARM for safety: disarm immediately
        btnArm.setOnLongClickListener {
            mavlink.sendArmCommand(false)
            leftStick.resetToDefault()
            true
        }

        // ── Connect button ───────────────────────────────────────────────────
        btnConnect.setOnClickListener {
            ConnectionDialogFragment(
                currentHost = mavlink.targetHost,
                currentPort = mavlink.targetPort,
                currentListenPort = mavlink.listenPort
            ) { host, port, listenPort ->
                mavlink.stop()
                mavlink.targetHost = host
                mavlink.targetPort = port
                mavlink.listenPort = listenPort
                saveSettings(host, port, listenPort)
                mavlink.start()
                tvConnectionStatus.text = "Connecting to $host:$port…"
            }.show(supportFragmentManager, "connect")
        }

        updateUI(armed = false, connected = false)
        mavlink.start()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("mavlink_prefs", Context.MODE_PRIVATE)
        mavlink.targetHost = prefs.getString("target_host", "255.255.255.255") ?: "255.255.255.255"
        mavlink.targetPort = prefs.getInt("target_port", 14550)
        mavlink.listenPort = prefs.getInt("listen_port", 14550)
    }

    private fun saveSettings(host: String, port: Int, listenPort: Int) {
        val prefs = getSharedPreferences("mavlink_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("target_host", host)
            putInt("target_port", port)
            putInt("listen_port", listenPort)
            apply()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mavlink.stop()
    }

    private fun pushChannels() {
        mavlink.setChannels(roll, pitch, throttle, yaw)
    }

    private fun updateUI(armed: Boolean, connected: Boolean) {
        // ARM button
        if (armed) {
            btnArm.text = "DISARM"
            btnArm.setBackgroundColor(Color.parseColor("#D32F2F"))
            tvStatus.text = "● ARMED"
            tvStatus.setTextColor(Color.parseColor("#FF5252"))
        } else {
            btnArm.text = "ARM"
            btnArm.setBackgroundColor(Color.parseColor("#2E7D32"))
            tvStatus.text = "○ DISARMED"
            tvStatus.setTextColor(Color.parseColor("#69F0AE"))
        }

        // Connection status
        if (connected) {
            tvConnectionStatus.text = "● ${mavlink.targetHost}:${mavlink.targetPort}"
            tvConnectionStatus.setTextColor(Color.parseColor("#69F0AE"))
        } else {
            tvConnectionStatus.text = "○ NO LINK"
            tvConnectionStatus.setTextColor(Color.parseColor("#FF5252"))
        }
    }
}
