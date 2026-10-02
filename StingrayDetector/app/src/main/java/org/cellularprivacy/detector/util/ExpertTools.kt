package org.cellularprivacy.detector.util

import android.content.Context
import android.content.Intent

/**
 * Advanced, device-level screens for the Expert mode. These are stock Android
 * engineering screens; they are powerful but also display sensitive identifiers,
 * so the UI gates them behind a warning.
 */
object ExpertTools {

    /**
     * The stock "Phone info" (RadioInfo) engineering screen. It shows live radio
     * registration, bands, NR state and the "Set Preferred Network Type"
     * control, but also IMEI / IMSI / phone number. Not all devices allow a
     * third-party launch, so callers wrap this in runCatching.
     */
    fun radioInfoIntent(): Intent =
        Intent().setClassName("com.android.phone", "com.android.phone.settings.RadioInfo")

    fun canOpenRadioInfo(context: Context): Boolean =
        radioInfoIntent().resolveActivity(context.packageManager) != null
}
