// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Repository: https://github.com/goldarte/mavlink-joystick
// Assisted by Gemini

package com.goldarte.mavlinkjoystick

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.goldarte.mavlinkjoystick.mavlink.MavlinkManager
import java.util.*

class MavlinkConsoleFragment : Fragment() {

    private lateinit var tvConsole: TextView
    private lateinit var tvTitle: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var etCommand: EditText
    private lateinit var btnSend: Button
    private lateinit var btnOrientation: Button
    private val logBuilder = StringBuilder()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_mavlink_console, container, false)
        tvConsole = view.findViewById(R.id.tvConsole)
        tvTitle = view.findViewById(R.id.tvTitle)
        scrollView = view.findViewById(R.id.scrollView)
        etCommand = view.findViewById(R.id.etCommand)
        btnSend = view.findViewById(R.id.btnSend)
        btnOrientation = view.findViewById(R.id.btnOrientation)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateTitleVisibility(resources.configuration.orientation)

        val mavlink = MavlinkManager.getInstance(requireContext())
        mavlink.onSerialControlReceived = { data ->
            activity?.runOnUiThread {
                val text = String(data, Charsets.UTF_8)
                appendRawText(text)
            }
        }

        btnSend.setOnClickListener {
            sendMessage(mavlink)
        }

        btnOrientation.setOnClickListener {
            toggleOrientation()
        }

        etCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage(mavlink)
                true
            } else {
                false
            }
        }

        scrollView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                scrollView.post {
                    scrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateTitleVisibility(newConfig.orientation)
    }

    private fun updateTitleVisibility(orientation: Int) {
        tvTitle.visibility = if (orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun toggleOrientation() {
        activity?.let {
            val currentOrientation = it.resources.configuration.orientation
            if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }

    private fun sendMessage(mavlink: MavlinkManager) {
        val text = etCommand.text.toString().trim()
        if (text.isNotEmpty()) {
            mavlink.sendSerialControl(text)
            appendRawText("> $text\n")
            etCommand.setText("")
        }
    }

    private fun appendRawText(text: String) {
        logBuilder.append(text)
        
        if (isAdded) {
            tvConsole.text = logBuilder.toString()
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val mavlink = MavlinkManager.getInstance(requireContext())
        mavlink.onSerialControlReceived = null
    }
}
