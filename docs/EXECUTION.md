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

## Specification routing
Architecture and roadmap define scope; ENGINES.md defines provider, network, cleaner and codec contracts; UI.md defines native TV design; PLAYER.md and IMAGE_VIEWER.md define viewers. IMPLEMENTATION.md is the evidence ledger. Apply relevant skills, verify dependency versions and licenses, resolve CI blockers, then deliver complete functional slices. Keep third-party no-ai-slop skill unchanged.

## Application management and intelligent cleanup
Follow docs/APP_MANAGEMENT.md as a binding contract. Include scoped installed-app inventory, system/user distinction, optional authorized storage and usage statistics, official app settings for cache/data/uninstall/disable, consent-based APK installation, accessible temporary/incomplete files, residual APKs, empty folders, own inactive cache and duplicate review. Never equate candidate names or age with junk. Protect system/private data, revalidate before deletion, preserve a duplicate, report actual reclaimed bytes and partial failures. No root, hidden APIs, forced process killing or fake RAM optimization. Verify permission eligibility and device capability; test revoked access, active files and unavailable settings. Keep UI native TV and operations bounded, cancellable and off the main thread. Implementation status belongs in IMPLEMENTATION.md.

## Mandatory upstream-first verification (2026-11-24)
Before each implementation batch, verify current official Android platform behavior changes, API contract, permissions, target/min/compile SDK and actual SDK package availability. Check every direct dependency and tool against its official release notes and published artifact metadata; use newest compatible stable release, never assume a version copied from another repository is current. Record date, URL, installed and candidate versions, compatibility and exceptions. Check Kotlin/AGP/Gradle/JDK matrix, Compose alignment, Media3 and TV releases, transitive conflicts, license/security and native ABI/page size. No speculative upgrades, dynamic version selectors or unnecessary libraries. Run focused tests, build, lint and release checks before declaring completion; inspect actual CI logs and fix root causes. Suppress lint only locally with documented API justification and regression coverage; never globally silence errors. Optimize from measured evidence, remove replaced code and avoid unused scaffolding. If upstream or runtime verification is unavailable, disclose and leave status unverified. Official entry points: https://developer.android.com/latest-updates ; https://developer.android.com/jetpack/androidx/versions ; https://developer.android.com/jetpack/androidx/versions/stable-channel ; https://developer.android.com/build/releases/gradle-plugin ; https://developer.android.com/reference ; https://developer.android.com/training/tv ; https://developer.android.com/media/media3 ; https://kotlinlang.org/docs/releases.html ; https://docs.gradle.org/current/userguide/compatibility.html .

Mandatory permission contract: docs/PERMISSIONS.md. Verify current official Android API and target-SDK rules before each permission change; audit merged manifest and consumers; contextual consent, OS-derived state, denial/revocation tests, no unused or speculative permissions. Android 17 LAN migration must accompany target SDK 37.

## Native-first implementation gate
Before adding or retaining any dependency, custom subsystem or compatibility workaround, inspect its actual consumers and the current official Android/AndroidX API and sample code. Prefer framework APIs, then stable AndroidX/Jetpack, then narrowly scoped maintained external libraries only for demonstrated gaps. Record official source URL, verification date, API availability, target SDK behavior, lifecycle, permissions, fallback and tests. Do not copy obsolete samples or assume newer APIs work on all supported devices. Use capability checks and documented API guards; remove redundant paths after migration. No custom crypto, decoder, UI toolkit, background scheduler or permission framework when official equivalents meet requirements. Network protocols without a public native client (SMB, NFS, SFTP, WebDAV) may use audited maintained libraries; do not invent protocol implementations. Verify latest compatible stable releases against official release notes and artifact metadata. Prefer official samples as reference, not blind copying. Validate build, lint, unit tests and runtime integration separately; never claim measured gains without evidence.

## Official implementation first — mandatory execution gate
Before writing or refactoring any feature, inspect the existing consumers and locate the current official Android/AndroidX API, maintained sample and release metadata. Prefer adopting the complete supported component or documented pattern over hand-written equivalents. Verify actual stable artifact availability, API/target requirements, TV suitability and licensing; record URLs, date and compatibility evidence. Do not assume a newer library is appropriate merely because it exists.
- Architecture: use ViewModel, lifecycle-aware Flow collection, SavedStateHandle and official navigation patterns where justified; avoid custom lifecycle, state serialization and navigation frameworks. Evaluate Navigation 3 against TV focus and supported versions before adoption.
- TV: Compose for TV, TV Material, official focus and lazy layout APIs; avoid custom widgets or key interception unless a demonstrated gap requires them.
- Storage/media/security: use SAF, DocumentsContract, StorageManager, ContentResolver, Media3, ImageDecoder, Keystore and official permission contracts. Use maintained protocol libraries only for missing platform capabilities.
- Deliver coherent vertical slices in the fewest safe passes: inspect, select official implementation, integrate, remove superseded code, test and verify actual CI. No repetitive micro-commits or documentation-only substitutes for requested implementation. Check every edited call site and API boundary before committing. Do not use blind text substitutions.
- Custom code requires a concrete unsupported requirement and focused regression tests. Prefer official samples as references, not wholesale imports; preserve licenses. No invented APIs, unverified version claims, unnecessary dependencies or performance claims without measurements.
- Report actual evidence and blockers, not planned work as completed. Never bypass lint or weaken permissions to make CI green.
