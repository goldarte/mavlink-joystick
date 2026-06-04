package com.eugenehammer.mavlinkjoystikkmp.ui.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eugenehammer.mavlinkjoystikkmp.ui.settings.SettingsScreenState
import com.eugenehammer.mavlinkjoystikkmp.ui.settings.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

private val BackgroundColor = Color(0xFF0D0D0D)
private val SurfaceColor = Color(0xFF1E1E1E)
private val DividerColor = Color(0x33FFFFFF)
private val ActiveColor = Color(0xFFFF5C8D)
private val InactiveColor = Color(0xFFAAAAAA)
private val FooterColor = Color(0xFF666666)

@Composable
fun SettingsScreen(
    goBack: () -> Unit,
    vm: SettingsViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            },
        containerColor = BackgroundColor,
        topBar = { SettingsToolbar(onBack = goBack) },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {

            SettingsSidebar(
                selectedTab = state.selectedTab,
                onTabSelected = vm::onTabSelected,
                onGithubClick = { uriHandler.openUri("https://github.com/goldarte/mavlink-joystick") }
            )

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp),
                color = DividerColor,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                when (state.selectedTab) {
                    SettingsScreenState.SettingsTab.Connection -> {
                        ConnectionSettingsScreen(
                            state = state.connectionSettingsState,
                            onAutodetectCheckboxClicked = vm::onAutodetectCheckboxClicked,
                            onListenPortChanged = vm::onListenPortChanged,
                            onHostChanged = vm::onHostChanged,
                            onTargetPortChanged = vm::onTargetPortChanged,
                            onDroneSystemIdChanged = vm::onDroneSystemIdChanged,
                            onDroneComponentIdChanged = vm::onDroneComponentIdChanged,
                            onSaveClick = vm::onSaveConnectionSettingsClicked,
                        )
                    }

                    SettingsScreenState.SettingsTab.SticksSize -> {
                        StickSizeSettingsScreen(
                            state = state.stickSizeState,
                            onLeftStickFactorChanged = vm::onLeftStickFactorChanged,
                            onLeftStickFactorDragEnded = vm::onLeftStickFactorDragEnded,
                            onRightStickFactorChanged = vm::onRightStickFactorChanged,
                            onRightStickFactorDragEnded = vm::onRightStickFactorDragEnded
                        )
                    }

                    SettingsScreenState.SettingsTab.SticksAppearance -> {
                        StickAppearanceSettingsScreen(
                            state = state.stickAppearanceState,
                            onShowCircularAreaChange = vm::onShowCircularAreaChange,
                            onShowSquareAreaChange = vm::onShowSquareAreaChange,
                            onShowCircleBoundariesChange = vm::onShowCircleBoundariesChange,
                            onKnobColorChange = vm::onKnobColorChange
                        )
                    }

                    SettingsScreenState.SettingsTab.SticksCurve -> {
                        StickCurveSettingsScreen(
                            state = state.curveSettingsState,
                            onAxisSelected = vm::onCurveParamsAxisSelected,
                            onWeightChange = vm::onWeightChange,
                            onWeightChangeFinished = vm::onWeightChangeFinished,
                            onOffsetChange = vm::onOffsetChange,
                            onOffsetChangeFinished = vm::onOffsetChangeFinished,
                            onExpoChange = vm::onExpoChange,
                            onExpoChangeFinished = vm::onExpoChangeFinished
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToolbar(
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
            text = "Settings",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun SettingsSidebar(
    selectedTab: SettingsScreenState.SettingsTab,
    onTabSelected: (SettingsScreenState.SettingsTab) -> Unit,
    onGithubClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .fillMaxHeight()
            .background(SurfaceColor)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 12.dp),
    ) {

        SidebarItem(
            title = "CONNECTION",
            selected = selectedTab == SettingsScreenState.SettingsTab.Connection,
            onClick = {
                onTabSelected(SettingsScreenState.SettingsTab.Connection)
            },
        )

        Text(
            text = "STICKS",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            color = InactiveColor,
            fontFamily = FontFamily.Monospace,
        )

        SidebarItem(
            title = "↳ SIZE",
            selected = selectedTab == SettingsScreenState.SettingsTab.SticksSize,
            paddingStart = 32.dp,
            onClick = {
                onTabSelected(SettingsScreenState.SettingsTab.SticksSize)
            },
        )

        SidebarItem(
            title = "↳ APPEARANCE",
            selected = selectedTab == SettingsScreenState.SettingsTab.SticksAppearance,
            paddingStart = 32.dp,
            onClick = {
                onTabSelected(SettingsScreenState.SettingsTab.SticksAppearance)
            },
        )

        SidebarItem(
            title = "↳ CURVE",
            selected = selectedTab == SettingsScreenState.SettingsTab.SticksCurve,
            paddingStart = 32.dp,
            onClick = {
                onTabSelected(SettingsScreenState.SettingsTab.SticksCurve)
            },
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "© 2026 Arthur Golubtsov",
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp),
            color = FooterColor,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onGithubClick)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 16.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = null,
                tint = FooterColor,
            )

            Text(
                text = "Source code",
                color = FooterColor,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SidebarItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    paddingStart: Dp = 6.dp,
) {
    Text(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = paddingStart,
                end = 16.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
        color = if (selected) ActiveColor else InactiveColor,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )
}