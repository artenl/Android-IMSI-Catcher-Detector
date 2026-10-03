package org.cellularprivacy.detector.detect

import org.cellularprivacy.detector.collect.OperatorFacts
import org.cellularprivacy.detector.model.CellSnapshot
import org.cellularprivacy.detector.model.Rat
import org.junit.Assert.assertTrue
import org.junit.Test

class AnfrMismatchTest {

    private val sim = OperatorFacts(simMcc = 208, simMnc = 1, isRoaming = false)

    private fun lte(mnc: Int) = CellSnapshot(
        rat = Rat.LTE, mcc = 208, mnc = mnc, areaCode = 1, cellId = 42,
        physicalId = 1, arfcn = 1800, bands = listOf(3), bandwidthKhz = 20000,
        dbm = -90, rsrq = -10, sinr = 10, timingAdvance = 50, registered = true,
        timestampMs = 0
    )

    // A Paris ANFR site for ORANGE, ~100 m from the user.
    private val orangeSite = AnfrRef(setOf("ORANGE"), 48.8566, 2.3522)

    @Test fun `orange cell with orange site nearby is fine`() {
        val engine = DetectionEngine()
        val a = engine.process(
            listOf(lte(mnc = 1)), sim, deviceMoving = false,
            userLat = 48.8566, userLon = 2.3523, anfr = listOf(orangeSite)
        )
        assertTrue(a.contributing.none { it.id == "anfr_mismatch" })
    }

    @Test fun `free cell with only orange sites nearby is flagged`() {
        val engine = DetectionEngine()
        val a = engine.process(
            listOf(lte(mnc = 15)), sim, deviceMoving = false, // 15 = FREE MOBILE
            userLat = 48.8566, userLon = 2.3523, anfr = listOf(orangeSite)
        )
        assertTrue(a.contributing.any { it.id == "anfr_mismatch" })
    }

    @Test fun `no data pack means no judgement`() {
        val engine = DetectionEngine()
        val a = engine.process(
            listOf(lte(mnc = 15)), sim, deviceMoving = false,
            userLat = 48.8566, userLon = 2.3523, anfr = emptyList()
        )
        assertTrue(a.contributing.none { it.id == "anfr_mismatch" })
    }

    @Test fun `area not covered (sites far away) means no judgement`() {
        val engine = DetectionEngine()
        val a = engine.process(
            listOf(lte(mnc = 15)), sim, deviceMoving = false,
            userLat = 43.6, userLon = 1.44, // Toulouse; the only site is in Paris
            anfr = listOf(orangeSite)
        )
        assertTrue(a.contributing.none { it.id == "anfr_mismatch" })
    }
}
