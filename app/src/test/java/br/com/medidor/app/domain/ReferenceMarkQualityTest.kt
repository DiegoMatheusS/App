package br.com.medidor.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceMarkQualityTest {
    @Test fun rejectsShortAndInvalidReferences() {
        assertFalse(ReferenceMarkQuality.isReadable(0.0))
        assertFalse(ReferenceMarkQuality.isReadable(19.99))
        assertFalse(ReferenceMarkQuality.isReadable(Double.NaN))
        assertFalse(ReferenceMarkQuality.isReadable(Double.POSITIVE_INFINITY))
        assertFalse(ReferenceMarkQuality.isReadable(-4.0))
    }

    @Test fun acceptsReferencesFromTwentyPixels() {
        assertTrue(ReferenceMarkQuality.isReadable(20.0))
        assertTrue(ReferenceMarkQuality.isReadable(100.0))
    }
}
