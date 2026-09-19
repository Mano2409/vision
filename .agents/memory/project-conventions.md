---
type: project
created: 2026-05-25
updated: 2026-09-19
---

# Project Conventions

## Git Workflow
- Always create a new dedicated branch for major code changes.
- Branch name format should follow: `feature/[task-slug]` or `fix/[bug-slug]`.

## Supported AI platforms (AG Kit)
- AG Kit **only supports Gemini CLI and Google Antigravity**.
- Do not claim compatibility with Claude Code, Cursor, Copilot, Windsurf, or other assistants unless the user explicitly expands scope.
- Copy on the website, docs, FAQ, README, and marketing should describe AG Kit as a toolkit for Gemini CLI / Antigravity-style agent setups.

## 🚨 VisionNav Non-Negotiable Core Safety Protocol (P0 Mandatory)
- **Top Priority in the Project:** Safety and immediate physical collision avoidance supersede all UI, navigation maneuvers, turn prompts, battery tuning, and feature completeness.
- **Approaching Vehicle Escalation:** When an approaching vehicle (car, lorry/truck, bus, motorcycle) has an intersecting trajectory with the user's path (Time-to-Collision $\text{TTC} < 2.5\text{s}$):
  1. **Immediate Reaction (<50ms):** Fire hardware vibration shock (`HapticManager`) + audio alert earcon concurrently with `tts.stop()`.
  2. **Audio Preemption:** Routine turn instructions are strictly suppressed via `AudioQueueManager.enqueue(CRITICAL_SAFETY)`.
  3. **Authoritative Warning:**
     - Single vehicle: **"STOP. [VEHICLE] APPROACHING."** (e.g. *"STOP. LORRY APPROACHING."*)
     - Multiple vehicles: **"STOP. MULTIPLE VEHICLES APPROACHING."**
     - Path uncertain: **"STOP. PATH NOT CLEAR."**
  4. **No Evasive Guessing:** Never blindly tell the user to move left or right in vehicular traffic unless 100% verified clear and safe. Default to commanding **"STOP"**.
  5. **Hysteresis / Recovery:** Never resume navigation on single-frame dropouts. Must maintain **at least 1.5 seconds of sustained, verified clear frames** before speaking: *"Danger cleared. Resuming route."*
- **Architecture Contract:**
  `Camera → YOLO26n → Tracking → Depth/Distance → Motion Estimation (TTC) → Path Intersection → Risk Assessment → CRITICAL Safety Event → AudioQueueManager → VoiceSpeaker`
  YOLO must NEVER directly call TTS. The Decision Engine arbitrates risk; `AudioQueueManager` controls preemption; `VoiceSpeaker` speaks.
