package org.cellularprivacy.detector.detect

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.cellularprivacy.detector.model.ThreatLevel

/**
 * Process-wide live detection state, so the UI can observe what the
 * [org.cellularprivacy.detector.service.MonitoringService] is seeing in real
 * time without binding to the service. A single source of truth, updated on
 * each batch.
 */
object DetectorState {

    data class Live(
        val monitoring: Boolean = false,
        val level: ThreatLevel = ThreatLevel.NORMAL,
        val score: Int = 0,
        val servingSummary: String = "--",
        val lastUpdateMs: Long = 0L
    )

    private val _state = MutableStateFlow(Live())
    val state: StateFlow<Live> = _state.asStateFlow()

    fun setMonitoring(on: Boolean) {
        _state.value = _state.value.copy(monitoring = on)
    }

    fun update(assessment: Assessment, servingSummary: String) {
        _state.value = _state.value.copy(
            level = assessment.level,
            score = assessment.score,
            servingSummary = servingSummary,
            lastUpdateMs = System.currentTimeMillis()
        )
    }
}
