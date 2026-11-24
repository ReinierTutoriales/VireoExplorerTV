---
name: efficient-delivery
description: Minimize development passes without reducing verification or completeness.
---
Read docs/EXECUTION.md. Inspect once broadly, batch related fixes, implement complete slices with regression tests and documentation. Use official current documentation and upstream evidence; verify dependency compatibility before edits. Avoid speculative rewrites, repeated planning, artificial waiting, dead code and redundant dependencies. Run focused checks early and full relevant checks before completion. Fix root causes rather than suppress errors. Record actual checks, commit, blockers and verified lessons. Do not claim work completed without evidence or bypass repository protections.

## Integrated project requirements
Use docs/ROADMAP.md as delivery order and docs/IMPLEMENTATION.md as evidence ledger. Resolve build blockers before expanding dependency graph. Batch independent verified changes, deliver functional slices rather than repeated documentation-only work when implementation is requested.

## Application management and intelligent cleanup
Follow docs/APP_MANAGEMENT.md as a binding contract. Include scoped installed-app inventory, system/user distinction, optional authorized storage and usage statistics, official app settings for cache/data/uninstall/disable, consent-based APK installation, accessible temporary/incomplete files, residual APKs, empty folders, own inactive cache and duplicate review. Never equate candidate names or age with junk. Protect system/private data, revalidate before deletion, preserve a duplicate, report actual reclaimed bytes and partial failures. No root, hidden APIs, forced process killing or fake RAM optimization. Verify permission eligibility and device capability; test revoked access, active files and unavailable settings. Keep UI native TV and operations bounded, cancellable and off the main thread. Implementation status belongs in IMPLEMENTATION.md.
