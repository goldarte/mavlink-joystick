package com.eugenehammer.mavlinkjoystikkmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.eugenehammer.mavlinkjoystikkmp.ui.JoystickScreen

@Composable
@Preview
fun App() {
    MaterialTheme {
        Scaffold {
            JoystickScreen()
        }
    }
}