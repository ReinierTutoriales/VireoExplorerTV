package com.reiniertutoriales.vireoexplorertv

internal fun zipPath(name: String): List<String> {
    require(name.length in 1..4096 && !name.startsWith('/') && '\\' !in name && ':' !in name)
    val parts = name.removeSuffix("/").split('/')
    require(parts.size <= 32 && parts.all { it.isNotEmpty() && it != "." && it != ".." && it.length <= 255 && it.none { c -> c.isISOControl() } })
    return parts
}
