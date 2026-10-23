package br.com.medidor.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveObjectCounterTest {
    @Test fun separateDarkSquaresOnBrightBackground() {
        val w = 80; val h = 60
        val pixels = IntArray(w*h) { 0xFFFFFFFF.toInt() }
        for (y in 12..22) for (x in 12..22) pixels[y*w+x] = 0xFF000000.toInt()
        for (y in 32..43) for (x in 48..60) pixels[y*w+x] = 0xFF000000.toInt()
        val result = LiveObjectCounter.detect(pixels,w,h)
        assertEquals(2,result.size)
    }

    @Test fun homogeneousFrameCountsNothing() {
        val pixels = IntArray(80*60) { 0xFFAAAAAA.toInt() }
        assertTrue(LiveObjectCounter.detect(pixels,80,60).isEmpty())
    }

    @Test fun objectsTouchingBorderAreExcluded() {
        val w = 80; val h = 60
        val pixels = IntArray(w*h) { 0xFFFFFFFF.toInt() }
        for (y in 16..27) for (x in 0..10) pixels[y*w+x] = 0xFF000000.toInt()
        assertEquals(0,LiveObjectCounter.detect(pixels,w,h).size)
    }
}
