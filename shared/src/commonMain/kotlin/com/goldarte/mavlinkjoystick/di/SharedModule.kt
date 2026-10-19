// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Copyright (c) 2026 Eugene Molotkov
// Repository: https://github.com/goldarte/mavlink-joystick

package com.goldarte.mavlinkjoystick.di

import com.goldarte.mavlinkjoystick.data.AppSettings
import com.goldarte.mavlinkjoystick.ui.console.MavlinkConsoleViewModel
import com.goldarte.mavlinkjoystick.ui.flight.FlightViewModel
import com.goldarte.mavlinkjoystick.ui.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharedModule = module {
    single { AppSettings(get()) }
    viewModel { FlightViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { MavlinkConsoleViewModel(get(), get()) }
}
