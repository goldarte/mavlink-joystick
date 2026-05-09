package com.eugenehammer.mavlinkjoystikkmp.di

import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkManager
import org.koin.dsl.module

val sharedModule = module {
    single { MavlinkManager() }
}