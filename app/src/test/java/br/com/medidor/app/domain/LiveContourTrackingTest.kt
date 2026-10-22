package br.com.medidor.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LiveContourTrackingTest {
    private fun contour(x: Int, y: Int, w: Int=20,h: Int=20) =
        PlanarContourResult(160,120,x,y,x+w-1,y+h-1,w*h,emptyList())

    @Test fun acceptsSimilarObjectPositionFromConsecutiveFrames() {
        val p=contour(60,40)
        val q=contour(65,43)
        assertNotNull(LiveContourTracking.check(p,q).result)
        assertEquals("Contorno aproximado atualizado.",LiveContourTracking.check(p,q).label)
    }
    @Test fun rejectsSuddenRegionJumpOrAreaExplosion() {
        assertNull(LiveContourTracking.check(contour(10,10),contour(105,70)).result)
        assertNull(LiveContourTracking.check(contour(60,40),contour(50,25,60,65)).result)
    }
    @Test fun doesNotInventMeasurementsOrKeepStaleContourWhenLost() {
        assertNull(LiveContourTracking.check(contour(50,50),null).result)
        assertNotNull(LiveContourTracking.check(null,contour(50,50)).result)
    }
}
