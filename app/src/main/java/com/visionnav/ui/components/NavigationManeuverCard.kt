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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionnav.ui.state.ManeuverInstruction
import com.visionnav.ui.state.TurnDirection
import com.visionnav.ui.theme.VisionAmberPrimary
import com.visionnav.ui.theme.VisionPureBlack
import com.visionnav.ui.theme.VisionSurfaceCard
import com.visionnav.ui.theme.VisionTextHighContrast
import com.visionnav.ui.theme.VisionTextMediumContrast

@Composable
fun NavigationManeuverCard(
    maneuver: ManeuverInstruction?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (maneuver == null) return

    val icon: ImageVector = when (maneuver.direction) {
        TurnDirection.STRAIGHT -> Icons.Filled.ArrowUpward
        TurnDirection.LEFT -> Icons.Filled.TurnLeft
        TurnDirection.SLIGHT_LEFT -> Icons.Filled.ArrowBack
        TurnDirection.RIGHT -> Icons.Filled.TurnRight
        TurnDirection.SLIGHT_RIGHT -> Icons.Filled.ArrowForward
        TurnDirection.U_TURN -> Icons.Filled.UTurnLeft
        TurnDirection.DESTINATION -> Icons.Filled.Flag
    }

    val accessibilityText = "Next turn: In ${maneuver.distanceMeters} meters, ${maneuver.primaryText}. ${maneuver.secondaryText}. Tap to repeat."

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(VisionSurfaceCard)
            .border(2.dp, VisionAmberPrimary, RoundedCornerShape(20.dp))
            .semantics { contentDescription = accessibilityText }
            .clickable(
                role = Role.Button,
                onClickLabel = "Repeat maneuver",
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon container with high-visibility Amber circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(VisionAmberPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = VisionPureBlack,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.Baseline) {
                    Text(
                        text = "${maneuver.distanceMeters}m",
                        color = VisionAmberPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AHEAD",
                        color = VisionTextMediumContrast,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = maneuver.primaryText,
                    color = VisionTextHighContrast,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                )

                if (maneuver.secondaryText.isNotBlank()) {
                    Text(
                        text = maneuver.secondaryText,
                        color = VisionTextMediumContrast,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}
