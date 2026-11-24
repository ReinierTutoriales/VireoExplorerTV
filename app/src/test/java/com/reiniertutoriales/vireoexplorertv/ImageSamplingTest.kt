package com.reiniertutoriales.vireoexplorertv
import org.junit.Test
import org.junit.Assert.assertEquals
class ImageSamplingTest {
    @Test fun fitsViewport() {
        assertEquals(1, sampleSize(1920,1080,3840,2160))
        assertEquals(4, sampleSize(8000,6000,3840,2160))
        assertEquals(8, sampleSize(1000,16000,3840,2160))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDimensions() { sampleSize(-1,100,1920,1080) }
}
