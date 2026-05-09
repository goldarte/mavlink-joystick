package com.eugenehammer.mavlinkjoystikkmp.di

import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkManager
import org.koin.dsl.module

actual val platformModule = module {
    single { MavlinkManager(get()) }
}