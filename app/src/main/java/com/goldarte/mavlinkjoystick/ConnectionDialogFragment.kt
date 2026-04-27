package com.goldarte.mavlinkjoystick

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.fragment.app.DialogFragment

class ConnectionDialogFragment(
    private val currentHost: String,
    private val currentPort: Int,
    private val onConnect: (host: String, port: Int) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_connection, null)
        val etHost = view.findViewById<EditText>(R.id.etHost)
        val etPort = view.findViewById<EditText>(R.id.etPort)
        etHost.setText(currentHost)
        etPort.setText(currentPort.toString())

        return AlertDialog.Builder(requireContext(), R.style.DarkDialog)
            .setTitle("MAVLink Connection")
            .setView(view)
            .setPositiveButton("Connect") { _, _ ->
                val host = etHost.text.toString().trim().ifEmpty { currentHost }
                val port = etPort.text.toString().toIntOrNull() ?: currentPort
                onConnect(host, port)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
}
