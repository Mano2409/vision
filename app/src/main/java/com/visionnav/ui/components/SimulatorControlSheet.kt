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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(VisionSurfaceDark)
            .border(2.dp, Color(0xFF333333), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(20.dp)
            .verticalScroll(scrollState)
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
                    text = "Phase 3: GPS • Compass • Geocoding Simulation",
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

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = Color(0xFF2A2A2A))
        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 1: HARDWARE MODE VS SIMULATOR TOGGLE
        Text(
            text = "LOCATION & SENSOR SOURCE",
            color = VisionAmberPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SimButton(
                text = if (uiState.isSimulatorMode) "🔴 SWITCH TO LIVE GPS" else "🟢 SWITCH TO SIMULATOR",
                color = if (uiState.isSimulatorMode) VisionCyanVoice else Color(0xFF2A2A2A),
                textColor = if (uiState.isSimulatorMode) Color.Black else VisionTextHighContrast
            ) {
                viewModel.toggleSimulationMode()
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 2: SIMULATED MOVEMENT & SENSOR CONTROLS
        Text(
            text = "SPATIAL TRACKING CONTROLS",
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
            SimButton(text = "🚶 Walk Step (+12m)") {
                viewModel.simulatorStepForward()
            }
            SimButton(text = "🧭 Turn Heading +45°") {
                viewModel.simulatorRotateHeading(45f)
            }
            SimButton(text = "🧭 Face North (0°)") {
                viewModel.simulatorRotateHeading(-360f)
            }
            SimButton(text = "⚠️ Inject GPS Teleport Jump", color = Color(0xFF552222)) {
                viewModel.simulatorInjectGpsOutlier()
            }
            SimButton(text = "🌫️ Inject Low Accuracy (±38m)", color = Color(0xFF443311)) {
                viewModel.simulatorSetLowAccuracy()
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 3: GEOCODING TEST DESTINATIONS
        Text(
            text = "GEOCODING DESTINATIONS (VOICE / MOCK)",
            color = VisionCyanVoice,
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
            SimButton(text = "Central Railway Station") {
                viewModel.processSpokenDestination("Chennai Central")
            }
            SimButton(text = "City Hospital (Ambiguity Test)") {
                viewModel.processSpokenDestination("City Hospital")
            }
            SimButton(text = "Public Library") {
                viewModel.processSpokenDestination("Public Library")
            }
            SimButton(text = "Metro Station Gate 2") {
                viewModel.processSpokenDestination("Metro Station")
            }
        }

        // Ambiguous Candidates Disambiguation UI
        if (uiState.ambiguousCandidates.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "MULTIPLE DESTINATIONS FOUND — SELECT ONE:",
                color = VisionAmberPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                uiState.ambiguousCandidates.forEach { candidate ->
                    SimButton(
                        text = "📍 ${candidate.displayName}",
                        color = Color(0xFF263238)
                    ) {
                        viewModel.selectDestinationCandidate(candidate)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = Color(0xFF2A2A2A))
        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 4: 🚨 P0 Safety Collision Test Section
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

        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 5: STATIC OBSTACLES & NAVIGATION ACTIONS
        Text(
            text = "NAVIGATION & OBSTACLE ACTIONS",
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
            SimButton(text = "Start Walking") {
                viewModel.startNavigation()
            }
            SimButton(text = "Next Maneuver") {
                viewModel.stepToNextManeuver()
            }
            SimButton(text = "Arrived at Destination") {
                viewModel.simulateArrival()
            }
            SimButton(text = "Obstacle Center", color = VisionObstacleCritical) {
                viewModel.injectObstacle(SpatialZone.CENTER, ProximityLevel.CRITICAL, "Utility Pole")
            }
            SimButton(text = "Reset All", color = Color(0xFF444444)) {
                viewModel.resetToIdle()
            }
        }
    }
}

@Composable
private fun SimButton(
    text: String,
    color: Color = VisionSurfaceCard,
    textColor: Color = VisionTextHighContrast,
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
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
