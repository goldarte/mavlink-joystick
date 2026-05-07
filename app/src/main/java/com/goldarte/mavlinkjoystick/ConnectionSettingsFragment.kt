// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Repository: https://github.com/goldarte/mavlink-joystick
// Assisted by Gemini

package com.goldarte.mavlinkjoystick

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment

class ConnectionSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_connection_settings, container, false)

        val etHost = view.findViewById<EditText>(R.id.etHost)
        val etPort = view.findViewById<EditText>(R.id.etPort)
        val etListenPort = view.findViewById<EditText>(R.id.etListenPort)
        val etDroneSystemId = view.findViewById<EditText>(R.id.etDroneSystemId)
        val etDroneComponentId = view.findViewById<EditText>(R.id.etDroneComponentId)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        val prefs = requireContext().getSharedPreferences("mavlink_prefs", Context.MODE_PRIVATE)
        etHost.setText(prefs.getString("host", "255.255.255.255"))
        etPort.setText(prefs.getInt("port", 14550).toString())
        etListenPort.setText(prefs.getInt("listen_port", 14550).toString())
        etDroneSystemId.setText(prefs.getInt("drone_system_id", 1).toString())
        etDroneComponentId.setText(prefs.getInt("drone_component_id", 1).toString())

        btnSave.setOnClickListener {
            val host = etHost.text.toString().trim()
            val port = etPort.text.toString().toIntOrNull() ?: 14550
            val listenPort = etListenPort.text.toString().toIntOrNull() ?: 14550
            val systemId = etDroneSystemId.text.toString().toIntOrNull() ?: 1
            val componentId = etDroneComponentId.text.toString().toIntOrNull() ?: 1

            prefs.edit()
                .putString("host", host)
                .putInt("port", port)
                .putInt("listen_port", listenPort)
                .putInt("drone_system_id", systemId)
                .putInt("drone_component_id", componentId)
                .apply()

            Toast.makeText(context, "Connection settings saved", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}
