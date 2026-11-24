package com.reiniertutoriales.vireoexplorertv

import java.util.Locale

// Routing hints only; actual container and codec support is determined by Media3.
internal fun mediaMime(name: String, reported: String = ""): String {
    if (reported.startsWith("video/") || reported.startsWith("audio/") || reported.startsWith("image/")) return reported
    return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "mkv", "webm", "mp4", "m4v", "mov", "avi", "ts", "m2ts", "mts", "mpg", "mpeg", "flv", "3gp", "vob" -> "video/*"
        "mp3", "flac", "alac", "m4a", "aac", "ogg", "oga", "opus", "wav", "ac3", "eac3", "dts", "amr", "aiff", "aif", "wv" -> "audio/*"
        "jpg", "jpeg", "png", "webp", "avif", "heif", "heic", "gif", "bmp", "tif", "tiff", "jxl" -> "image/*"
        else -> reported
    }
}
