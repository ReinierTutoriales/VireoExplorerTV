---
name: storage-media-network
description: Implement and audit storage, cleaner, transfer, media and network features safely and efficiently.
---
Read docs/ARCHITECTURE.md. Use unified capability-aware storage contracts, SAF and platform permission checks. Stream rather than load entire files. Verify copy before deleting source, handle partial writes and cancellation. Revalidate cleanup candidates and require explicit destructive consent; never delete all duplicates. Protect archive extraction from traversal and resource exhaustion. Prefer Media3 and hardware decoding; add native codecs only for demonstrated gaps after license, ABI and 16 KB review. Verify remote server identity, encrypt credentials, bound discovery and network IO, support disconnects. Test malformed inputs, permission revocation, USB removal, low disk space and remote failures. No unsupported codec or protocol claims.
