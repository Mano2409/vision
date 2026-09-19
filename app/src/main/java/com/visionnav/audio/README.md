# Audio Subsystem (Phase 2)

This package handles voice I/O and speech arbitration:
- `VoiceRecognizer.kt`: Android `SpeechRecognizer` wrapper with error recovery and silence detection.
- `VoiceSpeaker.kt`: Android `TextToSpeech` manager.
- `AudioQueueManager.kt`: Priority audio queue that prioritizes obstacle collision alerts over routine turn maneuvers and prevents overlapping chatter.
- `VoiceCommandParser.kt`: Matches utterances to commands ("Repeat", "Stop navigation", "Where am I?").

