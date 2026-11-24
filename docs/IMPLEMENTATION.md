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

SMBJ 0.15.0 verified against Maven Central and upstream examples on 2026-11-24. Direct authenticated SMB directory browsing, signing required, timeouts, transient credentials and INTERNET permission. No file streaming or write operations yet; Android runtime crypto compatibility and dependency notices pending validation. https://central.sonatype.com/artifact/com.hierynomus/smbj

SMB compilation CI 45428142635 passed. Added read-only SMB download to user-selected SAF document with bounded buffer, size verification, cancellation and explicit partial-file warning. No automatic deletion of destination because provider ownership/overwrite cannot be assumed. New CI and real server validation pending.

CI 45429056502 passed. Consolidated SMB connection lifecycle and authentication; download now verifies local destination size and SHA-256 by rereading it. No new dependencies or permissions. A destination without read access cannot be reported verified. New CI pending.

CI 45430026684 passed. Added SMB upload from SAF OpenDocument using FILE_CREATE (no overwrite), bounded streaming, cancellation and remote SHA-256 verification. Partial remote files may remain on failure; no automatic destructive cleanup. New CI and server integration pending.

CI 45430913449 passed. Added seekable SMB Media3 DataSource and reused MediaViewer for remote audio/video. No full-file download or new dependency. Runtime server, seek and codec tests pending; new CI pending.

CI 45431467789 passed. Added SMB folder creation and non-replacing rename for files and directories, shared name validation and unit tests. Case-only rename deliberately rejected; remote deletion not yet enabled. New CI and server validation pending.

CI 45432402957 passed. Added explicitly confirmed permanent SMB deletion, metadata revalidation and nonrecursive empty-directory deletion. Path-based revalidation is not atomic against concurrent remote modifications. New CI and real server validation pending.

CI 45433047121 passed. Added explicit opt-in single saved SMB connection encrypted with AndroidKeyStore AES-256/GCM, fresh IV per save, encrypted preferences, manual restore and forget. No automatic connection or new permissions. Hardware-backed key availability varies; transient JVM strings cannot be guaranteed erased. New CI and runtime Keystore validation pending.

CI 45433634053 passed. Added user-triggered 15-second DNS-SD SMB discovery via NsdManager, bounded service list, manual resolution and connection form prefill. Legacy resolveService compatibility path retained for API 24; modern callback migration pending. Only advertised _smb._tcp services on port 445 supported; manual host remains available. No new permissions or dependencies. New CI and runtime validation pending.

CI 45434197304 passed. DNS-SD now retains original service information and uses ServiceInfoCallback on API 34+, with cancellation unregister and single completion; legacy resolution retained below API 34. New CI and network integration pending.

CI 45434880043 passed. Extracted reusable streaming SHA-256 transfer primitive and migrated SMB upload/download to it. Added unit tests for multi-buffer copy, hash mismatch and size mismatch. Local SAF migration remains pending. New CI pending.

Migrated local SAF copy/move to the same bounded JCA SHA-256 streaming primitive as SMB. Preserved progress, destination verification and source recheck before deletion. No new dependencies. CI pending; provider operations are not atomic.

CI 45438969655 passed. Consolidated SAF provider navigation into the main explorer; removed redundant NetworkBrowser. SAF picker now persists only returned read/write grants; refreshes grants on resume and gates write UI. Direct SMB remains separate. New CI pending; runtime grant revocation and picker compatibility require validation.

CI 45440710021 failed WrongConstant on persistable permission flags. Fixed explicit read or read/write constants, no lint suppression. Added StorageManager volume inventory and media mount/unmount observation, grant revalidation in SAF backend, metadata revalidation for rename/delete and cancellable directory iteration. Volume inventory is informational, access still requires SAF grant. New CI pending.

CI 45441684731 passed. Added volume-specific official SAF picker on API 29+ with generic fallback below 29, directory selection/open/rename and empty-only deletion. Directory copy/move intentionally disabled until safe recursive transfer implementation. New CI pending.

CI 45443447249 passed. Added bounded recursive SAF directory copy with preflight tree traversal, same-provider document ID descendant rejection, cycle/depth/count checks, no overwrite and per-file verified transfer. Partial completed copies preserved on failure/cancellation. Directory move remains disabled; provider aliases and concurrent changes cannot be fully excluded. New CI pending.

CI 45444038075 passed. Added multi-selection, select visible, batch copy of files/directories and batch move of files only. Sequential verified transfers retain unfinished clipboard entries after partial failure; no bulk deletion. New CI pending.

CI 45444597042 passed. Added provider-native directory move via DocumentsContract.moveDocument when FLAG_SUPPORTS_MOVE is present, restricted to same persisted tree, with source parent, descendant and conflict checks. No recursive deletion fallback. New CI pending.

CI 45445805113 failed NewApi for isChildDocument (API29). Added API guard and bounded cancellable directory traversal on API24–28, fail closed on traversal limits. Added optional provider-reported available bytes using DocumentsContract root metadata, only exact root document ID matches; unknown space remains hidden. New CI pending.

CI 45446808435 passed. Added stop/skip/keep-both conflict policies, case-insensitive alternate naming, per-item destination checks and three naming tests. Native folder move cannot rename on conflict; no overwrite policy. New CI pending.

CI 45448074062 passed. Explorer state and storage instance moved to AndroidViewModel; mutation and transfer jobs use viewModelScope, retaining state across Activity recreation. UI state still uses Compose snapshots; StateFlow and process-death restoration remain pending. New CI pending.

Moved directory loading, mutation and paste execution into ExplorerModel; retained load identity across Activity recreation, StateFlow progress from IO, corrected refactor UI labels. SavedStateHandle and full immutable UI state remain pending. CI pending.

CI 45449046306 passed. SavedStateHandle persists navigation URI/label pairs and restores only matching persisted read grants, bounded to 128 entries. Load generation prevents obsolete requests from publishing state. Transfer progress exposed read-only StateFlow. Horizontal scrolling prevents toolbar clipping; TV focus behavior still requires validation. No automatic destructive operation recovery. New CI pending.

CI 45451018307 passed. MediaViewer replaces manual observer with AndroidX LifecycleStartEffect; player/session released on stop or disposal, playback state reset. Subtitle metadata query moved to Dispatchers.IO with cancellation and errors. Existing PlayerView retained for subtitle rendering. New CI pending.

CI 45451713372 passed. AppManager removes unused analysis-cache action and helper (no active producer), refreshes package inventory and invalidates stale storage statistics on resume via LifecycleEventEffect, preserves system-app filter via rememberSaveable, uses framework Formatter for sizes and scrollable toolbar. System settings remain the supported route for other apps' cache/data. New CI pending.

CI 45452297983 passed. Explorer rows use ContentResolver.loadThumbnail on API29+ for providers advertising FLAG_SUPPORTS_THUMBNAIL, small requested size, IO dispatcher, CancellationSignal on disposal; unsupported providers retain text rows. Native formatted sizes and bounded filename lines. No new dependencies or permissions. API reference: https://developer.android.com/reference/android/content/ContentResolver#loadThumbnail(android.net.Uri,android.util.Size,android.os.CancellationSignal). New CI pending; no measured performance claim.

CI 45452958837 passed. Native LazyVerticalGrid adaptive 180dp and LazyColumn share filtered/sorted entries, stable URI keys and selection action. Four sort modes, folders first, unknown sizes last for size sorting, rememberSaveable view/sort selection. No new dependencies. New CI and D-pad focus validation pending.

CI 45453449308 passed. DataStore Preferences 1.3.0 verified against https://developer.android.com/jetpack/androidx/releases/datastore on 2026-11-24 (stable October 21). Singleton preferences delegate, transactional toggles and lifecycle-aware collection persist grid and sort across sessions. No credentials stored here. New CI pending. Favorites and recent history remain separate pending features.

CI 45455044731 passed. DataStore now stores bounded favorite (50) and recent (20) folder URI lists, explicit remove/clear actions and grant-matched reopening. Saved nested folders reopen with root and destination breadcrumb only; full ancestry and file history not implemented. No new dependencies or permissions. New CI pending.

CI 45456009860 passed. Native ZipInputStream extraction to a new SAF directory, explicit confirmation, path traversal rejection, 32 depth/10000 entry and directory caps, 4 GiB actual output cap, no intentional overwrite, incomplete current file cleanup, path unit tests. Completed files retained on failure. Provider-specific naming and cancellation UI still require validation. ZIP creation and GZIP pending. New CI pending.

CI 45456884843 passed. Native ZipOutputStream creation for selected file or folder, bounded preflight, source metadata/size checks, no intentional overwrite, best-effort incomplete archive deletion. Both ZIP actions use existing ViewModel progress and cancellation UI. 4 GiB input cap and 10000 entries. No post-write archive verification yet. New CI pending.

CI 45457593482 passed. GZIP single-file compression/decompression via native GZIP streams, existing archive job/progress, SAF grants, collision check, 4 GiB uncompressed data limit, source metadata revalidation and best-effort incomplete output deletion. TAR.GZ currently extracts only the TAR stream. No new dependency or permission. New CI and provider integration tests pending.

CI 45458693284 passed. Apache Commons Compress 1.28.0 selected from official release index https://commons.apache.org/ (checked 2026-11-24). TAR/TAR.GZ/TGZ extraction uses TarArchiveInputStream, native GZIP wrapper, rejects links/sparse/special entries and invalid header checksum. Existing bounds and SAF operations retained. New CI, Android runtime compatibility and archive integration tests pending.

Scope correction: removed GZIP/TAR/TGZ code and UI, Commons Compress catalog/build dependency and active notice. ZIP remains. Multimedia and LAN are the primary roadmap. Earlier entries are historical, not current support. New CI pending.

CI 45459858303 passed after ZIP-only cleanup. Media3 player now exposes elapsed/duration progress, buffering state, explicit 10-second seek increments, seekability gating and horizontally scrollable TV controls. Applies to local and SMB playback. No new dependencies. New CI pending; runtime focus and streaming validation pending.

CI 45460943253 passed. MediaViewer full-size PlayerView with bottom TV overlay, four-second auto-hide during video playback, D-pad key-up reveal and focus restoration, persistent audio controls. No new dependencies. New CI and actual focus/SurfaceView overlay validation pending.

CI 45461762052 passed. Media3 native resize modes fit/zoom/stretch, playback speed 0.75–2x, media metadata title/artist listener, local and SMB shared player. No new dependencies. New CI and runtime validation pending. HDR and codec capability reporting not implemented in this batch.

CI 45462208452 passed. Native Media3 DefaultRenderersFactory decoder initialization fallback enabled; subtitle fractional text size S/M/L through SubtitleView; track information exposes MIME, codec string, dimensions, frame rate, channel count, sample rate, language and Media3 support/selection state. No new codecs bundled; no universal hardware support claim. New CI pending.

CI 45463525178 passed. Local sidecar subtitles matched against current folder entries by exact stem or dot-delimited suffix, capped at 16, attached at initial MediaItem creation using Media3 SubtitleConfiguration. Manual subtitle selection retained alongside automatic tracks. Added filename matching unit test. SMB sidecars and runtime validation pending.

CI 45464087752 passed. SMB Media3 data source now resolves each DataSpec URI against an explicit session allowlist of the media file and up to 16 matched sibling subtitle files. DefaultDataSource still routes local content URIs to platform sources. No arbitrary SMB path resolution or new dependency. New CI and real SMB integration tests pending.

CI 45465265655 passed. Music presentation uses embedded Media3 artwork and metadata, sampled BitmapFactory decode on IO, 8 MiB encoded artwork acceptance cap and 1024-pixel sampled dimension target, AMOLED placeholder and large title/artist/album. No external artwork lookup or new dependencies. New CI and TV layout validation pending.

CI 45466264115 passed. Shared media routing hints for generic MIME types in local/USB and SMB browser, preserving explicit media MIME. Unit tests cover generic MKV, FLAC, AVIF, reported audio MP4 and ZIP. Routing does not imply decoder support. New CI pending.

## Android TV stability audit (2026-11-24)

Base: `767eab3c3d0fb70491cb4f2cdae4b9b5953c1749`. [GitHub Actions 45480913362](https://github.com/ReinierTutoriales/VireoExplorerTV/actions/runs/45480913362) passed Debug build, unit tests and lint. The workflow had no TV runtime test and did not build Release. The local workflow change adds `assembleRelease`; it is verified locally and has not run on GitHub.

- Theme: the startup capture exposed dark body text on the AMOLED background. A non-interactive TV Material Surface now supplies the content color. The home screen was visually checked at 720p and 1080p. This verifies legibility, and visual alignment with the approved product design is pending.
- Explorer: OK opens folders and media. Holding OK opens file actions. The list and grid use stable document keys and attempt to restore focus to the last opened item. Home keeps storage, favorites and recent folders in a scrollable list.
- Permissions: the Android TV API 36 image resolves the folder picker to the platform's TV stub and returns without a tree grant. Home now reports that outcome and offers the existing SMB route. Local/USB browsing on this image remains unavailable through SAF. The merged manifest removes the unused Media3 wake lock permission. Android TV-specific system settings still vary by firmware.
- SMB: Back moves to the parent directory, disconnects at the root of a share, then returns to the app home. The connection dialog keeps invalid entries visible for correction. Discovery, connection, and empty-folder states have distinct messages.
- Media and cleanup: playback errors offer a retry, subtitle errors keep their own message, and the image viewer has a reachable Back action during decoding or failure. Storage analysis displays read-only access and keeps destructive actions disabled without a writable grant.
- Release: R8 previously stopped on JVM-only Kerberos references in SMBJ and optional expression filters in MBassador. The new rules are scoped to those missing types and preserve SMBJ event handlers used by reflection. See [SMBJ Android authenticator selection](https://raw.githubusercontent.com/hierynomus/smbj/v0.15.0/src/main/java/com/hierynomus/smbj/SmbConfig.java) and [SMBJ event bus](https://raw.githubusercontent.com/hierynomus/smbj/v0.15.0/src/main/java/com/hierynomus/smbj/event/SMBEventBus.java). Release remains unsigned.

Checks: `gradle assembleDebug testDebugUnitTest lintDebug assembleRelease` with SDK platform 37.2. The 17 unit tests passed. Lint reported 14 warnings and zero errors. The TV smoke script passed at 720p and 1080p on Android TV API 36, including the picker fallback. The [TV smoke script](../tests/tv_smoke.py) runs against an installed debug APK and checks cold start, initial focus, D-pad and Back across the SMB dialog and Apps & storage. Use `--expect-picker-stub` to also check the fallback on an image with the stub. A loopback connection to a closed SMB port displayed the connection error. Back disconnected and returned to the network screen, and the next Back returned home. SMB streaming and transfers, sustained playback, granted local/USB access, ZIP operations and actual cleanup deletions require integration fixtures. No physical-TV or performance results are recorded.


## Explorer home redesign (2026-11-24)

The supplied reference defines the fixed left navigation and the storage and category cards. The implementation uses TV Material focusable surfaces and list items, plus individually imported Google Material Symbols Rounded vectors. New home labels are available in English and Spanish. Focus starts on Internal storage and re-enters the content or the selected navigation item after a page change.

Internal and USB use existing Android folder grants and volume entry points. Search and categories operate in a chosen accessible folder and keep subfolders navigable. ZIP is the supported archive category. The transfer page represents the local file selection and operation state available to the explorer. SMB and the apps/cleanup manager route to their existing screens.

The local Debug and Release builds passed, along with 17 unit tests and lint with zero errors. The D-pad smoke test passed at 720p and 1080p on the Android TV API 36 image. Both resolutions were visually checked in Spanish. This image provides a picker stub, so the smoke test verifies the missing-picker feedback and the category's folder-selection entry rather than granted local file browsing.


## Explorer and playback interaction refinement (2026-11-24)

Folder navigation separates the abbreviated path, search, and secondary tools. The file list and grid use native TV Material selection instead of prefix symbols. Refresh keeps the last listing in place during the query and revalidates the active selection. A failed read clears the stale listing. The free-space query now runs when folder tools are visible.

SMB uses file names and type icons as the main entry points for browsing and playback. Media downloads and file operations remain separate controls. Player transport and format controls have separate rows that wrap with the available width. Progress polling pauses when playback controls are hidden. No performance measurements are recorded for these changes.


## Native TV dialogs, SMB session reuse, EXIF/remote image viewer, and LRU thumbnail cache (2026-11-24)

- Google TV Design Kit (Material 3 for TV) in Pure AMOLED (`VireoTheme.kt`, `ExplorerHome.kt`, `FolderEntry.kt`, `MainActivity.kt`, `TvDialogs.kt`): migrated the home navigation sidebar to the official collapsible `androidx.tv.material3.NavigationDrawer` + `NavigationDrawerItem` icon rail (expanding smoothly on D-pad focus with `Settings` anchored at the bottom); adopted `24.dp` rounded tonal cards (`VireoCardShape`) with two-line hierarchy (title + localized EN/ES storage subtitles) and `1.02f` focus micro-scale; styled selectable options and active filter chips (`ClearFilterButton`) with circular `#80DCC0` accent badges and `✕` dismiss cues; styled `TvTextField` with `16.dp` rounded outlined containers (`VireoInputShape`), hairline resting borders, and `2.dp` `#80DCC0` focus borders; added pill-rounded `VireoProgressBar`.
- Native state-based TV dialogs (`TvDialogs.kt`): replaced all legacy `android.app.AlertDialog` and `android.widget.EditText` instances across `MainActivity.kt`, `SmbBrowser.kt`, and `StorageAnalysis.kt` with Compose for TV AMOLED dialogs (`TvConfirmDialog`, `TvInputDialog`, `SmbConnectDialog`) passing `TextFieldState` directly (`BasicTextField` and `BasicSecureTextField` with `clearText()` on password dismiss/submit), avoiding stale closure sync bugs and placing initial focus on Cancel for destructive confirmations.
- SMB session pooling, cancellation, and remote images (`SmbDataSource.kt`, `SmbBrowser.kt`): `SmbShareSession` copies credentials internally, zeroes them on `close()`, closes sockets asynchronously on a daemon thread on disposal, and exposes non-blocking `executeSuspend` on `Dispatchers.IO` (preserving coroutine cancellation during SMB uploads/downloads) alongside synchronous `execute` for `SmbDataSource.open()`. Added IPv6-safe authority formatting (`formatSmbUriAuthority`) and direct SMB image viewing via bounded streaming (`MAX_IMAGE_STREAM_BYTES = 32 MiB`).
- Image viewer (`ImageViewer.kt`): eliminated double static image decoding on API 28+ by extracting `BitmapDrawable.bitmap` from a single `ImageDecoder.decodeDrawable` pass while supporting lifecycle-bound `AnimatedImageDrawable` playback; added `androidx.exifinterface:exifinterface:1.4.2` orientation normalization for sampled `BitmapFactory` decoding; isolated 4-way D-pad panning (`clampPanOffset`) to a focusable viewport mode so zoomed images never trap D-pad navigation in the bottom toolbar; added `Previous`/`Next` and 5-second `Slideshow`.
- Media player and progressive duplicate hashing (`MediaViewer.kt`, `Storage.kt`): configured seamless frame-rate matching (`C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS`), repeat-safe hardware TV remote media transport keys (`KeyDown` with `repeatCount == 0` for Play/Pause, `KeyDown` hold-to-scrub for FastForward/Rewind), single-prepare initial manual subtitle configuration, chronological LRU playback resume eviction (`reconstructOrderedResumeMap` persisted via `_lru_order` capped at 200 entries), and separate `64 MiB` prefix sample hash budget (`sampledPrefixSha256`) from `512 MiB` full SHA-256 budget.
- Modal TV contextual file actions, memory trimming, and Android 12+ backup rules (`MainActivity.kt`, `SmbBrowser.kt`, `FileThumbnail.kt`, `AndroidManifest.xml`, `data_extraction_rules.xml`): long-pressing OK opens an AMOLED modal dialog without unmounting or stealing focus from the underlying file list/grid, restoring focus cleanly when items are deleted; wired `addOnTrimMemoryListener` to `trimThumbnailCache` alongside `LruCache<String, Bitmap>` and `Dispatchers.IO.limitedParallelism(4)`; added `REQUEST_INSTALL_PACKAGES` consent flow and `data_extraction_rules.xml` excluding `smb_credentials.xml` from cloud backup and device-to-device transfer.
- Verification: `gradle assembleDebug testDebugUnitTest lintDebug assembleRelease --no-daemon` passed with 0 compiler warnings, 24 unit tests passing, 0 lint errors, and minified Release APK generated.
