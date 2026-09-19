package com.visionnav.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionnav.ui.state.VoiceState
import com.visionnav.ui.theme.VisionCyanPulse
import com.visionnav.ui.theme.VisionCyanVoice
import com.visionnav.ui.theme.VisionPureBlack
import com.visionnav.ui.theme.VisionSurfaceCard
import com.visionnav.ui.theme.VisionTextHighContrast
import com.visionnav.ui.theme.VisionTextMediumContrast

/**
 * Massive accessible touch surface designed for Blind & Visually Impaired (BVI) users.
 * Occupies the lower half of the viewport, satisfying Fitts' Law and eliminating precise target tapping.
 */
@Composable
fun VoiceTouchSurface(
    voiceState: VoiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when (voiceState) {
            VoiceState.IDLE -> VisionSurfaceCard
            VoiceState.LISTENING -> VisionCyanPulse
            VoiceState.PROCESSING -> VisionSurfaceCard
            VoiceState.SPEAKING -> VisionSurfaceCard
        },
        label = "surfaceBgColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when (voiceState) {
            VoiceState.IDLE -> Color(0xFF444444)
            VoiceState.LISTENING -> VisionCyanVoice
            VoiceState.PROCESSING -> VisionCyanVoice
            VoiceState.SPEAKING -> VisionCyanVoice
        },
        label = "surfaceBorderColor"
    )

    val scaleModifier = if (voiceState == VoiceState.LISTENING) {
        Modifier.scale(pulseScale)
    } else {
        Modifier
    }

    val stateDescription = when (voiceState) {
        VoiceState.IDLE -> "Ready to speak. Tap anywhere to begin."
        VoiceState.LISTENING -> "Listening to your destination. Tap again to cancel."
        VoiceState.PROCESSING -> "Finding route..."
        VoiceState.SPEAKING -> "VisionNav is speaking. Tap to interrupt."
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .border(
                width = if (voiceState == VoiceState.LISTENING) 4.dp else 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(24.dp)
            )
            .semantics {
                contentDescription = stateDescription
            }
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = scaleModifier
        ) {
            Icon(
                imageVector = when (voiceState) {
                    VoiceState.IDLE -> Icons.Filled.Mic
                    VoiceState.LISTENING -> Icons.Filled.Hearing
                    VoiceState.PROCESSING -> Icons.Filled.HourglassTop
                    VoiceState.SPEAKING -> Icons.Filled.VolumeUp
                },
                contentDescription = null,
                tint = if (voiceState == VoiceState.LISTENING) VisionCyanVoice else VisionTextHighContrast,
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when (voiceState) {
                    VoiceState.IDLE -> "TAP TO SPEAK"
                    VoiceState.LISTENING -> "LISTENING..."
                    VoiceState.PROCESSING -> "PROCESSING ROUTE..."
                    VoiceState.SPEAKING -> "SPEAKING..."
                },
                color = if (voiceState == VoiceState.LISTENING) VisionCyanVoice else VisionTextHighContrast,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when (voiceState) {
                    VoiceState.IDLE -> "Speak destination or command"
                    VoiceState.LISTENING -> "Tap when finished"
                    VoiceState.PROCESSING -> "Consulting Valhalla"
                    VoiceState.SPEAKING -> "Tap to pause or repeat"
                },
                color = VisionTextMediumContrast,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
