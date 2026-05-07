// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Repository: https://github.com/goldarte/mavlink-joystick
// Assisted by Gemini

package com.goldarte.mavlinkjoystick

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.viewpager2.widget.ViewPager2

class SettingsActivity : AppCompatActivity() {

    private lateinit var tabConnection: TextView
    private lateinit var tabSticksSize: TextView
    private lateinit var tabSticksAppearance: TextView
    private lateinit var tabSticksCurve: TextView
    private lateinit var tabMavlinkConsole: TextView
    private lateinit var sidebar: View
    private lateinit var divider: View
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tabConnection = findViewById(R.id.tabConnection)
        tabSticksSize = findViewById(R.id.tabSticksSize)
        tabSticksAppearance = findViewById(R.id.tabSticksAppearance)
        tabSticksCurve = findViewById(R.id.tabSticksCurve)
        tabMavlinkConsole = findViewById(R.id.tabMavlinkConsole)
        sidebar = findViewById(R.id.sidebar)
        divider = findViewById(R.id.divider)
        viewPager = findViewById(R.id.viewPager)

        viewPager.adapter = SettingsPagerAdapter(this)
        viewPager.isUserInputEnabled = false // Optional: disable swiping if desired

        tabConnection.setOnClickListener { viewPager.currentItem = 0 }
        tabSticksSize.setOnClickListener { viewPager.currentItem = 1 }
        tabSticksAppearance.setOnClickListener { viewPager.currentItem = 2 }
        tabSticksCurve.setOnClickListener { viewPager.currentItem = 3 }
        tabMavlinkConsole.setOnClickListener { viewPager.currentItem = 4 }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTabs(position)
            }
        })

        updateLayoutForOrientation(resources.configuration.orientation)
        hideSystemUI()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLayoutForOrientation(newConfig.orientation)
    }

    private fun updateLayoutForOrientation(orientation: Int) {
        if (orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            sidebar.visibility = View.GONE
            divider.visibility = View.GONE
            showSystemUI()
        } else {
            sidebar.visibility = View.VISIBLE
            divider.visibility = View.VISIBLE
            hideSystemUI()
        }
    }

    private fun showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun updateTabs(position: Int) {
        val activeColor = Color.parseColor("#FF5C8D")
        val inactiveColor = Color.parseColor("#AAAAAA")

        tabConnection.setTextColor(if (position == 0) activeColor else inactiveColor)
        tabSticksSize.setTextColor(if (position == 1) activeColor else inactiveColor)
        tabSticksAppearance.setTextColor(if (position == 2) activeColor else inactiveColor)
        tabSticksCurve.setTextColor(if (position == 3) activeColor else inactiveColor)
        tabMavlinkConsole.setTextColor(if (position == 4) activeColor else inactiveColor)
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }
}
