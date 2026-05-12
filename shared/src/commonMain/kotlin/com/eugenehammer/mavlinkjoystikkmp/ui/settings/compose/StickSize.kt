package com.eugenehammer.mavlinkjoystikkmp.ui.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eugenehammer.mavlinkjoystikkmp.ui.settings.SettingsScreenState

@Composable
fun StickSizeSettingsScreen(
    state: SettingsScreenState.StickSizeState,
    onLeftStickFactorChanged: (Float) -> Unit,
    onLeftStickFactorDragEnded: () -> Unit,
    onRightStickFactorChanged: (Float) -> Unit,
    onRightStickFactorDragEnded: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {

        Text(
            text = "Joystick Size / Sensitivity",
            color = Color(0xFFAAAAAA),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(16.dp))

        StickSlider(
            title = "Left Stick",
            factor = state.leftFactor,
            onChange = onLeftStickFactorChanged,
            onDragEnded = onLeftStickFactorDragEnded
        )

        Spacer(Modifier.height(16.dp))

        StickSlider(
            title = "Right Stick",
            factor = state.rightFactor,
            onChange = onRightStickFactorChanged,
            onDragEnded = onRightStickFactorDragEnded
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Adjust the physical area used by each joystick independently. Lower values make the sticks smaller and more sensitive.",
            color = Color(0xFF666666),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun StickSlider(
    title: String,
    factor: Float,
    onChange: (Float) -> Unit,
    onDragEnded: () -> Unit,
) {
    val percent = (factor * 100).toInt()

    Text(
        text = "$title Stick: $percent%",
        color = Color.White,
        fontSize = 16.sp,
        fontFamily = FontFamily.Monospace,
    )

    Spacer(Modifier.height(8.dp))

    Slider(
        value = factor,
        onValueChange = onChange,
        onValueChangeFinished = onDragEnded,
        valueRange = 0.5f..1.0f,
        steps = 49, // roughly same density as SeekBar(max=50)
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFFFF5C8D),
            activeTrackColor = Color(0xFFFF5C8D),
        ),
    )
}