# VireoExplorerTV — Agent Instructions

## Project
Android TV / Google TV project. At adoption the repository contains only README.md; architecture, stack, SDK levels and build commands must be established from actual implementation, not inherited from VireoAirTV.

## Mandatory workflow
1. Inspect remote HEAD, source, consumers, tests and official current documentation.
2. Verify latest compatible stable dependencies and tooling on every relevant change; update outdated versions and validate migrations. Do not use prereleases without owner direction.
3. Implement the minimum complete vertical slice in one pass where feasible. Remove superseded code, resources, dependencies and workflows after checking consumers.
4. Validate focused regressions, build, lint, security and relevant integration checks. Report actual results and blockers, never fabricated measurements.
5. Use platform-wide Android TV capability and API baselines, never device-model-specific assumptions. Physical validation is distinct from CI/emulator coverage and is not an automatic delivery gate.
6. Keep changes cohesive, preserve quality, and respect owner decisions on integration, releases and signing.

## Skills
Read relevant instructions in .agents/skills: agent-governance before repository changes, precise-coding for implementation, code-quality for auditing, testing-discipline for validation, tv-ui for interface changes, upstream-port for dependency and upstream research, no-ai-slop for prose only.
