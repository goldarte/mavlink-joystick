package com.eugenehammer.mavlinkjoystikkmp.ui.menu.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private val BackgroundColor = Color(0xFF0D0D0D)
private val SurfaceColor = Color(0xFF1E1E1E)
private val IconColor = Color.White
private val JoystickKnobColor = Color(0xFFFF5C8D)

@Composable
fun MenuScreen(
    onJoystickClick: () -> Unit,
    onConsoleClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MenuItem(
                iconContent = { JoystickIcon(modifier = Modifier.size(48.dp)) },
                label = "Joystick",
                onClick = onJoystickClick
            )
            MenuItem(
                icon = Icons.Default.Terminal,
                label = "Mavlink\nConsole",
                onClick = onConsoleClick
            )
            MenuItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun JoystickIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(size.width, size.height) / 2f
        val outerRingRadius = radius * 0.9f
        val knobRadius = radius * 0.35f
        
        // Draw outer ring
        drawCircle(
            color = Color.White.copy(alpha = 0.2f),
            radius = outerRingRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx())
        )
        
        // Draw inner circle fill
        drawCircle(
            color = Color.White.copy(alpha = 0.05f),
            radius = outerRingRadius,
            center = Offset(cx, cy)
        )

        // Draw crosshairs
        val crossColor = Color.White.copy(alpha = 0.2f)
        val crossStrokeWidth = 1.dp.toPx()
        
        drawLine(
            color = crossColor,
            start = Offset(cx - outerRingRadius, cy),
            end = Offset(cx + outerRingRadius, cy),
            strokeWidth = crossStrokeWidth
        )
        
        drawLine(
            color = crossColor,
            start = Offset(cx, cy - outerRingRadius),
            end = Offset(cx, cy + outerRingRadius),
            strokeWidth = crossStrokeWidth
        )

        val knobOffset = Offset(cx, cy)

        // Draw knob glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    JoystickKnobColor.copy(alpha = 0.33f),
                    Color.Transparent,
                ),
                center = knobOffset,
                radius = knobRadius * 2.2f,
            ),
            radius = knobRadius * 2.2f,
            center = knobOffset,
        )

        // Draw knob
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    JoystickKnobColor,
                    JoystickKnobColor.copy(alpha = 0.8f),
                ),
                center = Offset(
                    knobOffset.x,
                    knobOffset.y - knobRadius * 0.3f,
                ),
                radius = knobRadius,
            ),
            radius = knobRadius,
            center = knobOffset,
        )

        // Knob border
        drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = knobRadius,
            center = knobOffset,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

@Composable
private fun MenuItem(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceColor)
            .clickable(onClick = onClick)
            .padding(24.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (iconContent != null) {
                iconContent()
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(48.dp),
                    tint = IconColor
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
