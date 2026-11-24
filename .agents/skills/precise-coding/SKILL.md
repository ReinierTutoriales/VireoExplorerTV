---
name: precise-coding
description: Evidence-based implementation and complete vertical slices.
---
# Precise coding
- Read actual source, contracts, consumers, tests and build configuration before editing. Never invent APIs or platform behavior.
- Check official documentation and current stable release metadata; inspect library source when needed. State assumptions explicitly.
- Trace all callers, resource IDs, preference keys, manifest entries and workflows. Use uniquely anchored edits and inspect the resulting diff.
- Implement the smallest complete solution, not preparation-only commits. Delete dead paths in the same change, including unused state and wrappers.
- Follow existing naming and comment conventions; comments explain contracts and tradeoffs.
- Use git diff --check and available build/test/lint checks. Report unavailable checks explicitly.

## Integrated project requirements
Implement capability-aware shared storage contracts; do not assume every provider supports seek/write/delete. Handle cancellation, errors, permissions and lifecycle explicitly. No speculative features, placeholder UI or unsupported format claims.
