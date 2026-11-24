---
name: storage-media-network
description: Implement and audit storage, cleaner, transfer, media and network features safely and efficiently.
---
Read docs/ARCHITECTURE.md. Use unified capability-aware storage contracts, SAF and platform permission checks. Stream rather than load entire files. Verify copy before deleting source, handle partial writes and cancellation. Revalidate cleanup candidates and require explicit destructive consent; never delete all duplicates. Protect archive extraction from traversal and resource exhaustion. Prefer Media3 and hardware decoding; add native codecs only for demonstrated gaps after license, ABI and 16 KB review. Verify remote server identity, encrypt credentials, bound discovery and network IO, support disconnects. Test malformed inputs, permission revocation, USB removal, low disk space and remote failures. No unsupported codec or protocol claims.

## Integrated project requirements
Read docs/ENGINES.md, docs/PLAYER.md and docs/IMAGE_VIEWER.md. Apply shared streaming/seek contracts, hardware-first Media3 with track/subtitle selection, viewport-sized image decoding, bounded cache, local intelligence, verified duplicate preservation and secure protocol-specific authentication. Candidate libraries require current stable release, license and compatibility verification.

Application and system storage management: follow docs/APP_MANAGEMENT.md. Initial visible-app inventory and official settings shortcuts implemented; actual temporary-file cleanup, usage statistics and own-cache cleanup pending. No broad package visibility permission added. New CI pending.

Mandatory permission contract: docs/PERMISSIONS.md. Verify current official Android API and target-SDK rules before each permission change; audit merged manifest and consumers; contextual consent, OS-derived state, denial/revocation tests, no unused or speculative permissions. Android 17 LAN migration must accompany target SDK 37.
