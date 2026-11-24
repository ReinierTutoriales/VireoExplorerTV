# Upstream verification
2026-11-24: Android official reference confirms PACKAGE_USAGE_STATS can be user-granted through Settings and StorageStatsManager queries belong on worker threads: https://developer.android.com/reference/android/app/usage/StorageStatsManager and https://developer.android.com/reference/android/Manifest.permission . This supports the narrow manifest lint exception, not a privileged grant. Jetpack stable index observed Media3 1.11.1 and TV Material 1.1.0: https://developer.android.com/jetpack/androidx/versions/stable-channel . Remaining catalog and toolchain versions need individual release/artifact verification; no version changed in this documentation batch.

Current catalog snapshot:
```toml
[versions]
agp = "9.5.2"
kotlin = "2.4.21"
compose = "1.13.0"
tv = "1.1.0"
activity = "1.13.0"
coroutines = "1.11.0"
media3 = "1.11.1"
[libraries]
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
media3-session = { module = "androidx.media3:media3-session", version.ref = "media3" }
tv-material = { module = "androidx.tv:tv-material", version.ref = "tv" }
compose-foundation = { module = "androidx.compose.foundation:foundation", version.ref = "compose" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity" }
coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
[plugins]
android = { id = "com.android.application", version.ref = "agp" }
compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }

```

## Mandatory upstream-first verification (2026-11-24)
Before each implementation batch, verify current official Android platform behavior changes, API contract, permissions, target/min/compile SDK and actual SDK package availability. Check every direct dependency and tool against its official release notes and published artifact metadata; use newest compatible stable release, never assume a version copied from another repository is current. Record date, URL, installed and candidate versions, compatibility and exceptions. Check Kotlin/AGP/Gradle/JDK matrix, Compose alignment, Media3 and TV releases, transitive conflicts, license/security and native ABI/page size. No speculative upgrades, dynamic version selectors or unnecessary libraries. Run focused tests, build, lint and release checks before declaring completion; inspect actual CI logs and fix root causes. Suppress lint only locally with documented API justification and regression coverage; never globally silence errors. Optimize from measured evidence, remove replaced code and avoid unused scaffolding. If upstream or runtime verification is unavailable, disclose and leave status unverified. Official entry points: https://developer.android.com/latest-updates ; https://developer.android.com/jetpack/androidx/versions ; https://developer.android.com/jetpack/androidx/versions/stable-channel ; https://developer.android.com/build/releases/gradle-plugin ; https://developer.android.com/reference ; https://developer.android.com/training/tv ; https://developer.android.com/media/media3 ; https://kotlinlang.org/docs/releases.html ; https://docs.gradle.org/current/userguide/compatibility.html .

2026-11-24: official Lifecycle release page https://developer.android.com/jetpack/androidx/releases/lifecycle identifies 2.11.1 stable (Nov 18); 2.12 alpha not selected. https://developer.android.com/topic/libraries/architecture/lifecycle recommends LifecycleEventEffect for one-shot lifecycle events and collectAsStateWithLifecycle for Flow. MainActivity adopts both, volume receiver collection follows STARTED lifecycle, explicit runtime-compose and savedstate dependencies aligned. Artifact resolution and compatibility pending CI. Previous CI 45450567854 passed.

2026-11-24 TV audit: existing catalog kept. The Android SDK manager installed platform 37.2 and Build Tools 36.0.0. Debug and Release resolved the existing dependency graph. Official references include the [AndroidX release table](https://developer.android.com/jetpack/androidx/versions), [AGP compatibility and releases](https://developer.android.com/build/releases/agp-9-5-0-release-notes), [Kotlin release history](https://kotlinlang.org/docs/releases.html), [coroutines 1.11.0](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0), and [SMBJ 0.15.0](https://github.com/hierynomus/smbj/releases/tag/v0.15.0). Validation used the repository's Gradle 9.9.1 and JDK 25. [Gradle 9.9.2](https://docs.gradle.org/9.9.2/release-notes.html) is available upstream. The [Kotlin compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html) lists full compatibility for Kotlin 2.4.21 through Gradle 9.7.0 and AGP 9.3.1. Retain the tested set for this audit pending a build and TV run with a new combination. Local resolution and compilation establish the result for the tested toolchain.

Android TV interaction references: [D-pad and Back](https://developer.android.com/training/tv/get-started/navigation), [Compose focus](https://developer.android.com/develop/ui/compose/touch-input/focus), and [Media3 on TV](https://developer.android.com/media/media3/ui/androidtv). The app targets API 36. [Android 17 LAN permission](https://developer.android.com/privacy-and-security/local-network-permission) applies to apps targeting API 37 or higher. The app uses foreground-only playback without ExoPlayer [wake mode](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer#setWakeMode(int)).
