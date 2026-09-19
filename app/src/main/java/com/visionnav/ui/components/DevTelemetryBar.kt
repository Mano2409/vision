package com.visionnav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.visionnav.location.LocationConfidence
import com.visionnav.ui.state.SystemTelemetry
import com.visionnav.ui.theme.VisionAmberPrimary
import com.visionnav.ui.theme.VisionCyanVoice
import com.visionnav.ui.theme.VisionObstacleCritical
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
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Telemetry Chips (GPS, Heading, Mode)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Mode Chip (LIVE vs SIMULATED)
            val modeColor = if (telemetry.isSimulatorMode) VisionCyanVoice else VisionAmberPrimary
            TelemetryChip(
                label = "MODE",
                value = if (telemetry.isSimulatorMode) "SIM" else "LIVE",
                valueColor = modeColor
            )

            // GPS Accuracy Chip
            val gpsValue = when {
                !telemetry.isGpsAvailable -> "NO FIX"
                telemetry.isOutlier -> "JUMP!"
                telemetry.gpsAccuracyMeters != null -> "±${telemetry.gpsAccuracyMeters.toInt()}m"
                else -> "--"
            }
            val gpsColor = when {
                telemetry.isOutlier -> VisionObstacleCritical
                telemetry.locationConfidence == LocationConfidence.HIGH -> Color(0xFF4CAF50)
                telemetry.locationConfidence == LocationConfidence.MODERATE -> VisionAmberPrimary
                else -> Color(0xFFE57373)
            }
            TelemetryChip(label = "GPS", value = gpsValue, valueColor = gpsColor)

            // Heading Chip (degrees + cardinal)
            val headingVal = if (telemetry.headingDegrees != null) {
                val deg = telemetry.headingDegrees.toInt()
                val cardinal = getCardinalDirection(deg)
                "$deg° $cardinal"
            } else {
                "--"
            }
            TelemetryChip(label = "DIR", value = headingVal)

            // Lat/Lng coordinate chip if available
            if (telemetry.latitude != null && telemetry.longitude != null) {
                TelemetryChip(
                    label = "POS",
                    value = "${String.format("%.2f", telemetry.latitude)},${String.format("%.2f", telemetry.longitude)}"
                )
            }
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

private fun getCardinalDirection(degrees: Int): String {
    val norm = (degrees % 360 + 360) % 360
    return when (norm) {
        in 338..360, in 0..22 -> "N"
        in 23..67 -> "NE"
        in 68..112 -> "E"
        in 113..157 -> "SE"
        in 158..202 -> "S"
        in 203..247 -> "SW"
        in 248..292 -> "W"
        in 293..337 -> "NW"
        else -> "N"
    }
}

@Composable
private fun TelemetryChip(
    label: String,
    value: String,
    valueColor: Color = VisionTextHighContrast
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp)
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
                color = valueColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
