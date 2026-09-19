package com.visionnav.ui.theme

import androidx.compose.ui.graphics.Color

// Pure OLED Black base for battery conservation and maximum contrast
val VisionPureBlack = Color(0xFF000000)
val VisionSurfaceDark = Color(0xFF141414)
val VisionSurfaceCard = Color(0xFF1E1E1E)
val VisionSurfaceBorder = Color(0xFF333333)

// High-Luminance Accessible Primary Accents
val VisionAmberPrimary = Color(0xFFFFD600) // High contrast for navigation cues
val VisionAmberDark = Color(0xFFC7A500)
val VisionCyanVoice = Color(0xFF00E5FF)    // Distinct electric cyan for voice/STT
val VisionCyanPulse = Color(0x3300E5FF)

// Obstacle Severity Accents (Spatial HUD)
val VisionObstacleCritical = Color(0xFFFF3B30) // Red - Immediate collision hazard (<1.5m)
val VisionObstacleNear = Color(0xFFFF9500)     // Orange - Near proximity (1.5m - 3m)
val VisionObstacleMedium = Color(0xFFFFCC00)   // Yellow - Medium proximity (3m - 5m)
val VisionObstacleFar = Color(0xFF4CD964)      // Green/Cyan - Far proximity (>5m)

// Neutral Text & Icons
val VisionTextHighContrast = Color(0xFFFFFFFF)
val VisionTextMediumContrast = Color(0xFFCCCCCC)
val VisionTextLowContrast = Color(0xFF888888)
val VisionTextOnAccent = Color(0xFF000000)

