---
name: tv-ui
description: Native Android TV interaction, focus, layout and accessibility.
---
# TV UI
- Follow current Android Developers TV and Compose for TV guidance. Prefer latest compatible stable native components.
- Design for ten-foot readability, responsive layouts, clear focus, D-pad navigation, Back behavior and accessible localized labels.
- Trace key down/up, repeat and long press; distinguish focus, pointer, touch and media keys where supported. Restore logical focus after dialogs, transitions and list refresh; never default to destructive actions.
- Avoid unnecessary recomposition, continuous animations and heavy assets. Preserve scroll and focus state.
- Render changed screens at 720p and 1080p, check scaling for 4K, and inspect focused states. Include RTL and other supported form factors when relevant.
- Use actual input tests alongside screenshots; screenshots alone do not validate interaction. Avoid claiming device validation without a device run.

## AMOLED TV UI
Mandatory native Jetpack Compose for TV and AndroidX TV Material, latest compatible stable versions verified against official releases. Pure black #000000 window and content background; #121212 surfaces, #80DCC0 mint accent, #F2F4F3 primary text. Centralize tokens in VireoTheme; preserve native TV focus, typography and D-pad behavior. No mobile-only Material components or unnecessary UI libraries. Verify startup, focus contrast, back navigation and accessibility. Theme source introduced in 147e72c; build and visual acceptance remain unverified.

## Native UI performance contract
Follow [UI specification](docs/UI.md) (repository-root path). Prefer official TV Material and Compose Foundation primitives, native focus and vector resources; no redundant UI stacks. Measure startup, frame timing, memory and focus regressions before claiming improvements.

## Integrated project requirements
Follow docs/UI.md and viewer specs. AMOLED tokens, official Material Symbols Rounded local vectors with provenance, native TV controls, stable lazy keys, deterministic focus/back, accessible labels, contextual actions and no eager decoding/scanning.

## Official implementation first — mandatory execution gate
Before writing or refactoring any feature, inspect the existing consumers and locate the current official Android/AndroidX API, maintained sample and release metadata. Prefer adopting the complete supported component or documented pattern over hand-written equivalents. Verify actual stable artifact availability, API/target requirements, TV suitability and licensing; record URLs, date and compatibility evidence. Do not assume a newer library is appropriate merely because it exists.
- Architecture: use ViewModel, lifecycle-aware Flow collection, SavedStateHandle and official navigation patterns where justified; avoid custom lifecycle, state serialization and navigation frameworks. Evaluate Navigation 3 against TV focus and supported versions before adoption.
- TV: Compose for TV, TV Material, official focus and lazy layout APIs; avoid custom widgets or key interception unless a demonstrated gap requires them.
- Storage/media/security: use SAF, DocumentsContract, StorageManager, ContentResolver, Media3, ImageDecoder, Keystore and official permission contracts. Use maintained protocol libraries only for missing platform capabilities.
- Deliver coherent vertical slices in the fewest safe passes: inspect, select official implementation, integrate, remove superseded code, test and verify actual CI. No repetitive micro-commits or documentation-only substitutes for requested implementation. Check every edited call site and API boundary before committing. Do not use blind text substitutions.
- Custom code requires a concrete unsupported requirement and focused regression tests. Prefer official samples as references, not wholesale imports; preserve licenses. No invented APIs, unverified version claims, unnecessary dependencies or performance claims without measurements.
- Report actual evidence and blockers, not planned work as completed. Never bypass lint or weaken permissions to make CI green.

## Content-first explorer gate
Folder browsing must prioritize visible file/folder content rather than permanent side panels or decorative cards. Use compact native navigation, adaptive lazy grids, recognizable local Material Symbols and contextual details on demand. Inspect actual screenshots and D-pad traversal before claiming visual acceptance. Current stable Compose 1.13.0 and TV Material 1.1.0 verified 2026-11-25; do not add obsolete icon packs or mobile Material stacks.
