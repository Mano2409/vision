# Core Subsystem & Decision Engine (Phase 8)

This package contains the fusion intelligence of VisionNav:
- `DecisionEngine.kt`: Fuses current route instruction with active camera obstacles and heading.
- `ConflictResolver.kt`: Generates concise actionable warnings when center path is obstructed (e.g. "Obstacle ahead. Move slightly left.").
- `AlertDeduplicator.kt`: Suppresses repeated warnings for the same tracked obstacle.
