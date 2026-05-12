package com.eugenehammer.mavlinkjoystikkmp.ui.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eugenehammer.mavlinkjoystikkmp.ui.settings.SettingsScreenState

@Composable
fun ConsoleScreen(
    state: SettingsScreenState.ConsoleState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(16.dp),
    ) {

        Text(
            text = "MAVLINK CONSOLE",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(Modifier.height(8.dp))

        // LOG AREA
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(8.dp),
        ) {

            val scrollState = rememberScrollState()

            LaunchedEffect(state.log) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }

            Text(
                text = state.log,
                color = Color(0xFF69F0AE),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(scrollState),
            )
        }

        Spacer(Modifier.height(8.dp))

        // INPUT ROW
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(8.dp))

            TextField(
                value = state.input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type message...") },
                singleLine = true,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF2A2A2A),
                    unfocusedContainerColor = Color(0xFF2A2A2A),
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                )
            )

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = onSend,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5C8D)
                )
            ) {
                Text("SEND")
            }
        }
    }
}