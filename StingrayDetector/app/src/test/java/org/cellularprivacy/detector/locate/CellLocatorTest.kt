package org.cellularprivacy.detector.locate

import org.cellularprivacy.detector.util.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellLocatorTest {

    @Test fun `no observations returns null`() {
        assertNull(CellLocator.estimate(emptyList()))
    }

    @Test fun `single observation returns that point with floor accuracy`() {
        val e = CellLocator.estimate(listOf(Observation(48.8566, 2.3522, -90)))!!
        assertEquals(48.8566, e.lat, 1e-6)
        assertEquals(2.3522, e.lon, 1e-6)
        assertTrue(e.accuracyMeters >= 50.0)
        assertEquals(1, e.samples)
    }

    @Test fun `estimate is pulled toward the stronger-signal samples`() {
        // Two points ~1.5 km apart in Paris; strong signal near the east point.
        val west = Observation(48.8566, 2.3400, -110)   // weak
        val east = Observation(48.8566, 2.3600, -70)    // strong
        val e = CellLocator.estimate(listOf(west, east))!!
        // Weighted centroid must sit much closer to the strong (east) sample.
        val dEast = Geo.haversine(e.lat, e.lon, east.lat, east.lon)
        val dWest = Geo.haversine(e.lat, e.lon, west.lat, west.lon)
        assertTrue("estimate should be nearer the strong sample", dEast < dWest)
    }

    @Test fun `haversine matches known Paris-London distance roughly`() {
        // Paris (48.8566,2.3522) to London (51.5074,-0.1278) ~ 343 km.
        val d = Geo.haversine(48.8566, 2.3522, 51.5074, -0.1278)
        assertEquals(343_000.0, d, 10_000.0)
    }

    @Test fun `bearing north and east are correct`() {
        assertEquals(0.0, Geo.bearing(0.0, 0.0, 1.0, 0.0), 1.0)   // due north
        assertEquals(90.0, Geo.bearing(0.0, 0.0, 0.0, 1.0), 1.0)  // due east
    }
}
