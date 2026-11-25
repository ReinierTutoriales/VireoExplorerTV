# Native permission architecture — 2026-11-24
Official sources reviewed: https://developer.android.com/privacy-and-security/about ; https://developer.android.com/training/data-storage ; https://developer.android.com/training/data-storage/manage-all-files ; https://developer.android.com/privacy-and-security/local-network-permission ; https://developer.android.com/reference/android/Manifest.permission .
- Current SAF tree grants: no broad storage permission; persist only granted read/write flags, verify on resume and before operations, handle missing TV picker, revoked grants and read-only providers. No automatic retry dialogs.
- File-manager broad shared storage: API 30+ MANAGE_EXTERNAL_STORAGE only when direct filesystem backend is implemented and eligibility reviewed; use ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, fallback global settings, Environment.isExternalStorageManager. It does not grant other apps' private directories. API 24–28 legacy permissions only for direct access; scope manifest maxSdk to actual supported use. MediaStore granular media permissions only if needed independently of SAF/all-files; verify API-specific behavior before implementation.
- Package inventory: scoped queries first; QUERY_ALL_PACKAGES only for justified complete inventory and distribution eligibility. Visibility is not authority to delete data.
- Usage access: optional PACKAGE_USAGE_STATS, system settings, AppOps and query exception handling; no privileged grant. App details for cache/data/uninstall. APK installation: REQUEST_INSTALL_PACKAGES declared; checks canRequestPackageInstalls() on API 26+ with explicit consent dialog redirecting to Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES before launching the package installer.
- LAN: INTERNET for real network provider; ACCESS_NETWORK_STATE only if consumed. Official Android 17 guidance: target 37+ broad direct LAN access requires ACCESS_LOCAL_NETWORK at runtime; target <=36 do not declare/request it yet. Prefer NSD system picker when adequate. Multicast lock only during necessary discovery, release in finally. No location permission for generic file browsing.
- No arbitrary accessibility, root, ADB, privileged cache clearing, boot receiver, wake lock or foreground service permissions without implemented documented requirement. Evaluate media playback/background transfer notification and foreground service types only when implementing those lifecycles.
- Central capability state: unavailable, needs consent, granted, read only, denied/revoked; derive from OS not persisted booleans. Contextual request, native system UI, recheck on resume, graceful fallback. Check actual operation permissions again before mutations. Audit merged manifest including transitive dependencies; every permission needs consumer, API range, official URL, tests and distribution justification. Test API 24/28/29/30/33/34/36/37 as relevant, denial/revocation, missing settings and SAF picker, removable volumes, process recreation. No blanket lint exclusions.
Current manifest snapshot (not a full runtime audit):
```xml
<manifest xmlns:tools="http://schemas.android.com/tools" xmlns:android="http://schemas.android.com/apk/res/android">
<uses-permission android:name="android.permission.INTERNET"/>
<!-- Playback is foreground-only and keeps the visible view awake. -->
<uses-permission android:name="android.permission.WAKE_LOCK" tools:node="remove"/>
<uses-feature android:name="android.software.leanback" android:required="true"/>
<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <queries>
        <intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LEANBACK_LAUNCHER"/></intent>
        <intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent>
    </queries>
    <application android:label="VireoExplorerTV" android:icon="@drawable/symbol_folder" android:theme="@style/Theme.VireoExplorerTV" android:allowBackup="false" android:banner="@drawable/banner">
<activity android:name=".MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LEANBACK_LAUNCHER"/></intent-filter></activity>
</application></manifest>
```
No new permissions introduced by this documentation change. Direct filesystem, LAN and APK installation permission flows remain implementation work.
