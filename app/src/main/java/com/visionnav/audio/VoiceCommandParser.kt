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
    object RepeatHazard : VoiceCommand()
    data class SelectCandidate(val index: Int) : VoiceCommand()
    data class Unknown(val rawText: String) : VoiceCommand()
}

/**
 * Natural language intent parser for VisionNav voice commands (FR-10).
 */
object VoiceCommandParser {

    fun parse(rawText: String): VoiceCommand {
        val text = rawText.trim().lowercase().trimEnd('.', '?', '!', ',')

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
            text == "what was that" || text == "what did you say" ||
            text == "say again" || text == "say that again"
        ) {
            return VoiceCommand.RepeatInstruction
        }

        // 3. Repeat hazard / safety warning commands
        if (text == "repeat hazard" || text == "repeat warning" || text == "repeat danger" ||
            text.contains("repeat hazard") || text.contains("repeat warning")
        ) {
            return VoiceCommand.RepeatHazard
        }

        // 4. Location status queries
        if (text.contains("where am i") || text.contains("current location") || text == "where are we") {
            return VoiceCommand.WhereAmI
        }

        // 5. Path queries
        if (text.contains("what's ahead") || text.contains("whats ahead") || text.contains("what is ahead") ||
            text.contains("check ahead") || text == "check ahead" || text.contains("path status")
        ) {
            return VoiceCommand.WhatsAhead
        }

        // 6. Disambiguation candidate selection (e.g. "first one", "option 2")
        val candidate1Terms = listOf("first", "first one", "the first one", "option one", "option 1", "number one", "number 1", "candidate one")
        if (candidate1Terms.contains(text)) {
            return VoiceCommand.SelectCandidate(0)
        }

        val candidate2Terms = listOf("second", "second one", "the second one", "option two", "option 2", "number two", "number 2", "candidate two")
        if (candidate2Terms.contains(text)) {
            return VoiceCommand.SelectCandidate(1)
        }

        val candidate3Terms = listOf("third", "third one", "the third one", "option three", "option 3", "number three", "number 3", "candidate three")
        if (candidate3Terms.contains(text)) {
            return VoiceCommand.SelectCandidate(2)
        }

        // 7. Navigate to destination with expanded natural language phrases
        val navPrefixes = listOf(
            "can you please navigate to ",
            "can you please take me to ",
            "please navigate to ",
            "please take me to ",
            "please go to ",
            "can you navigate to ",
            "can you take me to ",
            "i want to go to ",
            "i would like to go to ",
            "i need to go to ",
            "navigate to ",
            "take me to ",
            "directions to ",
            "route to ",
            "head to ",
            "go to ",
            "find "
        )

        for (prefix in navPrefixes) {
            if (text.startsWith(prefix)) {
                val dest = text.removePrefix(prefix).trim().trimEnd('.', '?', '!', ',')
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

