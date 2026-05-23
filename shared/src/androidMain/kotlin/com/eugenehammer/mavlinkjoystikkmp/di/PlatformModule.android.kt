package com.eugenehammer.mavlinkjoystikkmp.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.eugenehammer.mavlinkjoystikkmp.datastore.createDataStore
import com.eugenehammer.mavlinkjoystikkmp.mavlink.AndroidMavlinkManager
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkManager
import org.koin.dsl.module

actual val platformModule = module {
    single<MavlinkManager> { AndroidMavlinkManager(get(), get()) }
    single<DataStore<Preferences>> { createDataStore(get()) }
}
