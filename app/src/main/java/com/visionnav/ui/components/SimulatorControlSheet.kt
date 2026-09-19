package com.visionnav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionnav.ui.state.ProximityLevel
import com.visionnav.ui.state.SpatialZone
import com.visionnav.ui.state.VisionNavViewModel
import com.visionnav.ui.theme.VisionAmberPrimary
import com.visionnav.ui.theme.VisionCyanVoice
import com.visionnav.ui.theme.VisionObstacleCritical
import com.visionnav.ui.theme.VisionObstacleNear
import com.visionnav.ui.theme.VisionSurfaceCard
import com.visionnav.ui.theme.VisionSurfaceDark
import com.visionnav.ui.theme.VisionTextHighContrast
import com.visionnav.ui.theme.VisionTextMediumContrast

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulatorControlSheet(
    viewModel: VisionNavViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(VisionSurfaceDark)
            .border(2.dp, Color(0xFF333333), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(20.dp)
    ) {
        // Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "OFFLINE STATE SIMULATOR",
                    color = VisionCyanVoice,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Test PRD scenarios without hardware sensors",
                    color = VisionTextMediumContrast,
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close simulator",
                    tint = VisionTextHighContrast
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider(color = Color(0xFF2A2A2A))
        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Lifecycle Scenarios
        Text(
            text = "NAVIGATION LIFECYCLE",
            color = VisionAmberPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SimButton(text = "1. Speak Destination") {
                viewModel.processSpokenDestination("Metropolitan Museum")
            }
            SimButton(text = "2. Start Walking") {
                viewModel.startNavigation()
            }
            SimButton(text = "3. Next Maneuver") {
                viewModel.stepToNextManeuver()
            }
            SimButton(text = "4. Arrived") {
                viewModel.simulateArrival()
            }
            SimButton(text = "Reset All", color = Color(0xFF444444)) {
                viewModel.resetToIdle()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🚨 P0 Safety Collision Test Section
        Text(
            text = "🚨 P0 SAFETY: VEHICLE COLLISION PREEMPTION",
            color = VisionObstacleCritical,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SimButton(text = "🚨 Lorry Approaching", color = VisionObstacleCritical) {
                viewModel.triggerCriticalVehicleThreat("Lorry")
            }
            SimButton(text = "🚨 Fast Car Approaching", color = VisionObstacleCritical) {
                viewModel.triggerCriticalVehicleThreat("Car")
            }
            SimButton(text = "Clear Threat (Verify 1.5s Resume)", color = Color(0xFF2A2A2A)) {
                viewModel.clearCriticalVehicleThreat()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Static Obstacle Detection Scenarios
        Text(
            text = "STATIC OBSTACLE DETECTION",
            color = VisionAmberPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SimButton(text = "Pole (Center • Critical)", color = VisionObstacleCritical) {
                viewModel.injectObstacle(SpatialZone.CENTER, ProximityLevel.CRITICAL, "Utility Pole")
            }
            SimButton(text = "Barrier (Left • Near)", color = VisionObstacleNear) {
                viewModel.injectObstacle(SpatialZone.LEFT, ProximityLevel.NEAR, "Construction Barrier")
            }
            SimButton(text = "Person (Right • Near)", color = VisionObstacleNear) {
                viewModel.injectObstacle(SpatialZone.RIGHT, ProximityLevel.NEAR, "Pedestrian")
            }
            SimButton(text = "Clear All Obstacles", color = Color(0xFF2A2A2A)) {
                viewModel.clearObstacles()
            }
        }
    }
}

@Composable
private fun SimButton(
    text: String,
    color: Color = VisionSurfaceCard,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .border(1.dp, Color(0xFF444444), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = VisionTextHighContrast,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

