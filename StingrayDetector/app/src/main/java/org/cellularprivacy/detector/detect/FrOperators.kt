package org.cellularprivacy.detector.detect

/**
 * Maps a French (MCC 208) MNC to the operator name as written in the ANFR data,
 * so a serving cell can be checked against official sites. Returns null outside
 * France or for an unknown MNC (the ANFR cross-check only applies there).
 */
object FrOperators {
    fun operatorForMnc(mcc: Int?, mnc: Int?): String? {
        if (mcc != 208 || mnc == null) return null
        return when (mnc) {
            1, 2, 95 -> "ORANGE"
            9, 10, 11, 13 -> "SFR"
            20, 21, 88 -> "BOUYGUES TELECOM"
            15, 16 -> "FREE MOBILE"
            else -> null
        }
    }
}
