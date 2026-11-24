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
}
