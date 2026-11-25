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

internal fun inferFileMime(name: String): String =
    when (val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "ts", "m2ts", "mts" -> "video/mp2t"
        "mpg", "mpeg", "vob" -> "video/mpeg"
        "flv" -> "video/x-flv"
        "3gp" -> "video/3gpp"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a", "aac", "alac" -> "audio/mp4"
        "ogg", "oga", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "ac3" -> "audio/ac3"
        "eac3" -> "audio/eac3"
        "amr" -> "audio/amr"
        "aiff", "aif" -> "audio/x-aiff"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "avif" -> "image/avif"
        "heic", "heif" -> "image/heic"
        "tif", "tiff" -> "image/tiff"
        "zip" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        "pdf" -> "application/pdf"
        "txt", "md", "log", "srt", "vtt", "ass", "ssa" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "xml", "ttml", "dfxp" -> "application/xml"
        else -> {
            val guessed = java.net.URLConnection.guessContentTypeFromName(name)
            if (!guessed.isNullOrBlank()) guessed
            else if (ext.isNotEmpty()) "application/octet-stream"
            else "application/octet-stream"
        }
    }

