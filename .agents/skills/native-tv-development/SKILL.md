---
name: native-tv-development
description: Implement complete efficient Android TV features using current official native APIs and verified dependencies.
---
Read docs/ARCHITECTURE.md and docs/EXECUTION.md. Select the smallest complete vertical slice, verify current stable versions and compatibility, implement UI through provider and tests together. Prefer Kotlin and AndroidX TV Material; no mobile-only focus assumptions. Verify D-pad repeats, long press, back, focus restoration, lifecycle and low-resource behavior. Use bounded IO, structured concurrency and capability checks. Avoid placeholder modules and unused dependencies. Run focused checks then integration checks, fix root causes, report exact evidence. Update internal docs only with verified findings; distinguish planned from implemented.

## AMOLED TV UI
Mandatory native Jetpack Compose for TV and AndroidX TV Material, latest compatible stable versions verified against official releases. Pure black #000000 window and content background; #121212 surfaces, #80DCC0 mint accent, #F2F4F3 primary text. Centralize tokens in VireoTheme; preserve native TV focus, typography and D-pad behavior. No mobile-only Material components or unnecessary UI libraries. Verify startup, focus contrast, back navigation and accessibility. Theme source introduced in 147e72c; build and visual acceptance remain unverified.

## Native UI performance contract
Follow [UI specification](docs/UI.md) (repository-root path). Prefer official TV Material and Compose Foundation primitives, native focus and vector resources; no redundant UI stacks. Measure startup, frame timing, memory and focus regressions before claiming improvements.

## Integrated project requirements
Read docs/UI.md, PLAYER.md and IMAGE_VIEWER.md under docs for relevant work. Native TV Material, Compose Foundation, lifecycle-aware state, hardware-first Media3, native image decoding, bounded caches, official vector icons and no redundant frameworks.

## Application management and intelligent cleanup
Follow docs/APP_MANAGEMENT.md as a binding contract. Include scoped installed-app inventory, system/user distinction, optional authorized storage and usage statistics, official app settings for cache/data/uninstall/disable, consent-based APK installation, accessible temporary/incomplete files, residual APKs, empty folders, own inactive cache and duplicate review. Never equate candidate names or age with junk. Protect system/private data, revalidate before deletion, preserve a duplicate, report actual reclaimed bytes and partial failures. No root, hidden APIs, forced process killing or fake RAM optimization. Verify permission eligibility and device capability; test revoked access, active files and unavailable settings. Keep UI native TV and operations bounded, cancellable and off the main thread. Implementation status belongs in IMPLEMENTATION.md.
