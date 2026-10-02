package org.cellularprivacy.detector.harden

import android.content.Intent

/**
 * One actionable recommendation shown on the hardening checklist.
 * [settingsIntent] deep-links into the nearest relevant Settings page the OS
 * exposes (there is no public intent for the exact toggle), and [steps] gives
 * the manual path, since the exact location varies by manufacturer.
 */
data class HardeningAdvice(
    val id: String,
    val title: String,
    val rationale: String,
    val available: Boolean,
    val settingsIntent: Intent?,
    val steps: String? = null
)
