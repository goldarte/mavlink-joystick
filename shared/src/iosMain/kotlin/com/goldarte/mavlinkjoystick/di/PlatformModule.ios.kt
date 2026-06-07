package com.goldarte.mavlinkjoystick.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.goldarte.mavlinkjoystick.datastore.createDataStore
import com.goldarte.mavlinkjoystick.mavlink.MavlinkManager
import com.goldarte.mavlinkjoystick.mavlink.MavlinkManagerIOS
import org.koin.dsl.module

actual val platformModule = module {
    single<MavlinkManager> { MavlinkManagerIOS(get()) }
    single<DataStore<Preferences>> { createDataStore() }
}
