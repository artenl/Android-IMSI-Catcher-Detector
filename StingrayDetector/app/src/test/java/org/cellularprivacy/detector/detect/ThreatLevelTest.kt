package org.cellularprivacy.detector.detect

import org.cellularprivacy.detector.model.ThreatLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreatLevelTest {
    @Test fun `score mapping`() {
        assertEquals(ThreatLevel.NORMAL, ThreatLevel.fromScore(0))
        assertEquals(ThreatLevel.INFO, ThreatLevel.fromScore(10))
        assertEquals(ThreatLevel.SUSPICIOUS, ThreatLevel.fromScore(50))
        assertEquals(ThreatLevel.HIGH, ThreatLevel.fromScore(90))
    }

    @Test fun `levels are ordered`() {
        assertTrue(ThreatLevel.HIGH > ThreatLevel.SUSPICIOUS)
        assertTrue(ThreatLevel.SUSPICIOUS > ThreatLevel.INFO)
    }
}
