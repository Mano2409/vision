package com.visionnav.safety

/**
 * Safety risk classification levels for detected physical obstacles.
 */
enum class SafetyLevel(val priority: Int) {
    NONE(0),
    INFO(1),
    CAUTION(2),
    WARNING(3),
    CRITICAL(4);

    val isUrgent: Boolean
        get() = this == CRITICAL || this == WARNING

    val requiresAudio: Boolean
        get() = this != NONE
}

