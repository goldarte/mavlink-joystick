package com.eugenehammer.mavlinkjoystikkmp.ui.settings.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.eugenehammer.mavlinkjoystikkmp.ui.settings.SettingsScreenState
import com.eugenehammer.mavlinkjoystikkmp.utils.CurveUtils

private val AccentColor = Color(0xFFFF5C8D)

@Composable
fun StickCurveSettingsScreen(
    state: SettingsScreenState.StickCurveSettingsState,
    onAxisSelected: (SettingsScreenState.StickCurveSettingsState.StickAxis) -> Unit,
    onWeightChange: (Float) -> Unit,
    onWeightChangeFinished: () -> Unit,
    onOffsetChange: (Float) -> Unit,
    onOffsetChangeFinished: () -> Unit,
    onExpoChange: (Float) -> Unit,
    onExpoChangeFinished: () -> Unit,
) {
    val params = when (state.selectedAxis) {
        SettingsScreenState.StickCurveSettingsState.StickAxis.Roll -> state.rollParams
        SettingsScreenState.StickCurveSettingsState.StickAxis.Pitch -> state.pitchParams
        SettingsScreenState.StickCurveSettingsState.StickAxis.Yaw -> state.yawParams
        SettingsScreenState.StickCurveSettingsState.StickAxis.Throttle -> state.throttleParams
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D)),
    ) {
        PrimaryTabRow(
            selectedTabIndex = state.selectedAxis.ordinal,
            containerColor = Color(0xFF1E1E1E),
            contentColor = AccentColor,
        ) {
            SettingsScreenState.StickCurveSettingsState.StickAxis.entries.forEach { axis ->
                Tab(
                    selected = state.selectedAxis == axis,
                    onClick = { onAxisSelected(axis) },
                    text = {
                        Text(
                            text = when (axis) {
                                SettingsScreenState.StickCurveSettingsState.StickAxis.Roll -> "ROLL"
                                SettingsScreenState.StickCurveSettingsState.StickAxis.Pitch -> "PITCH"
                                SettingsScreenState.StickCurveSettingsState.StickAxis.Yaw -> "YAW"
                                SettingsScreenState.StickCurveSettingsState.StickAxis.Throttle -> "THR"
                            }
                        )
                    },
                    selectedContentColor = AccentColor,
                    unselectedContentColor = Color(0xFFAAAAAA),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                SliderBlock(
                    title = "Weight",
                    value = params.weight,
                    valueText = "${(params.weight * 100).toInt()}%",
                    valueRange = 0f..1f,
                    onValueChange = onWeightChange,
                    onValueChangeFinished = onWeightChangeFinished,
                )

                Spacer(modifier = Modifier.height(24.dp))

                SliderBlock(
                    title = "Offset",
                    value = params.offset,
                    valueText = "${(params.offset * 100).toInt()}%",
                    valueRange = -1f..1f,
                    onValueChange = onOffsetChange,
                    onValueChangeFinished = onOffsetChangeFinished,
                )

                Spacer(modifier = Modifier.height(24.dp))

                SliderBlock(
                    title = "Expo",
                    value = params.expo,
                    valueText = "${(params.expo * 100).toInt()}%",
                    valueRange = -1f..1f,
                    onValueChange = onExpoChange,
                    onValueChangeFinished = onExpoChangeFinished,
                )

            }

            Spacer(modifier = Modifier.width(24.dp))

            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .background(Color(0xFF121212)),
                contentAlignment = Alignment.Center,
            ) {
                CurveGraph(
                    weight = params.weight,
                    offset = params.offset,
                    expo = params.expo,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderBlock(
    title: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {

    Column {

        Text(
            text = "$title: $valueText",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        val sliderColors = SliderDefaults.colors(
            thumbColor = AccentColor,
            activeTrackColor = AccentColor,
        )

        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = sliderColors,
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    drawStopIndicator = null,
                    colors = sliderColors
                )
            }
        )
    }
}

private val GridColor = Color(0x33FFFFFF)
private val AxisColor = Color(0x88FFFFFF)
private val CurveColor = Color(0xFFFF5C8D)

@Composable
fun CurveGraph(
    weight: Float,
    offset: Float,
    expo: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            // GRID
            for (i in 0..10) {
                val x = (w / 10f) * i
                drawLine(
                    color = GridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                )

                val y = (h / 10f) * i
                drawLine(
                    color = GridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                )
            }

            // AXES
            drawLine(
                color = AxisColor,
                start = Offset(cx, 0f),
                end = Offset(cx, h),
                strokeWidth = 2f,
            )

            drawLine(
                color = AxisColor,
                start = Offset(0f, cy),
                end = Offset(w, cy),
                strokeWidth = 2f,
            )

            // CURVE
            val path = Path()
            val steps = 50

            for (i in 0..steps) {
                val xInput = (i.toFloat() / steps) * 2f - 1f // -1..1
                val yOutput = CurveUtils.applyCurve(
                    xInput,
                    weight,
                    offset,
                    expo
                )

                val px = ((xInput + 1f) / 2f) * w
                val py = h - ((yOutput + 1f) / 2f) * h

                if (i == 0) {
                    path.moveTo(px, py)
                } else {
                    path.lineTo(px, py)
                }
            }

            drawPath(
                path = path,
                color = CurveColor,
                style = Stroke(width = 4f),
            )
        }
    }
}