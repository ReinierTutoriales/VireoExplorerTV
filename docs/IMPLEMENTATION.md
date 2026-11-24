# Implementation status
Initial slice: Compose TV launcher, persisted SAF folder grants, lazy directory listing, breadcrumbs/back, refresh, file properties and external viewer handoff. No integrated media, network, cleaner or transfer UI yet. Storage provider create/delete primitives require UI confirmation and integration before exposure. SAF availability varies across TV firmware; all-files fallback is pending. Build and focus validation pending CI. API 24 minimum, target/compile 37 provisional pending compatibility checks. Compose 1.13.0, TV Material 1.1.0 and AGP 9.5.2 checked against official Android release pages on 2026-11-24; other pins reused from VireoAirTV and need independent verification. CI uses explicit Gradle distribution, wrapper not yet included.

## AMOLED TV UI
Mandatory native Jetpack Compose for TV and AndroidX TV Material, latest compatible stable versions verified against official releases. Pure black #000000 window and content background; #121212 surfaces, #80DCC0 mint accent, #F2F4F3 primary text. Centralize tokens in VireoTheme; preserve native TV focus, typography and D-pad behavior. No mobile-only Material components or unnecessary UI libraries. Verify startup, focus contrast, back navigation and accessibility. Theme source introduced in 147e72c; build and visual acceptance remain unverified.

CI run 45415816950: sdkmanager runs but cannot find platforms;android-37. Use API 36 provisionally for compile/target until API 37 availability is verified. Build remains unverified.

SAF operations: create folder, rename and confirmed deletion of selected files wired to UI, provider flags checked for rename/delete. Native framework input/confirmation dialogs are interim until TV Material dialog and text input validation. Directory rename/delete, transfer, direct storage fallback and focus validation remain pending. No new dependencies.

Run 45417554338 resolved dependencies but failed checkDebugAarMetadata: Compose 1.13.0 requires compile API 37.1 or newer. Attempt compile SDK 37.2 using minor API DSL and matching SDK package; retain target 36 and min 24. SDK availability and DSL/build still require CI confirmation.

CI run 45419227954 succeeded before image viewer changes. Initial integrated static image viewer routes image MIME types internally, uses ImageDecoder API 28+ and sampled BitmapFactory fallback, viewport bounds, cancellable coroutine and TV Material zoom/rotation/reset controls. Tests added for sampling. Animated images, EXIF fallback, region zoom, pan, slideshow, focus and remote providers remain pending. No new dependencies. New CI not yet verified.

Image viewer CI 45419695720 succeeded. Initial Media3 player integrates local audio/video, PlayerView default SurfaceView and subtitle rendering, TV Material controls, embedded track selection, MediaSession, audio focus and lifecycle release. Media3 1.11.1 sourced from sibling project; latest stable status not independently verified. External subtitles, persisted resume, network, advanced controls and codec extensions pending. New CI pending.

Media3 initial CI 45420417016 succeeded. Added external subtitle selection via SAF for SRT/ASS/SSA/VTT/TTML and local playback position persistence on lifecycle stop/exit. Automatic sidecar matching, caption styling, bounded resume history and picker error handling refinement remain pending. New CI pending.

Subtitle/resume CI 45421647085 succeeded. Added initial single-file SAF copy/move clipboard, bounded 128 KiB streaming, SHA-256 destination verification, source recheck before move deletion, conflict rejection, cancellation and partial cleanup. Directory transfers, provider abstraction, race-free conflict guarantees, progress refinement and provider integration tests remain pending. New CI pending.

Transfer CI 45422292205 succeeded. Added read-only storage analysis, bounded traversal (depth 32, 2000 folders, 20000 files), largest-file report, SHA-256 duplicate candidates with 512 MiB hash budget, cancellation and partial-result disclosure, and current-folder search. No deletion from analysis until byte comparison/revalidation and explicit review are implemented. Limits provisional; provider integration tests and new CI pending.

Application and system storage management: follow docs/APP_MANAGEMENT.md. Initial visible-app inventory and official settings shortcuts implemented; actual temporary-file cleanup, usage statistics and own-cache cleanup pending. No broad package visibility permission added. New CI pending.

App manager CI 45423079038 succeeded. Added review-only classification of temporary extensions, incomplete downloads, APK installers and empty folders, individual confirmation and metadata/empty-folder revalidation before deletion. No automatic deletion or duplicate deletion. Metadata cannot prove file inactivity or prevent provider races; actual reclaimed space reporting and stronger identity checks pending. New CI pending.

Added optional application storage statistics via StorageStatsManager on API 26+, explicit usage-access settings and per-app query on IO. Cache is included in data bytes; no double counting. No automatic grant. Cleanup CI was still running before this change; new CI pending. Own-cache deletion, verified duplicate deletion and SMB remain pending.

Cleanup CI 45423637442 succeeded. Statistics CI 45423885708 failed on ProtectedPermissions for PACKAGE_USAGE_STATS. Narrow manifest suppression for documented user-grantable usage access; no global lint suppression. Added explicit cleanup of reserved inactive analysis cache only (currently no producer, so normally zero bytes). Duplicate deletion and SMB pending. New CI pending.

CI 45425614581 succeeded. Added individually confirmed duplicate deletion, distinct document identity checks, exact bounded streaming comparison, metadata revalidation and retained reference copy. Provider concurrency/aliasing cannot be made atomic by SAF; no bulk deletion. New CI pending. Network providers still pending.

Duplicate CI 45426006634 succeeded. Added read-only network document-provider browsing through Android SAF, requiring an installed provider. No direct network protocol, new dependency or INTERNET permission. Direct SMB/NFS/SFTP/WebDAV and shared provider integration remain pending. New CI pending.
