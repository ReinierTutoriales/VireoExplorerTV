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

## Official implementation first — mandatory execution gate
Before writing or refactoring any feature, inspect the existing consumers and locate the current official Android/AndroidX API, maintained sample and release metadata. Prefer adopting the complete supported component or documented pattern over hand-written equivalents. Verify actual stable artifact availability, API/target requirements, TV suitability and licensing; record URLs, date and compatibility evidence. Do not assume a newer library is appropriate merely because it exists.
- Architecture: use ViewModel, lifecycle-aware Flow collection, SavedStateHandle and official navigation patterns where justified; avoid custom lifecycle, state serialization and navigation frameworks. Evaluate Navigation 3 against TV focus and supported versions before adoption.
- TV: Compose for TV, TV Material, official focus and lazy layout APIs; avoid custom widgets or key interception unless a demonstrated gap requires them.
- Storage/media/security: use SAF, DocumentsContract, StorageManager, ContentResolver, Media3, ImageDecoder, Keystore and official permission contracts. Use maintained protocol libraries only for missing platform capabilities.
- Deliver coherent vertical slices in the fewest safe passes: inspect, select official implementation, integrate, remove superseded code, test and verify actual CI. No repetitive micro-commits or documentation-only substitutes for requested implementation. Check every edited call site and API boundary before committing. Do not use blind text substitutions.
- Custom code requires a concrete unsupported requirement and focused regression tests. Prefer official samples as references, not wholesale imports; preserve licenses. No invented APIs, unverified version claims, unnecessary dependencies or performance claims without measurements.
- Report actual evidence and blockers, not planned work as completed. Never bypass lint or weaken permissions to make CI green.
