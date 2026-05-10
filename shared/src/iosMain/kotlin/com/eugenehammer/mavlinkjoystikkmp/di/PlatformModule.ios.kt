package com.eugenehammer.mavlinkjoystikkmp.di

import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkManager
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkManagerIOS
import org.koin.dsl.module

actual val platformModule = module {
    single<MavlinkManager> { MavlinkManagerIOS() }
}