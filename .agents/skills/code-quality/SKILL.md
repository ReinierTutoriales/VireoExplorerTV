---
name: code-quality
description: Audit correctness, performance, security, accessibility and obsolete code.
---
# Code quality
- Trace full execution paths and lifecycle ownership before refactoring. Review cancellation, resource release, concurrency, error recovery and main-thread blocking.
- Prefer native documented Android/AndroidX APIs with compatibility across supported API levels; select by capabilities, not hardware model.
- Audit permission necessity, exported components, untrusted input, storage access and dependency vulnerabilities against actual project use.
- Remove duplicate, obsolete and dead code only after checking callers, reflection, resources, manifests and tests.
- Measure memory, startup, frame timing and latency when claiming improvement. Never trade correctness or visual quality for unmeasured gains.
- Distinguish verified findings from hypotheses and document reproduction and affected paths.

## Integrated project requirements
Audit shared provider boundaries, lifecycle, bounded memory/IO, cancellation, native UI focus, decoder capability, permission scope, credentials and destructive operations. Remove dead code only after consumer checks. Reject redundant stacks and unmeasured performance claims.

## Application management and intelligent cleanup
Follow docs/APP_MANAGEMENT.md as a binding contract. Include scoped installed-app inventory, system/user distinction, optional authorized storage and usage statistics, official app settings for cache/data/uninstall/disable, consent-based APK installation, accessible temporary/incomplete files, residual APKs, empty folders, own inactive cache and duplicate review. Never equate candidate names or age with junk. Protect system/private data, revalidate before deletion, preserve a duplicate, report actual reclaimed bytes and partial failures. No root, hidden APIs, forced process killing or fake RAM optimization. Verify permission eligibility and device capability; test revoked access, active files and unavailable settings. Keep UI native TV and operations bounded, cancellable and off the main thread. Implementation status belongs in IMPLEMENTATION.md.
