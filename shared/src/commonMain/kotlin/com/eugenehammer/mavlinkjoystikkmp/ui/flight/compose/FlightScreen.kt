package com.eugenehammer.mavlinkjoystikkmp.ui.flight.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eugenehammer.mavlinkjoystikkmp.ui.flight.FlightScreenEvent
import com.eugenehammer.mavlinkjoystikkmp.ui.flight.FlightViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FlightScreen(
    modifier: Modifier = Modifier,
    openMenu: () -> Unit,
    vm: FlightViewModel = koinViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.events.collectLatest { event ->
            when (event) {
                is FlightScreenEvent.GoToMenu -> openMenu()
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            //.systemBarsPadding()
            .padding(horizontal = 1.dp, vertical = 0.dp)
    ) {

        // ══════════════ LEFT STICK ══════════════

        Box(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight()
        ) {

            Joystick(
                modifier = Modifier.align(Alignment.Center),
                state = state.leftJoystickState,
                onChanged = vm::onLeftStickChanged,
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            ) {

                Text(
                    text = "YAW ←→",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.27f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )

                Text(
                    text = "↑ THR ↓",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.27f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(Modifier.width(7.dp))

        // ══════════════ CENTER PANEL ══════════════

        Column(
            modifier = Modifier
                .weight(0.24f)
                .fillMaxHeight()
        ) {

            // ───── Instruments ─────

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {

                ArtificialHorizon(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    rollDeg = state.rollDeg,
                    pitchDeg = state.pitchDeg,
                )

                Compass(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    heading = state.yawHeading,
                )
            }

            // ───── Autopilot name ─────

            Text(
                text = state.autopilotName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp, bottom = 20.dp),
                textAlign = TextAlign.Center,
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.04.em,
            )

            // ───── Status row ─────

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {

                Text(
                    text = if (state.armed) {
                        "ARMED"
                    } else {
                        "DISARMED"
                    },
                    color = if (state.armed) {
                        Color(0xFFFF5252)
                    } else {
                        Color(0xFF69F0AE)
                    },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.04.em,
                )

                Spacer(Modifier.width(10.dp))

                Text(
                    text = state.batteryVoltage,
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.04.em,
                )

                Spacer(Modifier.width(10.dp))

                Text(
                    text = state.flightMode,
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.04.em,
                )
            }

            // ───── ARM button ─────

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .combinedClickable(
                        onClick = vm::onArmClick,
                        onLongClick = vm::onArmLongClick,
                    )
                    .background(
                        color = if (state.armed) {
                            Color(0xFFD32F2F)
                        } else {
                            Color(0xFF2E7D32)
                        },
                        shape = RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {

                Text(
                    text = if (state.armed) {
                        "DISARM"
                    } else {
                        "ARM"
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.15.em,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(4.dp))

            // ───── Bottom bar ─────

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val connectionStatus = state.connectionStatus?.let { "● $it" } ?: "○ NO LINK"
                Text(
                    text = connectionStatus,
                    modifier = Modifier.weight(1f),
                    color = if (state.connectionStatus != null) {
                        Color(0xFF69F0AE)
                    } else {
                        Color(0xFFFF5252)
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                TextButton(
                    onClick = vm::onMenuButtonClicked,
                    contentPadding = PaddingValues(
                        horizontal = 6.dp,
                        vertical = 0.dp,
                    ),
                ) {
                    Text(
                        text = "☰ MENU",
                        color = Color(0xFFAAAAAA),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        Spacer(Modifier.width(7.dp))

        // ══════════════ RIGHT STICK ══════════════

        Box(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight()
        ) {

            Joystick(
                modifier = Modifier.align(Alignment.Center),
                state = state.rightJoystickState,
                onChanged = vm::onRightStickChanged,
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            ) {

                Text(
                    text = "ROLL ←→",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.27f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )

                Text(
                    text = "↑ PITCH ↓",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.27f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
