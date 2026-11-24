package com.reiniertutoriales.vireoexplorertv

internal enum class ConflictPolicy(val label: String) {
    STOP("Conflict: stop"), SKIP("Conflict: skip"), KEEP_BOTH("Conflict: keep both")
}

internal fun resolveConflict(name: String, directory: Boolean, existing: List<String>, policy: ConflictPolicy): String? {
    val occupied = existing.map { it.lowercase(java.util.Locale.ROOT) }.toHashSet()
    fun free(value: String) = value.lowercase(java.util.Locale.ROOT) !in occupied
    if (free(name)) return name
    if (policy == ConflictPolicy.SKIP) return null
    check(policy == ConflictPolicy.KEEP_BOTH) { "Destination already exists: $name" }
    val dot = if (directory) -1 else name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    val extension = if (dot > 0) name.substring(dot) else ""
    for (i in 1..10000) {
        val candidate = "$stem ($i)$extension"
        if (free(candidate)) return candidate
    }
    error("No available alternative name")
}
