---
name: precise-coding
description: Preventive evidence-based implementation and complete vertical slices.
---
# Precise coding

Apply AGENTS.md and docs/EXECUTION.md as the canonical project contracts.

## 1. Preflight before any edit
- Resolve current remote HEAD, active PR and actual source. Inspect all affected consumers, tests, manifests, resources, dependency metadata and CI configuration together.
- Prefer complete official framework/AndroidX implementations and maintained samples. Verify the exact API, minimum SDK, target behavior, published stable artifact and compatible toolchain; documentation alone does not prove package availability. Check live upstream metadata, not remembered versions.
- Inventory confirmed defects and adjacent failure paths across the entire requested slice. Record evidence, root cause, affected paths and acceptance checks. Separate confirmed defects from hypotheses. Do not stop at the first error or claim exhaustive correctness without coverage.

## 2. Implement coherently
- Resolve all confirmed related defects in one coherent batch where safe. Include UI, state, permissions, lifecycle, provider behavior, errors, tests and necessary documentation; do not substitute test-only or prose changes for a requested functional correction.
- Reuse existing working code and official patterns; remove superseded code, resources, dependencies and redundant wrappers after tracing consumers. Avoid blind substitutions, speculative abstractions and repetitive micro-commits.
- Handle partial permissions, revocation, cancellation, unavailable activities/providers, API boundaries and data integrity explicitly. Do not assume every storage provider supports seek/write/delete.
- Use native TV controls, stable dependencies and platform-wide capability baselines. Preserve security, data, licenses, signing and unrelated working behavior.

## 3. Validate and close
- Review the complete diff and every edited call site; run cheap static/syntax/configuration checks before expensive builds. Apply testing-discipline for the full acceptance gate.
- Correct all verified in-scope failures and adjacent manifestations of their root causes. Re-run affected checks and regressions. Never weaken checks to obtain green CI.
- Report actual commit, verified results, remaining failures and unavailable evidence. A plan, successful compilation or newly added test is not proof that a product defect was corrected.
- If blocked, continue independent safe work; disclose the exact blocker and unverified scope. Do not promise asynchronous work or repeat an unchanged status as progress.
