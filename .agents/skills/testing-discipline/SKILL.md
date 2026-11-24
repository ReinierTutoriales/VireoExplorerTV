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
