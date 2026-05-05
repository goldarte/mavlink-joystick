package com.goldarte.mavlinkjoystick

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.goldarte.mavlinkjoystick.views.CurveGraphView
import com.google.android.material.tabs.TabLayout

class StickCurveSettingsFragment : Fragment() {

    private lateinit var graphView: CurveGraphView
    private lateinit var tvWeight: TextView
    private lateinit var sbWeight: SeekBar
    private lateinit var tvOffset: TextView
    private lateinit var sbOffset: SeekBar
    private lateinit var tvExpo: TextView
    private lateinit var sbExpo: SeekBar
    private lateinit var tabLayout: TabLayout

    private val axes = listOf("roll", "pitch", "yaw", "throttle")
    private var currentAxisIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stick_curve_settings, container, false)

        graphView = view.findViewById(R.id.curveGraphView)
        tvWeight = view.findViewById(R.id.tvWeightLabel)
        sbWeight = view.findViewById(R.id.sbWeight)
        tvOffset = view.findViewById(R.id.tvOffsetLabel)
        sbOffset = view.findViewById(R.id.sbOffset)
        tvExpo = view.findViewById(R.id.tvExpoLabel)
        sbExpo = view.findViewById(R.id.sbExpo)
        tabLayout = view.findViewById(R.id.axisTabLayout)

        setupListeners()
        loadAxisParams(0)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    currentAxisIndex = it.position
                    loadAxisParams(currentAxisIndex)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        return view
    }

    private fun setupListeners() {
        val prefs = requireContext().getSharedPreferences("mavlink_prefs", Context.MODE_PRIVATE)

        sbWeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val weight = progress / 100f
                    tvWeight.text = "Weight: ${progress}%"
                    saveParam("weight", weight)
                    updateGraph()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val offset = (progress - 100) / 100f
                    tvOffset.text = "Offset: ${progress - 100}%"
                    saveParam("offset", offset)
                    updateGraph()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbExpo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val expo = (progress - 100) / 100f
                    tvExpo.text = "Expo: ${progress - 100}%"
                    saveParam("expo", expo)
                    updateGraph()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun loadAxisParams(index: Int) {
        val axis = axes[index]
        val prefs = requireContext().getSharedPreferences("mavlink_prefs", Context.MODE_PRIVATE)
        
        val weight = prefs.getFloat("${axis}_weight", 1.0f)
        val offset = prefs.getFloat("${axis}_offset", 0.0f)
        val expo = prefs.getFloat("${axis}_expo", 0.0f)

        sbWeight.progress = (weight * 100).toInt()
        tvWeight.text = "Weight: ${(weight * 100).toInt()}%"

        sbOffset.progress = (offset * 100).toInt() + 100
        tvOffset.text = "Offset: ${(offset * 100).toInt()}%"

        sbExpo.progress = (expo * 100).toInt() + 100
        tvExpo.text = "Expo: ${(expo * 100).toInt()}%"

        updateGraph()
    }

    private fun saveParam(key: String, value: Float) {
        val axis = axes[currentAxisIndex]
        val prefs = requireContext().getSharedPreferences("mavlink_prefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("${axis}_$key", value).apply()
    }

    private fun updateGraph() {
        val weight = sbWeight.progress / 100f
        val offset = (sbOffset.progress - 100) / 100f
        val expo = (sbExpo.progress - 100) / 100f
        graphView.setParams(weight, offset, expo)
    }
}
