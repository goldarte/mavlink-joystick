package com.eugenehammer.mavlinkjoystikkmp.ui.flight.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eugenehammer.mavlinkjoystikkmp.ui.flight.FlightViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun FlightScreen(
    modifier: Modifier = Modifier,
    autopilotName: String = "---",
    armStatus: String = "DISARMED",
    battery: String = "--.-V",
    flightMode: String = "---",
    connectionStatus: String = "○ NO LINK",
    isArmed: Boolean = false,
    onArmClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    vm: FlightViewModel = koinViewModel()
) {
    Scaffold { paddingValues ->
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0D0D0D))
                .padding(6.dp)
        ) {

            // ══════════════ LEFT STICK ══════════════
            Box(
                modifier = Modifier
                    .weight(0.355f)
                    .fillMaxHeight()
            ) {
                Joystick(
                    modifier = Modifier.fillMaxSize()
                )

                StickLabels(
                    left = "YAW ←→",
                    right = "↑ THR ↓",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                )
            }

            Spacer(Modifier.width(6.dp))

            // ══════════════ CENTER PANEL ══════════════
            Column(
                modifier = Modifier
                    .weight(0.278f)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp),
            ) {

                // Instruments
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    ArtificialHorizonView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    CompassView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                // Autopilot name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    MonoText(
                        text = autopilotName,
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }

                // Status row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    MonoText(
                        text = armStatus,
                        color = Color(0xFF69F0AE),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )

                    MonoText(
                        text = battery,
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp
                    )

                    MonoText(
                        text = flightMode,
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }

                // ARM / DISARM button
                Button(
                    onClick = onArmClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isArmed) {
                            Color(0xFFC62828)
                        } else {
                            Color(0xFF2E7D32)
                        }
                    )
                ) {
                    Text(
                        text = if (isArmed) "DISARM" else "ARM",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.15.sp
                    )
                }

                // Bottom bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    MonoText(
                        text = connectionStatus,
                        color = Color(0xFFFF5252),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    )

                    TextButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(
                            start = 6.dp,
                            end = 0.dp
                        )
                    ) {
                        Text(
                            text = "⚙ SETTINGS",
                            color = Color(0xFFAAAAAA),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // ══════════════ RIGHT STICK ══════════════
            Box(
                modifier = Modifier
                    .weight(0.355f)
                    .fillMaxHeight()
            ) {
                Joystick(
                    modifier = Modifier.fillMaxSize()
                )

                StickLabels(
                    left = "ROLL ←→",
                    right = "↑ PITCH ↓",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun StickLabels(
    left: String,
    right: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {

        MonoText(
            text = left,
            color = Color(0x44FFFFFF),
            fontSize = 10.sp,
            modifier = Modifier.weight(1f)
        )

        MonoText(
            text = right,
            color = Color(0x44FFFFFF),
            fontSize = 10.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MonoText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.04.sp
        )
    )
}