---
name: native-tv-development
description: Implement complete efficient Android TV features using current official native APIs and verified dependencies.
---
Read docs/ARCHITECTURE.md and docs/EXECUTION.md. Select the smallest complete vertical slice, verify current stable versions and compatibility, implement UI through provider and tests together. Prefer Kotlin and AndroidX TV Material; no mobile-only focus assumptions. Verify D-pad repeats, long press, back, focus restoration, lifecycle and low-resource behavior. Use bounded IO, structured concurrency and capability checks. Avoid placeholder modules and unused dependencies. Run focused checks then integration checks, fix root causes, report exact evidence. Update internal docs only with verified findings; distinguish planned from implemented.
