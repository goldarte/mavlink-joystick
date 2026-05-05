package com.goldarte.mavlinkjoystick

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ConnectionSettingsFragment()
            1 -> StickSettingsFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}
