package com.visionnav.audio

enum class AudioPriority(val level: Int) {
    /**
     * P0: Highest priority in the entire application.
     * Approaching vehicle collision threats (TTC < 2.5s) or emergency hazards.
     * Immediately triggers hardware haptic shock, flushes routine audio, and speaks.
     * Cannot be interrupted by anything except another critical event.
     */
    CRITICAL_SAFETY(0),

    /**
     * P1: Static obstacles (poles, construction barriers, curbs) in the pedestrian path.
     * Preempts routine navigation if path is directly obstructed.
     */
    STATIC_OBSTACLE(1),

    /**
     * P2: Standard turn-by-turn navigation maneuvers and distance countdowns.
     */
    ROUTINE_NAVIGATION(2),

    /**
     * P3: UI confirmations and state feedback ("Listening...", "Searching route...").
     */
    SYSTEM_FEEDBACK(3)
}

data class AudioMessage(
    val id: String = "msg_${System.currentTimeMillis()}",
    val text: String,
    val priority: AudioPriority,
    val requiresHaptic: Boolean = (priority == AudioPriority.CRITICAL_SAFETY),
    val timestamp: Long = System.currentTimeMillis()
)
