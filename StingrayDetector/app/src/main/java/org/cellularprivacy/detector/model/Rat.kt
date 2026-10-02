package org.cellularprivacy.detector.model

/**
 * Radio Access Technology, normalised across the Android telephony zoo.
 *
 * We only care about the family (and its generation) for detection, not every
 * [android.telephony.TelephonyManager] NETWORK_TYPE_* constant.
 */
enum class Rat(val generation: Int) {
    GSM(2),
    WCDMA(3),
    LTE(4),
    NR(5),
    CDMA(2),
    UNKNOWN(0);

    val isTwoG: Boolean get() = this == GSM || this == CDMA

    companion object {
        /** Map a TelephonyManager.NETWORK_TYPE_* value to a [Rat]. */
        fun fromNetworkType(networkType: Int): Rat = when (networkType) {
            1, 2, 16 -> GSM                       // GPRS, EDGE, GSM
            4, 5, 6, 7, 12, 14 -> CDMA            // CDMA/EVDO family
            3, 8, 9, 10, 15, 17 -> WCDMA          // UMTS, HSPA family, TD-SCDMA
            13, 19 -> LTE                         // LTE, LTE_CA
            20 -> NR                              // NR (5G)
            else -> UNKNOWN
        }
    }
}
