package com.eugenehammer.mavlinkjoystikkmp.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.eugenehammer.mavlinkjoystikkmp.datastore.createDataStore
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkManager
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkManagerIOS
import org.koin.dsl.module

actual val platformModule = module {
    single<MavlinkManager> { MavlinkManagerIOS() }
    single<DataStore<Preferences>> { createDataStore() }
}