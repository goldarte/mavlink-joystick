package com.goldarte.mavlinkjoystick.ui.menu.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val BackgroundColor = Color(0xFF0D0D0D)
private val SurfaceColor = Color(0xFF1E1E1E)
private val IconColor = Color.White
private val JoystickKnobColor = Color(0xFFFF5C8D)

@Composable
fun MenuScreen(
    onConsoleClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onJoystickClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MenuItem(
                    label = "MAVLINK JOYSTICK",
                    iconContent = { JoystickIcon() },
                    onClick = onJoystickClick
                )
                MenuItem(
                    label = "MAVLINK CONSOLE",
                    iconContent = { ConsoleIcon() },
                    onClick = onConsoleClick
                )
                MenuItem(
                    label = "SETTINGS\n",
                    iconContent = { SettingsIcon() },
                    onClick = onSettingsClick
                )
            }
        }
    }
}

@Composable
private fun JoystickIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width, size.height) / 2.5f

        // Outer ring - thinned
        drawCircle(
            color = IconColor.copy(alpha = 0.8f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Axis lines - thinned
        drawLine(
            color = IconColor.copy(alpha = 0.5f),
            start = Offset(center.x - radius * 0.8f, center.y),
            end = Offset(center.x + radius * 0.8f, center.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = IconColor.copy(alpha = 0.5f),
            start = Offset(center.x, center.y - radius * 0.8f),
            end = Offset(center.x, center.y + radius * 0.8f),
            strokeWidth = 1.dp.toPx()
        )

        // Inner circle/knob
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(JoystickKnobColor, JoystickKnobColor.copy(alpha = 0.8f)),
                center = center,
                radius = radius * 0.4f
            ),
            radius = radius * 0.4f,
            center = center
        )
    }
}

@Composable
private fun ConsoleIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = 2.dp.toPx()
        val color = IconColor.copy(alpha = 0.8f)
        
        // Draw ">" prompt
        val promptStart = size.width * 0.25f
        val promptWidth = size.width * 0.15f
        val promptHeight = size.height * 0.3f
        val centerY = size.height / 2
        
        drawLine(
            color = color,
            start = Offset(promptStart, centerY - promptHeight / 2),
            end = Offset(promptStart + promptWidth, centerY),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = color,
            start = Offset(promptStart + promptWidth, centerY),
            end = Offset(promptStart, centerY + promptHeight / 2),
            strokeWidth = strokeWidth
        )
        
        // Draw "_" cursor
        val cursorStart = promptStart + promptWidth + 6.dp.toPx()
        val cursorWidth = size.width * 0.2f
        val cursorY = centerY + promptHeight / 2
        
        drawLine(
            color = color,
            start = Offset(cursorStart, cursorY),
            end = Offset(cursorStart + cursorWidth, cursorY),
            strokeWidth = strokeWidth
        )
    }
}

@Composable
private fun SettingsIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = 2.dp.toPx()
        val color = IconColor.copy(alpha = 0.8f)
        val center = Offset(size.width / 2, size.height / 2)
        
        val innerRadius = size.width * 0.06f
        val outerRadius = size.width * 0.28f
        val toothLength = size.width * 0.07f
        
        // Inner circle (hub)
        drawCircle(
            color = color,
            radius = innerRadius,
            center = center,
            style = Stroke(width = strokeWidth/2)
        )
        
        // Outer ring (rim)
        drawCircle(
            color = color,
            radius = outerRadius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        
        // Gear teeth
        val teethCount = 8
        val toothWidth = 7.dp.toPx()
        
        for (i in 0 until teethCount) {
            val angle = (i * 360f / teethCount).toDouble()
            val rad = (angle * 3.141592653589793 / 180.0)
            
            val start = Offset(
                (center.x + cos(rad) * outerRadius).toFloat(),
                (center.y + sin(rad) * outerRadius).toFloat()
            )
            val end = Offset(
                (center.x + cos(rad) * (outerRadius + toothLength)).toFloat(),
                (center.y + sin(rad) * (outerRadius + toothLength)).toFloat()
            )
            
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = toothWidth,
                cap = StrokeCap.Butt
            )
        }
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
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(160.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceColor)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            if (iconContent != null) {
                iconContent()
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(64.dp),
                    tint = IconColor
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}
