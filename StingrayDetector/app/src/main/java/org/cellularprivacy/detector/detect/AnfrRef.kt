package org.cellularprivacy.detector.detect

/** Minimal official-site reference passed to heuristics (framework-free). */
data class AnfrRef(
    val operators: Set<String>, // ANFR names, e.g. {"ORANGE","SFR"}
    val lat: Double,
    val lon: Double
)
