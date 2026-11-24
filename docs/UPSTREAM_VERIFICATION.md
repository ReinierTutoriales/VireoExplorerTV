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
