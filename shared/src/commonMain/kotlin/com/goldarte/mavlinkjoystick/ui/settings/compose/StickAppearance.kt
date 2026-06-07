package com.goldarte.mavlinkjoystick.ui.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.goldarte.mavlinkjoystick.ui.settings.SettingsScreenState

@Composable
fun StickAppearanceSettingsScreen(
    state: SettingsScreenState.StickAppearanceState,
    onShowCircularAreaChange: () -> Unit,
    onShowSquareAreaChange: () -> Unit,
    onShowCircleBoundariesChange: () -> Unit,
    onKnobColorChange: (Color) -> Unit,
) {
    val colors = listOf(
        Color(0xFFF44336), // Red
        Color(0xFFFF9800), // Orange
        Color(0xFFFFEB3B), // Yellow
        Color(0xFF4CAF50), // Green
        Color(0xFF2196F3), // Blue
        Color(0xFF9C27B0), // Purple
        Color(0xFFFF5C8D), // Pink
        Color(0xFFFFFFFF), // White
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SectionTitle("Boundaries Visibility")

            SettingsCheckbox(
                text = "Show Circular Area",
                checked = state.showCircularArea,
                onCheckedChange = { onShowCircularAreaChange() },
            )

            SettingsCheckbox(
                text = "Show Square Boundaries",
                checked = state.showSquareArea,
                onCheckedChange = { onShowSquareAreaChange() },
            )

            SettingsCheckbox(
                text = "Show Circle Boundaries",
                checked = state.showCircleBoundaries,
                onCheckedChange = { onShowCircleBoundariesChange() },
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        Column(modifier = Modifier.weight(1f)) {
            SectionTitle("Knobs Color")

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 4,
            ) {
                colors.forEach { color ->
                    val isSelected = color == state.knobColor
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .border(
                                width = 3.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape,
                            )
                            .padding(3.dp)
                            .border(
                                width = 1.dp,
                                color = Color.Black,
                                shape = CircleShape,
                            )
                            .clip(CircleShape)
                            .background(color)
                            .clickable {
                                onKnobColorChange(color)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
) {
    Text(
        text = text,
        color = Color(0xFFAAAAAA),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(bottom = 16.dp),
    )
}

@Composable
private fun SettingsCheckbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onCheckedChange(!checked)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFFFF5C8D),
            ),
        )

        Text(
            text = text,
            color = Color.White,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}