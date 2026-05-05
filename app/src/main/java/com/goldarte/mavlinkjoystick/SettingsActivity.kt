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
    private lateinit var tabSticks: TextView
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tabConnection = findViewById(R.id.tabConnection)
        tabSticks = findViewById(R.id.tabSticks)
        viewPager = findViewById(R.id.viewPager)

        viewPager.adapter = SettingsPagerAdapter(this)
        viewPager.isUserInputEnabled = false // Optional: disable swiping if desired

        tabConnection.setOnClickListener { viewPager.currentItem = 0 }
        tabSticks.setOnClickListener { viewPager.currentItem = 1 }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTabs(position)
            }
        })

        hideSystemUI()
    }

    private fun updateTabs(position: Int) {
        tabConnection.setTextColor(if (position == 0) Color.parseColor("#FF5C8D") else Color.parseColor("#AAAAAA"))
        tabSticks.setTextColor(if (position == 1) Color.parseColor("#FF5C8D") else Color.parseColor("#AAAAAA"))
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
