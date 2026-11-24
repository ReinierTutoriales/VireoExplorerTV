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

## Binding product and execution specifications
Read [architecture](docs/ARCHITECTURE.md) and [execution](docs/EXECUTION.md) before implementation. Apply native-tv-development, storage-media-network and efficient-delivery skills alongside existing skills. These documents define the complete target, not implemented features. Keep implementation status and evidence accurate. Prefer minimum complete passes, never skip validation.

## AMOLED TV UI
Mandatory native Jetpack Compose for TV and AndroidX TV Material, latest compatible stable versions verified against official releases. Pure black #000000 window and content background; #121212 surfaces, #80DCC0 mint accent, #F2F4F3 primary text. Centralize tokens in VireoTheme; preserve native TV focus, typography and D-pad behavior. No mobile-only Material components or unnecessary UI libraries. Verify startup, focus contrast, back navigation and accessibility. Theme source introduced in 147e72c; build and visual acceptance remain unverified.

## Licensing
Project-owned code, assets and documentation are proprietary, all rights reserved. Do not relicense or publish source without owner authorization. Preserve third-party notices and license rights; verify compatibility with proprietary distribution before adding dependencies or upstream code. Flag copyleft obligations before integration. Repository visibility is a separate owner decision.

## Engine specifications
Read [docs/ENGINES.md](docs/ENGINES.md) before storage, media, network, cleaner or archive changes. Candidate libraries are not approved pins; verify compatibility, security, licensing and current stable releases before adoption.

## Native UI performance contract
Follow [UI specification](docs/UI.md) (repository-root path). Prefer official TV Material and Compose Foundation primitives, native focus and vector resources; no redundant UI stacks. Measure startup, frame timing, memory and focus regressions before claiming improvements.

## Integrated player
Follow [docs/PLAYER.md](docs/PLAYER.md) for all playback changes. Hardware-first Media3, native TV controls, subtitle support and lifecycle correctness are mandatory.

## Integrated image viewer
Follow [docs/IMAGE_VIEWER.md](docs/IMAGE_VIEWER.md). Native decoding and rendering first, bounded memory, lifecycle-aware animation and capability-based format extensions.

## Unified specification index
Use docs/ARCHITECTURE.md, docs/EXECUTION.md and docs/ROADMAP.md for all work; docs/ENGINES.md for storage/network/cleaner/archives; docs/UI.md for interface; docs/PLAYER.md and docs/IMAGE_VIEWER.md for multimedia. docs/IMPLEMENTATION.md records evidence, not targets. Apply relevant skills together. Implementation requests require code and validation, not documentation alone unless blocked.

Application and system storage management: follow docs/APP_MANAGEMENT.md. Initial visible-app inventory and official settings shortcuts implemented; actual temporary-file cleanup, usage statistics and own-cache cleanup pending. No broad package visibility permission added. New CI pending.
