// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Repository: https://github.com/goldarte/mavlink-joystick
// Assisted by Gemini

package com.goldarte.mavlinkjoystick

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ConnectionSettingsFragment()
            1 -> StickSizeSettingsFragment()
            2 -> StickAppearanceSettingsFragment()
            3 -> StickCurveSettingsFragment()
            4 -> MavlinkConsoleFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}
