package com.eugenehammer.mavlinkjoystikkmp.di

import com.eugenehammer.mavlinkjoystikkmp.data.AppSettings
import com.eugenehammer.mavlinkjoystikkmp.ui.flight.FlightViewModel
import com.eugenehammer.mavlinkjoystikkmp.ui.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharedModule = module {
    single { AppSettings(get()) }
    viewModel { FlightViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
}