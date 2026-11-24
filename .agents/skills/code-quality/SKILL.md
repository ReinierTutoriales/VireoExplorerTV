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
