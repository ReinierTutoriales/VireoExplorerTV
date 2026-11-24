# VireoExplorerTV — internal architecture

## Product contract
Android TV and Google TV exclusively. Kotlin, Compose for TV and AndroidX TV Material; D-pad-first, AMOLED dark theme, visible focus, adaptive layouts, vector icons, accessible dialogs and deterministic back/focus restoration. No WebView or cross-platform UI. Global capability-based compatibility, not device-specific assumptions. Local-first, no proprietary telemetry or mandatory accounts.

## Functional scope
- Explorer: internal storage, removable USB/SD, SAF persisted grants, MediaStore and eligible all-files access; list/grid, breadcrumbs, favorites, recents, search by name/type/size/date, properties, free space, multi-select, create/rename/copy/move/delete, authorized APK installation.
- Unified storage: capability-aware provider interface for local, SAF and remote resources; stable identities, streaming reads/writes, pagination, cancellation, conflict resolution, progress, verified copy before source deletion, resumability only where supported. No silent overwrite.
- Intelligence: incremental local index, file classification, large/old/empty files, incomplete downloads, redundant APKs and potential residues; duplicate candidates grouped by size then progressive hash and final byte comparison. Bounded IO, concurrency, caches and thumbnails; invalidate stale results; no cloud AI requirement.
- Cleaner: explainable candidates, risk and recoverable space, explicit review, revalidate before deletion, preserve at least one duplicate, no private app data bypass, fake RAM boosters or indiscriminate process killing. Own cache cleanup; other apps' cache only through supported system mechanisms. Remote cleaning opt-in.
- Archives: ZIP creation/extraction, path traversal and symlink escape prevention, expansion and disk-space limits.
- Media: Media3/MediaCodec hardware-first, capability-based fallback, audio/video/image viewer, tracks, subtitles, resume, playlists, metadata, artwork, zoom, rotation, slideshow, EXIF and color management. Streaming local/USB/network with seekable sources. Refresh-rate matching, HDR10/HDR10+/HLG/Dolby Vision and audio passthrough only when supported.
- Video targets: AV1, HEVC, AVC, VP8/9, MPEG-2/4, VC-1; evaluate VVC. Containers MKV, MP4, WebM, AVI, MOV, TS/M2TS, MPEG, FLV. Audio targets FLAC, ALAC, Opus, AAC, AC-3/E-AC-3, TrueHD/Atmos, DTS variants, MP3, Vorbis, PCM/WAV, WavPack. Images JPEG, PNG, WebP, AVIF, JPEG XL, HEIF/HEIC, GIF, SVG, TIFF/BMP, evaluate RAW/DNG. Subtitles SRT, ASS/SSA, WebVTT, PGS. These are targets, not claims of existing support.
- Network: SMB2/3, NFS3/4, SFTP, FTP/FTPS, WebDAV/HTTPS, DLNA/UPnP. Discovery via mDNS/DNS-SD, WS-Discovery and SSDP as applicable, manual host entry, favorites, reconnect, streaming, remote operations by server permissions. SMB1 disabled; plaintext protocols explicit opt-in; verify TLS and SSH host identity, SMB signing/encryption where supported. Keystore-backed credential encryption, no secrets in logs.
- Transfer: bounded concurrency, cancellation, partial-file cleanup, integrity checks, cross-provider moves, disconnect recovery and explicit conflicts.

## Technology selection
Prefer Android Framework and AndroidX: Lifecycle, Navigation, Coroutines/Flow, SAF/MediaStore, Room for justified index, DataStore, WorkManager for deferrable tasks, Keystore, Media3, ImageDecoder. Introduce dependencies only with a real consumer. Use latest compatible stable AGP, Kotlin, Gradle, SDK and libraries, verified against official releases and resolved artifacts; no invented version numbers. Use version catalog and reproducible dependency verification. NDK C/C++ only for proven codec gaps; evaluate selective FFmpeg, dav1d, libjxl, libavif, LibRaw, with ABI, 16 KB page compatibility, licensing and security review. No universal 4K/8K or proprietary codec guarantee.

## Boundaries
Explorer, Intelligence, Cleaner, Media, Network, Transfer, Archives, Security share unified storage and capability contracts. Start with cohesive packages; extract Gradle modules only when boundaries justify build/maintenance cost. No empty scaffolding or speculative abstractions. Keep UI state separate from IO; lifecycle-aware cancellation; no disk/network work on main thread. Avoid always-on services. Persist only needed data. Use Android permissions and user consent; no root or ADB requirement for ordinary use. Verify current target-SDK permission and distribution policies.

## Evidence
Track startup, frame timing, focus navigation, memory, idle CPU, APK size, throughput and cancellation with reproducible measurements. R8/resource shrinking, Baseline Profiles and Macrobenchmark when runnable. No claimed gains without measured baseline. Physical testing is separate from emulator/CI; global Android TV baseline is the default.

## Visual identity
Pure AMOLED black (#000000) window and content background, near-black elevated surfaces, mint accent (#80DCC0), high-contrast text. Central AndroidX TV Material color scheme; native TV component focus and typography defaults. Verify focus contrast, D-pad navigation and startup on emulator before claiming visual acceptance. Do not introduce mobile Material widgets or custom focus systems without evidence.

## Detailed contracts
ENGINES.md, UI.md, PLAYER.md and IMAGE_VIEWER.md refine this architecture; EXECUTION.md and ROADMAP.md govern delivery. Preserve proprietary licensing and shared provider boundaries; do not treat candidate dependencies or target formats as implemented support.

## Application management and intelligent cleanup
Follow docs/APP_MANAGEMENT.md as a binding contract. Include scoped installed-app inventory, system/user distinction, optional authorized storage and usage statistics, official app settings for cache/data/uninstall/disable, consent-based APK installation, accessible temporary/incomplete files, residual APKs, empty folders, own inactive cache and duplicate review. Never equate candidate names or age with junk. Protect system/private data, revalidate before deletion, preserve a duplicate, report actual reclaimed bytes and partial failures. No root, hidden APIs, forced process killing or fake RAM optimization. Verify permission eligibility and device capability; test revoked access, active files and unavailable settings. Keep UI native TV and operations bounded, cancellable and off the main thread. Implementation status belongs in IMPLEMENTATION.md.
