package com.goldarte.mavlinkjoystick.ui.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldarte.mavlinkjoystick.ui.settings.SettingsScreenState

@Composable
fun ConnectionSettingsScreen(
    state: SettingsScreenState.ConnectionSettingsState,
    onAutodetectCheckboxClicked: () -> Unit,
    onListenPortChanged: (String) -> Unit,
    onHostChanged: (String) -> Unit,
    onTargetPortChanged: (String) -> Unit,
    onDroneSystemIdChanged: (String) -> Unit,
    onDroneComponentIdChanged: (String) -> Unit,
    onSaveClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        CheckboxRow(
            checked = state.autoDetect,
            onCheckedChange = { onAutodetectCheckboxClicked() },
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsLabel("Listen Port")

        SettingsTextField(
            value = state.listenPort,
            onValueChange = onListenPortChanged,
            hint = "14550",
            enabled = true,
            keyboardType = KeyboardType.Number,
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingsLabel("Target IP")

        SettingsTextField(
            value = state.host,
            onValueChange = onHostChanged,
            hint = "192.168.4.1",
            enabled = !state.autoDetect,
            keyboardType = KeyboardType.Uri,
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingsLabel("Target Port")

        SettingsTextField(
            value = state.port,
            onValueChange = onTargetPortChanged,
            hint = "14550",
            enabled = !state.autoDetect,
            keyboardType = KeyboardType.Number,
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingsLabel("Target System ID")

        SettingsTextField(
            value = state.droneSystemId,
            onValueChange = onDroneSystemIdChanged,
            hint = "1",
            enabled = !state.autoDetect,
            keyboardType = KeyboardType.Number,
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingsLabel("Target Component ID")

        SettingsTextField(
            value = state.droneComponentId,
            onValueChange = onDroneComponentIdChanged,
            hint = "1",
            enabled = true,
            keyboardType = KeyboardType.Number,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSaveClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF5C8D),
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = "Save Connection Settings",
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun CheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFFFF5C8D),
                uncheckedColor = Color.White,
                checkmarkColor = Color.White,
            ),
        )

        Text(
            text = "Detect target ip/port/id from first received heartbeat",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SettingsLabel(
    text: String,
) {
    Text(
        text = text,
        color = Color(0xFFAAAAAA),
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    enabled: Boolean,
    keyboardType: KeyboardType,
) {
    val textColor = if (enabled) {
        Color.White
    } else {
        Color(0xFF666666)
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
        ),
        placeholder = {
            Text(
                text = hint,
                color = Color(0xFF555555),
                fontFamily = FontFamily.Monospace,
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,

            focusedIndicatorColor = Color(0xFF444444),
            unfocusedIndicatorColor = Color(0xFF444444),
            disabledIndicatorColor = Color(0xFF444444),

            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            disabledTextColor = textColor,

            focusedPlaceholderColor = Color(0xFF555555),
            unfocusedPlaceholderColor = Color(0xFF555555),
            disabledPlaceholderColor = Color(0xFF555555),
        ),
    )
}