---
name: upstream-port
description: Research, audit and integrate documented upstream fixes and stable dependencies.
---
# Upstream integration
- Identify actual dependencies and pins from source and manifests. Consult primary release notes, source diffs, official registries and advisories.
- Verify latest compatible stable versions; inspect migration requirements, transitive changes and affected APIs.
- Prefer released versions; pin reproducible dependencies and verify hashes for build downloads when applicable.
- For individual ports, inspect prerequisites and follow-up fixes, keep provenance and license notices, review every diff, and test the resulting behavior.
- Search the local codebase for the same verified bug class rather than merely copying upstream.
- For parsers and native boundaries if present, use bounded input validation, fuzzing and sanitizers where supported.
- Record accepted, rejected and pending changes with reasons and actual validation.
