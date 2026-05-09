package com.eugenehammer.mavlinkjoystikkmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.eugenehammer.mavlinkjoystikkmp.di.platformModule
import com.eugenehammer.mavlinkjoystikkmp.di.sharedModule
import com.eugenehammer.mavlinkjoystikkmp.ui.flight.compose.FlightScreen
import com.eugenehammer.mavlinkjoystikkmp.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

@Serializable
private data object Flight : NavKey

@Serializable
private data object Settings : NavKey

private val config = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Flight::class, Flight.serializer())
            subclass(Settings::class, Settings.serializer())
        }
    }
}

@Composable
@Preview
fun App() {
    KoinApplication(
        configuration = koinConfiguration(declaration = { modules(sharedModule, platformModule) }),
        content = {
            MaterialTheme {
                val backStack = rememberNavBackStack(config, Flight)
                val entryProvider = entryProvider {
                    entry<Flight> {
                        FlightScreen()
                    }
                    entry<Settings> {
                        SettingsScreen()
                    }
                }

                NavDisplay(
                    backStack = backStack,
                    entryProvider = entryProvider,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator()
                    )
                )
            }
        })
}