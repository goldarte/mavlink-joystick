// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Repository: https://github.com/goldarte/mavlink-joystick
// Assisted by Gemini

package com.goldarte.mavlinkjoystick

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.goldarte.mavlinkjoystick.mavlink.MavlinkManager
import com.goldarte.mavlinkjoystick.views.ArtificialHorizonView
import com.goldarte.mavlinkjoystick.views.JoystickView
import com.goldarte.mavlinkjoystick.views.CompassView
import com.goldarte.mavlinkjoystick.utils.CurveUtils

import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var mavlink: MavlinkManager
    private lateinit var leftStick: JoystickView
    private lateinit var rightStick: JoystickView
    private lateinit var horizon: ArtificialHorizonView
    private lateinit var compass: CompassView
    private lateinit var tvAutopilotName: TextView
    private lateinit var btnArm: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvFlightMode: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var btnConnect: Button

    // Local axis values (updated by joystick callbacks)
    private var throttleRaw = 0f
    private var yawRaw = 0f
    private var pitchRaw = 0f
    private var rollRaw = 0f

    // Curved values
    private var throttle = 0f
    private var yaw = 0f
    private var pitch = 0f
    private var roll = 0f

    private var rollWeight = 1.0f
    private var rollOffset = 0.0f
    private var rollExpo = 0.0f

    private var pitchWeight = 1.0f
    private var pitchOffset = 0.0f
    private var pitchExpo = 0.0f

    private var yawWeight = 1.0f
    private var yawOffset = 0.0f
    private var yawExpo = 0.0f

    private var thrWeight = 1.0f
    private var thrOffset = 0.0f
    private var thrExpo = 0.0f

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
        tvAutopilotName      = findViewById(R.id.tvAutopilotName)
        btnArm               = findViewById(R.id.btnArm)
        tvStatus             = findViewById(R.id.tvArmStatus)
        tvBattery            = findViewById(R.id.tvBattery)
        tvFlightMode         = findViewById(R.id.tvFlightMode)
        tvConnectionStatus   = findViewById(R.id.tvConnectionStatus)
        btnConnect           = findViewById(R.id.btnConnect)

        // ── Left stick: Throttle (Y, no spring) + Yaw (X, spring) ─────────────
        leftStick.isThrottleMode = true
        leftStick.onChanged = { x, y ->
            yawRaw      = x
            throttleRaw = y
            pushChannels()
        }

        // ── Right stick: Pitch (Y) + Roll (X) ─────────────────────────────────
        rightStick.isThrottleMode = false
        rightStick.onChanged = { x, y ->
            rollRaw  = x
            pitchRaw = y
            pushChannels()
        }

        // ── MAVLink ──────────────────────────────────────────────────────────
        mavlink = MavlinkManager.getInstance(applicationContext)
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

        mavlink.onFlightModeReceived = { mode ->
            runOnUiThread {
                tvFlightMode.text = mode
            }
        }

        mavlink.onAutopilotNameReceived= { name ->
            runOnUiThread {
                tvAutopilotName.text = name
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
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        updateUI(armed = false, connected = false)
        mavlink.start()
    }

    override fun onResume() {
        super.onResume()
        // Full-screen immersive (repeat on resume)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        
        val oldHost = mavlink.targetHost
        val oldPort = mavlink.targetPort
        val oldListen = mavlink.listenPort
        val oldDroneSys = mavlink.droneSystemId
        val oldDroneComp = mavlink.droneComponentId
        
        loadSettings()

        // Re-start if connection settings changed
        if (mavlink.targetHost != oldHost || mavlink.targetPort != oldPort || mavlink.listenPort != oldListen 
            || mavlink.droneSystemId != oldDroneSys || mavlink.droneComponentId != oldDroneComp) {
            mavlink.stop()
            mavlink.start()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("mavlink_prefs", Context.MODE_PRIVATE)
        mavlink.targetHost = prefs.getString("host", "255.255.255.255") ?: "255.255.255.255"
        mavlink.targetPort = prefs.getInt("port", 14550)
        mavlink.listenPort = prefs.getInt("listen_port", 14550)
        mavlink.droneSystemId = prefs.getInt("drone_system_id", 1)
        mavlink.droneComponentId = prefs.getInt("drone_component_id", 1)
        
        val leftFactor = prefs.getFloat("left_stick_size_factor", 0.65f)
        val rightFactor = prefs.getFloat("right_stick_size_factor", 0.65f)
        leftStick.stickSizeFactor = leftFactor
        rightStick.stickSizeFactor = rightFactor

        val showCircular = prefs.getBoolean("show_circular_area", true)
        leftStick.showCircularArea = showCircular
        rightStick.showCircularArea = showCircular

        val showSquare = prefs.getBoolean("show_square_area", true)
        leftStick.showSquareArea = showSquare
        rightStick.showSquareArea = showSquare

        val showCircleBoundaries = prefs.getBoolean("show_circle_boundaries", false)
        leftStick.showCircleBoundaries = showCircleBoundaries
        rightStick.showCircleBoundaries = showCircleBoundaries

        val knobColorStr = prefs.getString("knob_color", "#F44336") ?: "F44336"
        val knobColor = Color.parseColor(knobColorStr)
        leftStick.knobColor = knobColor
        rightStick.knobColor = knobColor

        // Curves
        rollWeight = prefs.getFloat("roll_weight", 1.0f)
        rollOffset = prefs.getFloat("roll_offset", 0.0f)
        rollExpo = prefs.getFloat("roll_expo", 0.0f)

        pitchWeight = prefs.getFloat("pitch_weight", 1.0f)
        pitchOffset = prefs.getFloat("pitch_offset", 0.0f)
        pitchExpo = prefs.getFloat("pitch_expo", 0.0f)

        yawWeight = prefs.getFloat("yaw_weight", 1.0f)
        yawOffset = prefs.getFloat("yaw_offset", 0.0f)
        yawExpo = prefs.getFloat("yaw_expo", 0.0f)

        thrWeight = prefs.getFloat("throttle_weight", 1.0f)
        thrOffset = prefs.getFloat("throttle_offset", 0.0f)
        thrExpo = prefs.getFloat("throttle_expo", 0.0f)
    }

    override fun onDestroy() {
        super.onDestroy()
        mavlink.stop()
    }

    private fun pushChannels() {
        roll = CurveUtils.applyCurve(rollRaw, rollWeight, rollOffset, rollExpo)
        pitch = CurveUtils.applyCurve(pitchRaw, pitchWeight, pitchOffset, pitchExpo)
        yaw = CurveUtils.applyCurve(yawRaw, yawWeight, yawOffset, yawExpo)
        throttle = CurveUtils.applyCurve(throttleRaw, thrWeight, thrOffset, thrExpo)

        mavlink.setChannels(roll, pitch, throttle, yaw)
    }

    private fun updateUI(armed: Boolean, connected: Boolean) {
        // ARM button
        if (armed) {
            btnArm.text = "DISARM"
            btnArm.setBackgroundColor(Color.parseColor("#D32F2F"))
            tvStatus.text = "ARMED"
            tvStatus.setTextColor(Color.parseColor("#FF5252"))
        } else {
            btnArm.text = "ARM"
            btnArm.setBackgroundColor(Color.parseColor("#2E7D32"))
            tvStatus.text = "DISARMED"
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
