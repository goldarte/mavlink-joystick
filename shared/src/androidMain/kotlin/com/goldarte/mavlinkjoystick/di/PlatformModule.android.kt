package com.goldarte.mavlinkjoystick.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.goldarte.mavlinkjoystick.datastore.createDataStore
import com.goldarte.mavlinkjoystick.mavlink.MavlinkManager
import com.goldarte.mavlinkjoystick.mavlink.MavlinkManagerAndroid
import org.koin.dsl.module

actual val platformModule = module {
    single<MavlinkManager> { MavlinkManagerAndroid(get(), get()) }
    single<DataStore<Preferences>> { createDataStore(get()) }
}
