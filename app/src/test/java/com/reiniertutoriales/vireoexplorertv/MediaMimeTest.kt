package com.reiniertutoriales.vireoexplorertv
import org.junit.Test
import org.junit.Assert.assertEquals
class MediaMimeTest {
    @Test fun routing() {
        assertEquals("video/*", mediaMime("Movie.MKV", "application/octet-stream"))
        assertEquals("audio/*", mediaMime("Track.FLAC"))
        assertEquals("image/*", mediaMime("photo.avif"))
        assertEquals("audio/mp4", mediaMime("track.mp4", "audio/mp4"))
        assertEquals("application/zip", mediaMime("archive.zip", "application/zip"))
    }
    @Test fun inferredMimeTypes() {
        assertEquals("video/x-matroska", inferFileMime("Film.MKV"))
        assertEquals("audio/flac", inferFileMime("Song.flac"))
        assertEquals("image/png", inferFileMime("Screen.PNG"))
        assertEquals("application/zip", inferFileMime("archive.zip"))
        assertEquals("application/vnd.android.package-archive", inferFileMime("app.apk"))
    }
}
