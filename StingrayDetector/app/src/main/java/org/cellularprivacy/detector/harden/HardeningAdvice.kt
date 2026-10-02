package org.cellularprivacy.detector.harden

import android.content.Intent

/**
 * One actionable recommendation shown on the hardening checklist. [settingsIntent]
 * deep-links into the relevant Settings page when the OS exposes one.
 */
data class HardeningAdvice(
    val id: String,
    val title: String,
    val rationale: String,
    val available: Boolean,
    val settingsIntent: Intent?
)
