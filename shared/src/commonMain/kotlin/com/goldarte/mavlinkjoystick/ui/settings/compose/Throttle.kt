package com.goldarte.mavlinkjoystick.ui.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.goldarte.mavlinkjoystick.ui.settings.SettingsScreenState

private val AccentColor = Color(0xFFFF5C8D)

@Composable
fun ThrottleSettingsScreen(
    state: SettingsScreenState.ThrottleSettingsState,
    onThrottleCenteredChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {

        Text(
            text = "Throttle Axis",
            color = Color(0xFFAAAAAA),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        ThrottleModeOption(
            text = "Throttle is at the bottom and maps from 0 to 1000. " +
                "This is better suited for multirotor in stab/acro mode or for airplanes.",
            selected = !state.isCentered,
            onSelect = { onThrottleCenteredChange(false) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        ThrottleModeOption(
            text = "Throttle is centered and mapped from -1000 to 1000. " +
                "This is better suited for multirotor in althold/poshold mode, " +
                "mobile robots or games.",
            selected = state.isCentered,
            onSelect = { onThrottleCenteredChange(true) },
        )
    }
}

@Composable
private fun ThrottleModeOption(
    text: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = AccentColor,
            ),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
