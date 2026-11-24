# Implementation status
Initial slice: Compose TV launcher, persisted SAF folder grants, lazy directory listing, breadcrumbs/back, refresh, file properties and external viewer handoff. No integrated media, network, cleaner or transfer UI yet. Storage provider create/delete primitives require UI confirmation and integration before exposure. SAF availability varies across TV firmware; all-files fallback is pending. Build and focus validation pending CI. API 24 minimum, target/compile 37 provisional pending compatibility checks. Compose 1.13.0, TV Material 1.1.0 and AGP 9.5.2 checked against official Android release pages on 2026-11-24; other pins reused from VireoAirTV and need independent verification. CI uses explicit Gradle distribution, wrapper not yet included.

## AMOLED TV UI
Mandatory native Jetpack Compose for TV and AndroidX TV Material, latest compatible stable versions verified against official releases. Pure black #000000 window and content background; #121212 surfaces, #80DCC0 mint accent, #F2F4F3 primary text. Centralize tokens in VireoTheme; preserve native TV focus, typography and D-pad behavior. No mobile-only Material components or unnecessary UI libraries. Verify startup, focus contrast, back navigation and accessibility. Theme source introduced in 147e72c; build and visual acceptance remain unverified.

CI run 45415816950: sdkmanager runs but cannot find platforms;android-37. Use API 36 provisionally for compile/target until API 37 availability is verified. Build remains unverified.

SAF operations: create folder, rename and confirmed deletion of selected files wired to UI, provider flags checked for rename/delete. Native framework input/confirmation dialogs are interim until TV Material dialog and text input validation. Directory rename/delete, transfer, direct storage fallback and focus validation remain pending. No new dependencies.

Run 45417554338 resolved dependencies but failed checkDebugAarMetadata: Compose 1.13.0 requires compile API 37.1 or newer. Attempt compile SDK 37.2 using minor API DSL and matching SDK package; retain target 36 and min 24. SDK availability and DSL/build still require CI confirmation.
