# Delivery roadmap
Proprietary Android TV/Google TV only. Follow ARCHITECTURE.md, EXECUTION.md and AGENTS.md. Each phase requires a functional vertical slice, tests, documentation and actual evidence.
1. Foundation: resolve SDK/toolchain, reproducible build, lint, tests, startup, dependency/license inventory.
2. Native AMOLED UI: TV Material, D-pad focus restoration, list/grid, accessibility, empty/error/loading states, localization.
3. Storage: SAF and eligible all-files access, USB/SD, capabilities, search, favorites, selection, create/rename/delete with consent.
4. Transfers: streaming copy/move, progress, cancellation, conflicts, integrity before source deletion, low-space/disconnect tests.
5. Local intelligence and cleaner: incremental index, bounded scanning, duplicate byte verification, explicit review, revalidation, no fake RAM cleaning.
6. Multimedia: Media3 hardware-first playback, tracks/subtitles/resume, images, capability matrix, selective licensed codec extensions.
7. Network: SMB2/3 first, then SFTP, NFS, WebDAV, FTP/FTPS and DLNA; discovery, encrypted credentials, authenticated connections and remote streaming.
8. Archives: ZIP only: browse/create/extract, traversal and expansion limits.
9. Hardening: permissions, dependency licenses, security, R8, startup/frame/memory/throughput measurement, baseline profiles.
10. Release: complete regression matrix, signed release only with owner approval, documentation and verified artifacts.
Combine independent work where safe; never mark a phase complete without evidence. No device-specific assumptions. Latest compatible stable releases verified before adoption. Compile SDK 37.2 attempted to satisfy Compose metadata; target 36 and min 24 retained provisionally. Verify current CI and SDK availability before further dependency expansion.

Detailed acceptance contracts: ENGINES.md, UI.md, PLAYER.md, IMAGE_VIEWER.md. Multimedia includes audio/video tracks, external and embedded subtitles, native image viewer, bounded memory and remote streaming. All features share provider capabilities and security policies.

## Application management and intelligent cleanup
Follow docs/APP_MANAGEMENT.md as a binding contract. Include scoped installed-app inventory, system/user distinction, optional authorized storage and usage statistics, official app settings for cache/data/uninstall/disable, consent-based APK installation, accessible temporary/incomplete files, residual APKs, empty folders, own inactive cache and duplicate review. Never equate candidate names or age with junk. Protect system/private data, revalidate before deletion, preserve a duplicate, report actual reclaimed bytes and partial failures. No root, hidden APIs, forced process killing or fake RAM optimization. Verify permission eligibility and device capability; test revoked access, active files and unavailable settings. Keep UI native TV and operations bounded, cancellable and off the main thread. Implementation status belongs in IMPLEMENTATION.md.

## Binding TV-first scope (2026-11-24)
Prioritize multimedia playback, audio/video/image compatibility, subtitles, hardware decoding, TV remote UX and secure LAN discovery, browsing and streaming. ZIP is the only supported archive format: native Java creation/extraction with bounded SAF IO. Do not add GZIP, TAR, TGZ, BZIP2, XZ, 7z, RAR or archive libraries. Keep storage, cleaner and app management secondary to multimedia/network; prioritize complete TV-relevant slices and remove obsolete consumers and dependencies. This supersedes earlier archive expansion plans.
