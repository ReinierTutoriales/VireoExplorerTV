---
name: testing-discipline
description: Regression coverage and honest validation for Android TV.
---
# Testing discipline
- Establish expected behavior and reproduce defects before correcting them where feasible. Add a regression test that detects the old defect.
- Derive actual commands from project Gradle tasks and CI; do not invent existing tests.
- Verify focused tests, adjacent behavior, build, lint, and relevant emulator/API matrix. Do not weaken checks to obtain green CI.
- Cover cold start, recreation, process death, background/foreground, cancellation, resource cleanup, focus and remote navigation as applicable.
- Distinguish unit, emulator, benchmark and physical results. Report unexecuted checks and hardware-specific validation gaps without making physical testing a mandatory gate unless explicitly required.
- Classify failures as product, fixture, build or environment before fixing.

## Integrated project requirements
Test D-pad repeat/focus restoration, API boundaries, provider revocation/removal, transfer integrity/cancellation, duplicate preservation, malformed archives/media/images, subtitle tracks, network loss, memory pressure and lifecycle. Measure startup/frame timing/memory before optimization claims; separate CI and physical evidence.

## Application management and intelligent cleanup
Follow docs/APP_MANAGEMENT.md as a binding contract. Include scoped installed-app inventory, system/user distinction, optional authorized storage and usage statistics, official app settings for cache/data/uninstall/disable, consent-based APK installation, accessible temporary/incomplete files, residual APKs, empty folders, own inactive cache and duplicate review. Never equate candidate names or age with junk. Protect system/private data, revalidate before deletion, preserve a duplicate, report actual reclaimed bytes and partial failures. No root, hidden APIs, forced process killing or fake RAM optimization. Verify permission eligibility and device capability; test revoked access, active files and unavailable settings. Keep UI native TV and operations bounded, cancellable and off the main thread. Implementation status belongs in IMPLEMENTATION.md.

## Mandatory upstream-first verification (2026-11-24)
Before each implementation batch, verify current official Android platform behavior changes, API contract, permissions, target/min/compile SDK and actual SDK package availability. Check every direct dependency and tool against its official release notes and published artifact metadata; use newest compatible stable release, never assume a version copied from another repository is current. Record date, URL, installed and candidate versions, compatibility and exceptions. Check Kotlin/AGP/Gradle/JDK matrix, Compose alignment, Media3 and TV releases, transitive conflicts, license/security and native ABI/page size. No speculative upgrades, dynamic version selectors or unnecessary libraries. Run focused tests, build, lint and release checks before declaring completion; inspect actual CI logs and fix root causes. Suppress lint only locally with documented API justification and regression coverage; never globally silence errors. Optimize from measured evidence, remove replaced code and avoid unused scaffolding. If upstream or runtime verification is unavailable, disclose and leave status unverified. Official entry points: https://developer.android.com/latest-updates ; https://developer.android.com/jetpack/androidx/versions ; https://developer.android.com/jetpack/androidx/versions/stable-channel ; https://developer.android.com/build/releases/gradle-plugin ; https://developer.android.com/reference ; https://developer.android.com/training/tv ; https://developer.android.com/media/media3 ; https://kotlinlang.org/docs/releases.html ; https://docs.gradle.org/current/userguide/compatibility.html .

Mandatory permission contract: docs/PERMISSIONS.md. Verify current official Android API and target-SDK rules before each permission change; audit merged manifest and consumers; contextual consent, OS-derived state, denial/revocation tests, no unused or speculative permissions. Android 17 LAN migration must accompany target SDK 37.
