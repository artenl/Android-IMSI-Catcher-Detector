package org.cellularprivacy.detector.detect

import org.cellularprivacy.detector.collect.OperatorFacts
import org.cellularprivacy.detector.model.CellSnapshot
import org.cellularprivacy.detector.model.Rat
import org.cellularprivacy.detector.model.ThreatLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the detection graph. No Android framework involved: the
 * model and detect packages are deliberately framework-free so heuristics can
 * be driven with synthetic snapshots.
 */
class DetectionEngineTest {

    private val frenchSim = OperatorFacts(simMcc = 208, simMnc = 1, isRoaming = false)

    private fun lte(
        dbm: Int, cellId: Long = 1000, ts: Long, bandwidthKhz: Int? = 20000,
        ta: Int? = 50, mcc: Int? = 208, mnc: Int? = 1, registered: Boolean = true
    ) = CellSnapshot(
        rat = Rat.LTE, mcc = mcc, mnc = mnc, areaCode = 500, cellId = cellId,
        physicalId = 10, arfcn = 1800, bands = listOf(3), bandwidthKhz = bandwidthKhz,
        dbm = dbm, rsrq = -10, sinr = 10, timingAdvance = ta, registered = registered,
        timestampMs = ts
    )

    private fun gsm(dbm: Int, ts: Long, mcc: Int? = 208, mnc: Int? = 1) = CellSnapshot(
        rat = Rat.GSM, mcc = mcc, mnc = mnc, areaCode = 500, cellId = 777,
        physicalId = null, arfcn = 60, bands = emptyList(), bandwidthKhz = null,
        dbm = dbm, rsrq = null, sinr = null, timingAdvance = null,
        registered = true, timestampMs = ts
    )

    /** A non-serving neighbour cell, so the empty-neighbour heuristic stays quiet. */
    private fun neighbor(ts: Long) = CellSnapshot(
        rat = Rat.LTE, mcc = 208, mnc = 1, areaCode = 500, cellId = 2000,
        physicalId = 20, arfcn = 1800, bands = listOf(3), bandwidthKhz = 20000,
        dbm = -100, rsrq = -12, sinr = 5, timingAdvance = 60, registered = false,
        timestampMs = ts
    )

    @Test
    fun `quiet LTE network with neighbours stays normal`() {
        val engine = DetectionEngine()
        engine.process(listOf(lte(dbm = -95, ts = 0), neighbor(0)), frenchSim, false)
        engine.process(listOf(lte(dbm = -96, ts = 1000), neighbor(1000)), frenchSim, false)
        val a = engine.process(listOf(lte(dbm = -95, ts = 2000), neighbor(2000)), frenchSim, false)
        assertEquals(ThreatLevel.NORMAL, a.level)
    }

    @Test
    fun `2G downgrade after healthy LTE in France is flagged`() {
        val engine = DetectionEngine()
        // Healthy LTE first.
        engine.process(listOf(lte(dbm = -85, ts = 0)), frenchSim, deviceMoving = false)
        // Then a sudden drop to GSM while stationary.
        val a = engine.process(listOf(gsm(dbm = -80, ts = 5000)), frenchSim, deviceMoving = false)

        assertTrue("downgrade heuristic should contribute",
            a.contributing.any { it.id == "rat_downgrade" })
        assertTrue("level should be at least suspicious", a.level >= ThreatLevel.SUSPICIOUS)
    }

    @Test
    fun `downgrade while moving is not flagged`() {
        val engine = DetectionEngine()
        engine.process(listOf(lte(dbm = -85, ts = 0)), frenchSim, deviceMoving = true)
        val a = engine.process(listOf(gsm(dbm = -80, ts = 5000)), frenchSim, deviceMoving = true)
        assertTrue(a.contributing.none { it.id == "rat_downgrade" })
    }

    @Test
    fun `operator mismatch without roaming is flagged`() {
        val engine = DetectionEngine()
        val a = engine.process(
            listOf(lte(dbm = -90, ts = 0, mcc = 310, mnc = 260)), // US T-Mobile on a French SIM
            frenchSim, deviceMoving = false
        )
        assertTrue(a.contributing.any { it.id == "operator_mismatch" })
    }

    @Test
    fun `narrow bandwidth lone cell triggers physical and neighbor heuristics`() {
        val engine = DetectionEngine()
        val a = engine.process(
            listOf(lte(dbm = -60, ts = 0, bandwidthKhz = 1400, ta = 0)),
            frenchSim, deviceMoving = false
        )
        assertTrue(a.contributing.any { it.id == "suspicious_physical" })
        assertTrue(a.contributing.any { it.id == "empty_neighbor_list" })
    }

    @Test
    fun `score decays to normal after the condition stops`() {
        val aggregator = ScoreAggregator(decayMs = 10_000L)
        val engine = DetectionEngine(aggregator = aggregator)
        engine.process(listOf(lte(dbm = -85, ts = 0)), frenchSim, false)
        val hot = engine.process(listOf(gsm(dbm = -80, ts = 1000)), frenchSim, false)
        assertTrue(hot.level >= ThreatLevel.SUSPICIOUS)

        // Long after, a benign LTE batch with neighbours: decayed entries gone.
        val t = 1000 + 20_000L
        val cooled = engine.process(listOf(lte(dbm = -90, ts = t), neighbor(t)), frenchSim, false)
        assertEquals(ThreatLevel.NORMAL, cooled.level)
    }
}
