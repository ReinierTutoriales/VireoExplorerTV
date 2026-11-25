---
name: testing-discipline
description: Preventive compatibility validation, functional regression coverage and truthful acceptance for Android TV.
---
# Testing discipline

Apply AGENTS.md and docs/EXECUTION.md as canonical project contracts. Use docs/IMPLEMENTATION.md for verified evidence.

## 1. Prevent avoidable failures
- Before editing CI or dependencies, verify exact stable package IDs, API/ABI combinations, runner support, toolchain compatibility and official upstream metadata. For Android system images, inspect sdkmanager --list --channel=0; do not guess package names.
- Derive commands from actual repository tasks and workflows. Run syntax, configuration and targeted static checks before expensive compilation or emulator jobs. Reuse one build across compatible runtime jobs; avoid duplicated setup and unnecessary full reruns.
- Define acceptance checks for every confirmed defect and its adjacent failure paths before implementation. Test the old failure where feasible.

## 2. Test actual behavior
- Exercise user-visible operations and verify their observable effects, not merely button visibility, process startup or compilation. Check on-disk bytes, archive contents, source preservation, cancellation and destructive-action confirmation.
- Test read-only, denied and revoked permissions; legacy/scoped storage API boundaries; unavailable activities, provider/USB removal and network disconnect. Use capability-aware fixtures and official permission mechanisms.
- Exercise real D-pad navigation, long press, repeated keys, focus restoration and Back. Record touch fallback separately; it is not evidence of remote-only success.
- Cover media playback, subtitles, malformed media/archives, resource cleanup, lifecycle transitions and memory pressure as relevant to the requested slice.
- Distinguish unit, emulator, benchmark and physical results. Use representative supported API images; disclose unsupported image/API combinations rather than claiming universal coverage. Physical hardware is not a mandatory delivery gate unless requested.

## 3. Resolve and verify
- Inspect every failed job and its logs for the exact candidate SHA. For main/push runs, query the GitHub REST Actions runs collection with branch=main and match head_sha; the PR-only helper is insufficient. Retrieve runtime evidence ZIP (hierarchy, window state, logcat) before attributing an empty UI to a crash.
- In timed TV overlays, use idempotent media pause to establish visible controls before testing them; never assume an initial hierarchy snapshot precedes auto-hide. Verify actual focus, first-frame/cue events and auto-hide separately.
- Classify failures as product, test/fixture, build or infrastructure. Group related root causes, correct them together, and rerun affected checks plus adjacent regressions.
- Never skip tests, silently fall back, suppress lint globally, or weaken permissions/security to obtain green CI. Keep failure evidence and report unexecuted checks.
- Require focused regressions, relevant runtime/API checks, build and lint before declaring the slice validated. Inspect the actual current HEAD results before integration.
- Measure startup, frames, memory and throughput before asserting performance gains. Distinguish instrumented measurements from automation overhead.
- Follow docs/APP_MANAGEMENT.md and docs/PERMISSIONS.md for destructive operations and authorization; verify preservation, actual effects and error reporting. Use docs/PLAYER.md and docs/IMAGE_VIEWER.md for multimedia acceptance.
- Report exact SHA, run IDs, checks passed/failed, coverage limits and unresolved blockers. Do not promise future verification without an actual scheduled mechanism.

## Visual and dependency acceptance
Apply AGENTS.md modernization gate. For UI changes capture and inspect real screenshots, verify remote navigation and actions, and distinguish visual evidence from functional/CI results. For dependency changes inspect resolved debug/release graphs and consumers; check minified release behavior. Record unexecuted checks and investigate exact-SHA push runs via REST.

## Emulator readiness and install failures
ADB online does not prove Android is operational (https://developer.android.com/tools/adb). Before fixture installs require consecutive transport, boot and package manager probes. Retry only isolated fixture reinstall on transient transport failure or timeout, once; propagate deterministic install errors immediately. Capture setup diagnostics even before functional tests start. Do not weaken runtime assertions or classify setup failure as product failure. Run harness regression tests before emulator jobs.
