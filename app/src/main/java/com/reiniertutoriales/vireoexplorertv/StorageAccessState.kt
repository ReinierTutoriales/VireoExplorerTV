package com.reiniertutoriales.vireoexplorertv

internal enum class StorageAccessState {
    UNAVAILABLE, NEEDS_CONSENT, GRANTED, READ_ONLY, REVOKED;

    val canRead: Boolean get() = this == GRANTED || this == READ_ONLY
    val canWrite: Boolean get() = this == GRANTED

    companion object {
        fun fromPermissions(read: Boolean, write: Boolean, previous: StorageAccessState = NEEDS_CONSENT) = when {
            read && write -> GRANTED
            read -> READ_ONLY
            previous.canRead || previous == REVOKED -> REVOKED
            previous == UNAVAILABLE -> UNAVAILABLE
            else -> NEEDS_CONSENT
        }
    }
}
