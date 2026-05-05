package com.goldarte.mavlinkjoystick

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment

class StickSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stick_settings, container, false)

        val tvLeftValue = view.findViewById<TextView>(R.id.tvLeftStickSizeValue)
        val sbLeft = view.findViewById<SeekBar>(R.id.sbLeftStickSize)
        
        val tvRightValue = view.findViewById<TextView>(R.id.tvRightStickSizeValue)
        val sbRight = view.findViewById<SeekBar>(R.id.sbRightStickSize)
        
        val cbCircular = view.findViewById<CheckBox>(R.id.cbShowCircularArea)
        val cbSquare = view.findViewById<CheckBox>(R.id.cbShowSquareArea)
        val cbCircleBoundaries = view.findViewById<CheckBox>(R.id.cbShowCircleBoundaries)

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

        // Circular toggle (both)
        cbCircular.isChecked = prefs.getBoolean("show_circular_area", true)
        cbCircular.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_circular_area", isChecked).apply()
        }

        // Square toggle (both)
        cbSquare.isChecked = prefs.getBoolean("show_square_area", true)
        cbSquare.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_square_area", isChecked).apply()
        }

        // Circle boundaries toggle (both)
        cbCircleBoundaries.isChecked = prefs.getBoolean("show_circle_boundaries", false)
        cbCircleBoundaries.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_circle_boundaries", isChecked).apply()
        }

        return view
    }

    private fun updateLabel(textView: TextView, side: String, factor: Float) {
        textView.text = "$side Stick: ${(factor * 100).toInt()}%"
    }
}
