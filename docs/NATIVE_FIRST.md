# Native-first engineering standard

Required implementation order: Android framework → AndroidX/Jetpack → maintained external library for a proven gap → custom code only with documented necessity.

| Subsystem | Preferred official implementation |
|---|---|
| TV UI | Compose for TV, TV Material, native focus and accessibility |
| State and lifecycle | ViewModel, SavedStateHandle, lifecycle-aware Flow |
| Storage | StorageManager, SAF, DocumentsContract, MediaStore, File API where authorized |
| Permissions | Activity Result APIs and system settings, contextual grants |
| Media | Media3, MediaCodec, AudioTrack, platform capabilities |
| Images | ImageDecoder, BitmapFactory, bounded decoding |
| Credentials | Android Keystore, JCA AES-GCM |
| Discovery | NsdManager, API-specific callback lifecycle |
| Background work | WorkManager only for appropriate persistent work |
| Preferences | DataStore where justified |
| Archives | java.util.zip for supported ZIP operations, audited external formats only as needed |
| Network protocols | Native discovery and sockets where appropriate; maintained protocol clients for unsupported protocols |

Official reference entry points (verify current contents before implementation):
- https://developer.android.com/training/tv
- https://developer.android.com/develop/ui/compose
- https://developer.android.com/jetpack/androidx/versions
- https://developer.android.com/training/data-storage
- https://developer.android.com/media/media3
- https://developer.android.com/privacy-and-security/keystore
- https://developer.android.com/develop/connectivity/wifi/use-nsd
- https://developer.android.com/topic/libraries/architecture

Audit priorities: remove redundant SAF network browser, unify streaming integrity, review permission grants and lifecycle, replace temporary platform dialogs where TV Material provides suitable components, migrate deprecated NSD only where API support permits, review Media3 surface and subtitle IO. Do not mark these migrations complete until code and checks exist.
