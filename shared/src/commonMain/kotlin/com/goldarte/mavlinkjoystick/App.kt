package com.goldarte.mavlinkjoystick

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.goldarte.mavlinkjoystick.di.platformModule
import com.goldarte.mavlinkjoystick.di.sharedModule
import com.goldarte.mavlinkjoystick.ui.Orientation
import com.goldarte.mavlinkjoystick.ui.OrientationLock
import com.goldarte.mavlinkjoystick.ui.console.compose.MavlinkConsoleScreen
import com.goldarte.mavlinkjoystick.ui.flight.compose.FlightScreen
import com.goldarte.mavlinkjoystick.ui.menu.compose.MenuScreen
import com.goldarte.mavlinkjoystick.ui.settings.compose.SettingsScreen
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

@Serializable
sealed interface Route : NavKey

@Serializable
data object Flight : Route

@Serializable
data object Menu : Route

@Serializable
data object Settings : Route

@Serializable
data object MavlinkConsole : Route

@OptIn(ExperimentalSerializationApi::class)
private val config = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclassesOfSealed<Route>()
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
                val entryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
                    entry<Flight> {
                        OrientationLock(Orientation.Landscape)
                        FlightScreen(
                            openMenu = { backStack.add(Menu) }
                        )
                    }
                    entry<Menu> {
                        OrientationLock(Orientation.Landscape)
                        MenuScreen(
                            onJoystickClick = { backStack.removeLastOrNull() },
                            onConsoleClick = { backStack.add(MavlinkConsole) },
                            onSettingsClick = { backStack.add(Settings) }
                        )
                    }
                    entry<Settings> {
                        OrientationLock(Orientation.Landscape)
                        SettingsScreen(
                            goBack = { backStack.removeLastOrNull() }
                        )
                    }
                    entry<MavlinkConsole> {
                        OrientationLock(Orientation.All)
                        MavlinkConsoleScreen(
                            goBack = { backStack.removeLastOrNull() }
                        )
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