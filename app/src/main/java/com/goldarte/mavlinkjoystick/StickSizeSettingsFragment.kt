// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Repository: https://github.com/goldarte/mavlink-joystick
// Assisted by Gemini

package com.goldarte.mavlinkjoystick

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment

class StickSizeSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stick_size_settings, container, false)

        val tvLeftValue = view.findViewById<TextView>(R.id.tvLeftStickSizeValue)
        val sbLeft = view.findViewById<SeekBar>(R.id.sbLeftStickSize)
        
        val tvRightValue = view.findViewById<TextView>(R.id.tvRightStickSizeValue)
        val sbRight = view.findViewById<SeekBar>(R.id.sbRightStickSize)

        val prefs = requireContext().getSharedPreferences("mavlink_prefs", Context.MODE_PRIVATE)
        
        // Left Stick
        val leftFactor = prefs.getFloat("left_stick_size_factor", 0.65f)
        sbLeft.progress = ((leftFactor - 0.5f) * 100).toInt()
        updateLabel(tvLeftValue, "Left", leftFactor)
        
        sbLeft.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val factor = 0.5f + (progress / 100f)
                updateLabel(tvLeftValue, "Left", factor)
                prefs.edit().putFloat("left_stick_size_factor", factor).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Right Stick
        val rightFactor = prefs.getFloat("right_stick_size_factor", 0.65f)
        sbRight.progress = ((rightFactor - 0.5f) * 100).toInt()
        updateLabel(tvRightValue, "Right", rightFactor)

        sbRight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val factor = 0.5f + (progress / 100f)
                updateLabel(tvRightValue, "Right", factor)
                prefs.edit().putFloat("right_stick_size_factor", factor).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        return view
    }

    private fun updateLabel(textView: TextView, side: String, factor: Float) {
        textView.text = "$side Stick: ${(factor * 100).toInt()}%"
    }
}
