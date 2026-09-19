package com.visionnav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionnav.ui.state.SystemTelemetry
import com.visionnav.ui.theme.VisionAmberPrimary
import com.visionnav.ui.theme.VisionCyanVoice
import com.visionnav.ui.theme.VisionSurfaceCard
import com.visionnav.ui.theme.VisionTextHighContrast
import com.visionnav.ui.theme.VisionTextMediumContrast

@Composable
fun DevTelemetryBar(
    telemetry: SystemTelemetry,
    isHudVisible: Boolean,
    onToggleHud: () -> Unit,
    onToggleSimulator: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Telemetry Chips
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TelemetryChip(label = "FPS", value = "${telemetry.fps.toInt()}")
            TelemetryChip(label = "LATENCY", value = "${telemetry.inferenceLatencyMs}ms")
            TelemetryChip(label = "GPS", value = "±${telemetry.gpsAccuracyMeters}m")
        }

        // Action Buttons: HUD Toggle & Simulator Trigger
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Simulator sheet button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(VisionSurfaceCard)
                    .border(1.dp, VisionCyanVoice, CircleShape)
                    .clickable(onClick = onToggleSimulator),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Open Simulator Panel",
                    tint = VisionCyanVoice,
                    modifier = Modifier.size(20.dp)
                )
            }

            // HUD visibility toggle
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(VisionSurfaceCard)
                    .border(1.dp, if (isHudVisible) VisionAmberPrimary else Color(0xFF444444), CircleShape)
                    .clickable(onClick = onToggleHud),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isHudVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = "Toggle Developer HUD",
                    tint = if (isHudVisible) VisionAmberPrimary else VisionTextMediumContrast,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TelemetryChip(
    label: String,
    value: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$label ",
                color = Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = VisionTextHighContrast,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

