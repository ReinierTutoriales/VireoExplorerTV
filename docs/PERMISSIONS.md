# Permissions and storage access

Verified against the app manifest and Android platform documentation on 2026-11-25.

| Manifest entry | Platform and purpose |
| --- | --- |
| `INTERNET` | SMB discovery and connections. Local network permission must be migrated with target API 37. The app currently targets API 36. |
| `MANAGE_EXTERNAL_STORAGE` | API 30 and newer. Optional direct access to shared internal storage and mounted USB. Other apps' private directories remain restricted. |
| `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` | APIs 24–29. Legacy runtime permissions for direct shared storage. The manifest caps both at API 29 and requests legacy storage for Android 10. |
| `PACKAGE_USAGE_STATS` | Optional access granted in system settings for app usage and available storage statistics. |
| `REQUEST_INSTALL_PACKAGES` | Allows user-mediated APK installation. On API 26 and newer, Android separately checks whether this app may request installs from unknown sources. |
| `WAKE_LOCK` | Removed from the merged manifest. Foreground playback uses the window's screen-on behavior. |

Package visibility queries are scoped to TV and regular launchers. Android controls launch, uninstall, cache and data management through its system screens.

## Folder grants and direct access

- **Add folder** opens Android's Storage Access Framework directory selector. Persist only the granted read and write flags. Recheck them on resume and before mutations. A provider's flags determine whether a folder supports creation or an entry supports rename, moving, or deletion.
- Canceling the selector returns to the explorer. Some TV firmwares supply a picker stub that reports cancellation, while others lack a handler. The app reports both outcomes and shows an explicit action for direct access on the Files page.
- **Allow access** opens app-specific all-files settings, with the global settings screen as fallback. If neither exists, use an authorized folder or browse SMB. On Android 11 and newer, the picker cannot grant the primary shared-storage root, the `Download` directory or restricted app directories. A folder search keeps the original tree root in its path so valid grants survive resume and process recreation.
- Installing a local APK or opening a local document in another app uses a temporary read-only content URI. Direct-path files are staged in a dedicated private cache subdirectory with a total 1 GiB budget. Expired copies are purged on the next export and failed preparations are removed. Files already backed by SAF retain their original content URI.

The following ADB command is for independent testing on an isolated Android TV emulator or an authorized test device.

```sh
adb shell appops set --uid com.reiniertutoriales.vireoexplorertv MANAGE_EXTERNAL_STORAGE allow
```

Sources: [Android 11 storage changes](https://developer.android.com/about/versions/11/privacy/storage), [all-files access](https://developer.android.com/training/data-storage/manage-all-files), [SAF grants](https://developer.android.com/training/data-storage/shared/documents-files), [secure file sharing](https://developer.android.com/training/secure-file-sharing), [local network permissions](https://developer.android.com/privacy-and-security/local-network-permission).
