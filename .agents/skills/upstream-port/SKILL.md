---
name: upstream-port
description: Research, audit and integrate documented upstream fixes and stable dependencies.
---
# Upstream integration
- Identify actual dependencies and pins from source and manifests. Consult primary release notes, source diffs, official registries and advisories.
- Verify latest compatible stable versions; inspect migration requirements, transitive changes and affected APIs.
- Prefer released versions; pin reproducible dependencies and verify hashes for build downloads when applicable.
- For individual ports, inspect prerequisites and follow-up fixes, keep provenance and license notices, review every diff, and test the resulting behavior.
- Search the local codebase for the same verified bug class rather than merely copying upstream.
- For parsers and native boundaries if present, use bounded input validation, fuzzing and sanitizers where supported.
- Record accepted, rejected and pending changes with reasons and actual validation.

## Integrated project requirements
Before adding codecs, protocol stacks or icons, verify upstream official stable release, Android compatibility, transitive dependencies, security, proprietary license and patent obligations, ABI/16 KiB requirements where relevant. Record source/version/date and use minimal necessary integration.

## Mandatory upstream-first verification (2026-11-24)
Before each implementation batch, verify current official Android platform behavior changes, API contract, permissions, target/min/compile SDK and actual SDK package availability. Check every direct dependency and tool against its official release notes and published artifact metadata; use newest compatible stable release, never assume a version copied from another repository is current. Record date, URL, installed and candidate versions, compatibility and exceptions. Check Kotlin/AGP/Gradle/JDK matrix, Compose alignment, Media3 and TV releases, transitive conflicts, license/security and native ABI/page size. No speculative upgrades, dynamic version selectors or unnecessary libraries. Run focused tests, build, lint and release checks before declaring completion; inspect actual CI logs and fix root causes. Suppress lint only locally with documented API justification and regression coverage; never globally silence errors. Optimize from measured evidence, remove replaced code and avoid unused scaffolding. If upstream or runtime verification is unavailable, disclose and leave status unverified. Official entry points: https://developer.android.com/latest-updates ; https://developer.android.com/jetpack/androidx/versions ; https://developer.android.com/jetpack/androidx/versions/stable-channel ; https://developer.android.com/build/releases/gradle-plugin ; https://developer.android.com/reference ; https://developer.android.com/training/tv ; https://developer.android.com/media/media3 ; https://kotlinlang.org/docs/releases.html ; https://docs.gradle.org/current/userguide/compatibility.html .
