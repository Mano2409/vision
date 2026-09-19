package com.visionnav.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionnav.ui.state.DetectedObstacle
import com.visionnav.ui.state.ProximityLevel
import com.visionnav.ui.theme.VisionObstacleCritical
import com.visionnav.ui.theme.VisionObstacleFar
import com.visionnav.ui.theme.VisionObstacleMedium
import com.visionnav.ui.theme.VisionObstacleNear
import com.visionnav.ui.theme.VisionTextHighContrast
import com.visionnav.ui.theme.VisionTextOnAccent

/**
 * Developer & Researcher HUD overlay.
 * Renders CameraX spatial partition guidelines (Left 33% | Center 34% | Right 33%)
 * and maps normalized bounding boxes with proximity color-coding.
 */
@Composable
fun SpatialVisionOverlay(
    obstacles: List<DetectedObstacle>,
    isHudVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isHudVisible) return

    val criticalObstacle = obstacles.firstOrNull { it.isBlockingPath }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Spatial partition grid & bounding box canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Division lines at 33.3% and 66.6%
            val leftSplitX = width * 0.333f
            val rightSplitX = width * 0.666f

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)

            // Left guideline
            drawLine(
                color = Color(0x55FFFFFF),
                start = Offset(leftSplitX, 0f),
                end = Offset(leftSplitX, height),
                strokeWidth = 2f,
                pathEffect = dashEffect
            )

            // Right guideline
            drawLine(
                color = Color(0x55FFFFFF),
                start = Offset(rightSplitX, 0f),
                end = Offset(rightSplitX, height),
                strokeWidth = 2f,
                pathEffect = dashEffect
            )

            // Draw bounding boxes for detected obstacles
            for (obs in obstacles) {
                val box = obs.boundingBox
                val boxLeft = box.left * width
                val boxTop = box.top * height
                val boxWidth = (box.right - box.left) * width
                val boxHeight = (box.bottom - box.top) * height

                val boxColor = when (obs.proximity) {
                    ProximityLevel.CRITICAL -> VisionObstacleCritical
                    ProximityLevel.NEAR -> VisionObstacleNear
                    ProximityLevel.MEDIUM -> VisionObstacleMedium
                    ProximityLevel.FAR -> VisionObstacleFar
                }

                // Bounding rectangle
                drawRect(
                    color = boxColor,
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(width = 4f)
                )

                // Semi-transparent box fill
                drawRect(
                    color = boxColor.copy(alpha = 0.15f),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxWidth, boxHeight)
                )
            }
        }

        // 2. Zone Labels (Top Header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 52.dp)
        ) {
            Text(
                text = "LEFT",
                color = Color(0x88FFFFFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "CENTER (PATH)",
                color = Color(0xAA00E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "RIGHT",
                color = Color(0x88FFFFFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        // 3. Urgent Collision Warning Banner (when obstacle interferes with center path)
        AnimatedVisibility(
            visible = criticalObstacle != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
        ) {
            if (criticalObstacle != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VisionObstacleCritical)
                        .border(3.dp, VisionTextHighContrast, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = VisionTextHighContrast,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "OBSTACLE DIRECTLY AHEAD",
                                color = VisionTextHighContrast,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${criticalObstacle.label.uppercase()} in center path • Move slightly left",
                                color = VisionTextHighContrast,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
