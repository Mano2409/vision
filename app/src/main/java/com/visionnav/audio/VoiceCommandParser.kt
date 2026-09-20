package com.visionnav.audio

sealed class VoiceCommand {
    data class NavigateTo(val destination: String) : VoiceCommand()
    object StartNavigation : VoiceCommand()
    object PauseNavigation : VoiceCommand()
    object ResumeNavigation : VoiceCommand()
    object StopNavigation : VoiceCommand()
    object RepeatInstruction : VoiceCommand()
    object WhereAmI : VoiceCommand()
    object WhatsAhead : VoiceCommand()
    data class Unknown(val rawText: String) : VoiceCommand()
}

/**
 * Natural language intent parser for VisionNav voice commands (FR-10).
 */
object VoiceCommandParser {

    fun parse(rawText: String): VoiceCommand {
        val text = rawText.trim().lowercase()

        // 1. Navigation control commands
        if (text == "start navigation" || text == "start" || text == "begin" || text == "start walking") {
            return VoiceCommand.StartNavigation
        }

        if (text == "pause navigation" || text == "pause") {
            return VoiceCommand.PauseNavigation
        }

        if (text == "resume navigation" || text == "resume" || text == "continue navigation" || text == "unpause") {
            return VoiceCommand.ResumeNavigation
        }

        if (text == "stop" || text == "stop navigation" || text == "cancel" || text == "end route") {
            return VoiceCommand.StopNavigation
        }

        // 2. Repeat instruction commands
        if (text == "repeat" || text == "repeat that" || text == "repeat instruction" ||
            text == "what was that" || text == "what was that?" || text == "what did you say" ||
            text == "say again" || text == "say that again"
        ) {
            return VoiceCommand.RepeatInstruction
        }

        // 3. Location status queries
        if (text.contains("where am i") || text.contains("current location") || text == "where are we") {
            return VoiceCommand.WhereAmI
        }

        // 4. Path queries
        if (text.contains("what's ahead") || text.contains("whats ahead") || text.contains("what is ahead") || text.contains("path status")) {
            return VoiceCommand.WhatsAhead
        }

        // 5. Navigate to destination
        val navPrefixes = listOf(
            "navigate to ",
            "take me to ",
            "go to ",
            "route to ",
            "directions to ",
            "find "
        )

        for (prefix in navPrefixes) {
            if (text.startsWith(prefix)) {
                val dest = text.removePrefix(prefix).trim()
                if (dest.isNotBlank()) {
                    return VoiceCommand.NavigateTo(capitalizeWords(dest))
                }
            }
        }

        // If the user spoke a destination directly (e.g. "Central Park" or "Grand Central Terminal")
        if (text.length > 2 && !text.startsWith("how") && !text.startsWith("why")) {
            return VoiceCommand.NavigateTo(capitalizeWords(text))
        }

        return VoiceCommand.Unknown(rawText)
    }

    private fun capitalizeWords(input: String): String {
        return input.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}

