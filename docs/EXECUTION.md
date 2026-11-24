# Execution and acceptance

One coherent implementation pass plus focused validation and correction whenever feasible; never impose an arbitrary pass cap at the expense of correctness.

1. Read AGENTS and relevant skills, inspect HEAD, existing consumers, tests and repository rules. Establish actual build environment.
2. Verify official current stable releases, compatibility matrices, Android TV guidance, storage/permission policy, codec and protocol upstream docs. Record source URL, version and verification date in the change when relevant. Distinguish stable from preview; no unsupported version claims.
3. Implement complete vertical slices: UI, state, domain, provider, permissions, lifecycle, errors, tests and documentation together. Reuse native APIs and proven upstream implementations; audit licenses. Remove superseded code and dependencies in the same change.
4. Run build, lint, unit and integration checks; release build, startup and TV focus tests; security and dependency checks; benchmarks for performance claims. Test API boundaries, low resources, denied/revoked grants, USB removal, network disconnect, malformed media/archive, destructive actions and cancellation. Fix root causes and rerun affected checks. Do not poll CI indefinitely.
5. Review diff, dependency graph, unused code, resources, permission scope, error handling and release readiness. Report exact commit, actual checks and blockers. Respect branch/signing/release policy; never claim unexecuted checks passed.

## Delivery order
A: runnable TV foundation, build/CI, storage provider, permissions and core explorer.
B: robust transfers, archive operations, index and safe cleaner.
C: integrated media and capability-based codec extensions.
D: network providers, discovery, credentials and remote streaming.
E: cross-feature regression, release optimization, benchmarks and documentation.
Independent work may be combined where safe; every slice must be functional. No placeholder feature claims.

## Primary references
- https://developer.android.com/training/tv
- https://developer.android.com/training/tv/playback/compose
- https://developer.android.com/training/data-storage
- https://developer.android.com/training/data-storage/manage-all-files
- https://developer.android.com/media/media3
- https://developer.android.com/topic/performance
- https://developer.android.com/guide/practices/page-sizes
- https://developer.android.com/jetpack/androidx/releases
- https://developer.android.com/build/releases/gradle-plugin
- https://kotlinlang.org/docs/releases.html
- https://docs.gradle.org/current/userguide/compatibility.html

Reference entry points are not evidence that a specific version or feature was verified. Consult live upstream documentation before implementation.
