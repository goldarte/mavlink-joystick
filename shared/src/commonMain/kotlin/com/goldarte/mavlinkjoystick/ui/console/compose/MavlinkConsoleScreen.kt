// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Copyright (c) 2026 Eugene Molotkov
// Repository: https://github.com/goldarte/mavlink-joystick

package com.goldarte.mavlinkjoystick.ui.console.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goldarte.mavlinkjoystick.ui.ToggleOrientationButton
import com.goldarte.mavlinkjoystick.ui.console.MavlinkConsoleViewModel
import org.koin.compose.viewmodel.koinViewModel

private val BackgroundColor = Color(0xFF0D0D0D)
private val SurfaceColor = Color(0xFF1E1E1E)

@Composable
fun MavlinkConsoleScreen(
    goBack: () -> Unit,
    vm: MavlinkConsoleViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        // In landscape the on-screen keyboard takes most of the height, so the header is
        // hidden while it is open and the console grows into the freed space.
        val isLandscape = maxWidth > maxHeight
        val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val showToolbar = !(isLandscape && isImeVisible)

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                // Shrink the whole screen above the on-screen keyboard instead of letting the
                // platform pan the window: the toolbar stays put and the input row sits right
                // above the keyboard.
                .imePadding()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                },
            containerColor = BackgroundColor,
            topBar = { if (showToolbar) ConsoleToolbar(onBack = goBack) },
            bottomBar = {
                ConsoleInputBar(
                    input = state.input,
                    onInputChange = vm::onInputChange,
                    onSend = vm::sendConsoleMessage,
                    onToggleOrientation = vm::toggleOrientation,
                )
            },
        ) { innerPadding ->
            // LOG AREA
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(
                        start = 16.dp,
                        top = if (showToolbar) 16.dp else 0.dp,
                        end = 16.dp,
                        bottom = 8.dp,
                    )
                    .background(SurfaceColor)
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
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(scrollState),
                )
            }
        }
    }
}

@Composable
private fun ConsoleInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleOrientation: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleOrientationButton(onClick = onToggleOrientation)

        Spacer(Modifier.width(8.dp))

        TextField(
            value = input,
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

@Composable
private fun ConsoleToolbar(
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        IconButton(
            onClick = onBack,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.White,
            )
        }

        Text(
            text = "Mavlink Console",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
