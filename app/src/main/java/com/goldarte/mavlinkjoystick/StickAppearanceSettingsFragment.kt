package com.goldarte.mavlinkjoystick

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

class StickAppearanceSettingsFragment : Fragment() {

    private val colors = listOf(
        "#F44336", // Red (Default)
        "#FF9800", // Orange
        "#FFEB3B", // Yellow
        "#4CAF50", // Green
        "#2196F3", // Blue
        "#9C27B0", // Purple
        "#FF5C8D", // Pink
        "#FFFFFF"  // White
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stick_appearance_settings, container, false)

        val cbCircular = view.findViewById<CheckBox>(R.id.cbShowCircularArea)
        val cbSquare = view.findViewById<CheckBox>(R.id.cbShowSquareArea)
        val cbCircleBoundaries = view.findViewById<CheckBox>(R.id.cbShowCircleBoundaries)
        val colorPickerContainer = view.findViewById<GridLayout>(R.id.colorPickerContainer)

        val prefs = requireContext().getSharedPreferences("mavlink_prefs", Context.MODE_PRIVATE)

        // Toggles
        cbCircular.isChecked = prefs.getBoolean("show_circular_area", true)
        cbCircular.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_circular_area", isChecked).apply()
        }

        cbSquare.isChecked = prefs.getBoolean("show_square_area", true)
        cbSquare.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_square_area", isChecked).apply()
        }

        cbCircleBoundaries.isChecked = prefs.getBoolean("show_circle_boundaries", false)
        cbCircleBoundaries.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_circle_boundaries", isChecked).apply()
        }

        // Color Picker
        val selectedColor = prefs.getString("knob_color", "#F44336") ?: "#F44336"
        
        colors.forEach { colorStr ->
            val colorView = View(requireContext())
            val size = (40 * resources.displayMetrics.density).toInt()
            val margin = (8 * resources.displayMetrics.density).toInt()
            
            val params = GridLayout.LayoutParams()
            params.width = size
            params.height = size
            params.setMargins(0, 0, margin, margin)
            colorView.layoutParams = params
            
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.OVAL
            shape.setColor(Color.parseColor(colorStr))
            if (colorStr == selectedColor) {
                shape.setStroke((3 * resources.displayMetrics.density).toInt(), Color.WHITE)
            }
            colorView.background = shape
            
            colorView.setOnClickListener {
                prefs.edit().putString("knob_color", colorStr).apply()
                // Refresh colors
                updateColorPicker(colorPickerContainer, colorStr)
            }
            
            colorPickerContainer.addView(colorView)
        }

        return view
    }

    private fun updateColorPicker(container: GridLayout, selectedColor: String) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val colorStr = colors[i]
            val shape = child.background as GradientDrawable
            if (colorStr == selectedColor) {
                shape.setStroke((3 * resources.displayMetrics.density).toInt(), Color.WHITE)
            } else {
                shape.setStroke(0, Color.TRANSPARENT)
            }
        }
    }
}
