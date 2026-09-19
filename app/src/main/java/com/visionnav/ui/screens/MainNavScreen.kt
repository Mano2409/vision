package com.visionnav.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
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
import com.visionnav.ui.components.DevTelemetryBar
import com.visionnav.ui.components.NavigationManeuverCard
import com.visionnav.ui.components.SimulatorControlSheet
import com.visionnav.ui.components.SpatialVisionOverlay
import com.visionnav.ui.components.VoiceTouchSurface
import com.visionnav.ui.state.NavigationStatus
import com.visionnav.ui.state.VisionNavViewModel
import com.visionnav.ui.theme.VisionAmberPrimary
import com.visionnav.ui.theme.VisionCyanVoice
import com.visionnav.ui.theme.VisionPureBlack
import com.visionnav.ui.theme.VisionSurfaceCard
import com.visionnav.ui.theme.VisionTextHighContrast
import com.visionnav.ui.theme.VisionTextMediumContrast

@Composable
fun MainNavScreen(
    viewModel: VisionNavViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VisionPureBlack)
    ) {
        // 1. Vision Layer: Camera Viewfinder Placeholder & Spatial HUD Overlay
        SpatialVisionOverlay(
            obstacles = uiState.activeObstacles,
            isHudVisible = uiState.isDevHudVisible,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Main Accessible Touch & Control Flow
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Telemetry & Navigation Maneuver Guidance
            Column {
                DevTelemetryBar(
                    telemetry = uiState.telemetry,
                    isHudVisible = uiState.isDevHudVisible,
                    onToggleHud = { viewModel.toggleDevHud() },
                    onToggleSimulator = { viewModel.toggleSimulatorDrawer() }
                )

                // Turn Maneuver Card (active during routing)
                if (uiState.navigationStatus == NavigationStatus.NAVIGATING ||
                    uiState.navigationStatus == NavigationStatus.ROUTE_READY
                ) {
                    NavigationManeuverCard(
                        maneuver = uiState.currentManeuver,
                        onClick = { viewModel.onManeuverCardClicked() }
                    )
                }

                // Arrival Banner
                if (uiState.navigationStatus == NavigationStatus.ARRIVED) {
                    ArrivalBanner(destinationName = uiState.destinationName)
                }
            }

            // Center Section: Real-time Spoken Feedback Bubble (Audio Transcript Display)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                SpokenMessageBubble(message = uiState.latestSpokenMessage)
            }

            // Bottom Section: Massive Fitts' Law Touch Surface
            VoiceTouchSurface(
                voiceState = uiState.voiceState,
                onClick = { viewModel.onVoiceSurfaceClicked() }
            )
        }

        // 3. Slide-up Offline State Simulator Sheet
        AnimatedVisibility(
            visible = uiState.isSimulatorDrawerOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SimulatorControlSheet(
                viewModel = viewModel,
                onClose = { viewModel.toggleSimulatorDrawer() }
            )
        }
    }
}

@Composable
private fun SpokenMessageBubble(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE61A1A1A))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.VolumeUp,
                contentDescription = null,
                tint = VisionCyanVoice,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
            Column {
                Text(
                    text = "VOICE OUTPUT",
                    color = VisionCyanVoice,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"$message\"",
                    color = VisionTextHighContrast,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun ArrivalBanner(
    destinationName: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(VisionSurfaceCard)
            .border(2.dp, VisionAmberPrimary, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = VisionAmberPrimary,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Column {
                Text(
                    text = "ARRIVED AT DESTINATION",
                    color = VisionAmberPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = destinationName ?: "Navigation complete",
                    color = VisionTextHighContrast,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

