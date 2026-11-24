# Delivery roadmap
Proprietary Android TV/Google TV only. Follow ARCHITECTURE.md, EXECUTION.md and AGENTS.md. Each phase requires a functional vertical slice, tests, documentation and actual evidence.
1. Foundation: resolve SDK/toolchain, reproducible build, lint, tests, startup, dependency/license inventory.
2. Native AMOLED UI: TV Material, D-pad focus restoration, list/grid, accessibility, empty/error/loading states, localization.
3. Storage: SAF and eligible all-files access, USB/SD, capabilities, search, favorites, selection, create/rename/delete with consent.
4. Transfers: streaming copy/move, progress, cancellation, conflicts, integrity before source deletion, low-space/disconnect tests.
5. Local intelligence and cleaner: incremental index, bounded scanning, duplicate byte verification, explicit review, revalidation, no fake RAM cleaning.
6. Multimedia: Media3 hardware-first playback, tracks/subtitles/resume, images, capability matrix, selective licensed codec extensions.
7. Network: SMB2/3 first, then SFTP, NFS, WebDAV, FTP/FTPS and DLNA; discovery, encrypted credentials, authenticated connections and remote streaming.
8. Archives: ZIP browse/create/extract, traversal and expansion limits; evaluate other formats by license and demand.
9. Hardening: permissions, dependency licenses, security, R8, startup/frame/memory/throughput measurement, baseline profiles.
10. Release: complete regression matrix, signed release only with owner approval, documentation and verified artifacts.
Combine independent work where safe; never mark a phase complete without evidence. No device-specific assumptions. Latest compatible stable releases verified before adoption. API 36 provisional due to recorded SDK package failure; revisit when API 37 is available.
