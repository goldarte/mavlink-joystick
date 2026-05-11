package com.eugenehammer.mavlinkjoystikkmp.di

import com.eugenehammer.mavlinkjoystikkmp.ui.flight.FlightViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharedModule = module {
    viewModel { FlightViewModel(get(), get()) }
}