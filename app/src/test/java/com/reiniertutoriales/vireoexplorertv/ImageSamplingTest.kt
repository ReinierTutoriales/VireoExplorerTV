package com.reiniertutoriales.vireoexplorertv
import androidx.exifinterface.media.ExifInterface
import org.junit.Test
import org.junit.Assert.*
class ImageSamplingTest {
    @Test fun fitsViewport() {
        assertEquals(1, sampleSize(1920,1080,3840,2160))
        assertEquals(4, sampleSize(8000,6000,3840,2160))
        assertEquals(8, sampleSize(1000,16000,3840,2160))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDimensions() { sampleSize(-1,100,1920,1080) }

    @Test fun exifOrientationMapping() {
        assertEquals(0f to false, exifTransformDegreesAndFlip(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(90f to false, exifTransformDegreesAndFlip(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(180f to false, exifTransformDegreesAndFlip(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270f to false, exifTransformDegreesAndFlip(ExifInterface.ORIENTATION_ROTATE_270))
        assertEquals(0f to true, exifTransformDegreesAndFlip(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
        assertEquals(90f to true, exifTransformDegreesAndFlip(ExifInterface.ORIENTATION_TRANSPOSE))
    }

    @Test fun boundedImageStreamReadsAndEnforcesCap() {
        val sample = ByteArray(4096) { (it % 251).toByte() }
        assertArrayEquals(sample, readBoundedImageBytes(sample.inputStream(), maxBytes = 4096))
        assertTrue(runCatching { readBoundedImageBytes(sample.inputStream(), maxBytes = 2048) }.isFailure)
        assertTrue(runCatching { readBoundedImageBytes(ByteArray(0).inputStream(), maxBytes = 2048) }.isFailure)
    }

    @Test fun boundedImageStreamReadsAtMostOneByteBeyondTheLimit() {
        val input = ByteArray(32).inputStream()
        assertTrue(runCatching { readBoundedImageBytes(input, maxBytes = 8) }.isFailure)
        assertEquals(23, input.available())
    }

    @Test fun boundedImageStreamChecksCancellationBeforeReading() {
        val input = ByteArray(8).inputStream()
        val cancelled = java.util.concurrent.CancellationException()
        val failure = runCatching {
            readBoundedImageBytes(input, maxBytes = 8) { throw cancelled }
        }.exceptionOrNull()
        assertSame(cancelled, failure)
        assertEquals(8, input.available())
    }

    @Test fun clampPanOffsetRespectsZoomBounds() {
        assertEquals(0f, clampPanOffset(100f, 50f, 1920, 1f), 0.01f)
        assertEquals(480f, clampPanOffset(400f, 200f, 1920, 1.5f), 0.01f)
        assertEquals(-480f, clampPanOffset(-400f, -200f, 1920, 1.5f), 0.01f)
    }
}
