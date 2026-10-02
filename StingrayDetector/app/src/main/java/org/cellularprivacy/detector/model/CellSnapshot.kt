package org.cellularprivacy.detector.model

/**
 * One observation of a single cell at a point in time, normalised across GSM /
 * WCDMA / LTE / NR. Fields that the radio did not report are null (Android uses
 * [android.telephony.CellInfo.UNAVAILABLE]; the mapper converts that to null).
 *
 * Immutable on purpose: snapshots flow from the collector to the engine and
 * into storage without being mutated, unlike the old mutable Cell god-object.
 */
data class CellSnapshot(
    val rat: Rat,
    val mcc: Int?,
    val mnc: Int?,
    /** LAC (GSM/WCDMA) or TAC (LTE/NR). Unified "area code". */
    val areaCode: Int?,
    /** CID/CI (28-bit) or NCI (36-bit, NR). Kept as Long to hold NR NCI. */
    val cellId: Long?,
    /** Physical Cell Id (LTE/NR) or PSC (WCDMA). */
    val physicalId: Int?,
    /** EARFCN (LTE) / NRARFCN (NR) / ARFCN (GSM) / UARFCN (WCDMA). */
    val arfcn: Int?,
    /** Operating band numbers reported by the identity, if any. */
    val bands: List<Int>,
    /** Downlink channel bandwidth in kHz (LTE only; -1/null if unknown). */
    val bandwidthKhz: Int?,
    /** Primary signal level in dBm (RSRP for LTE, SS-RSRP for NR, RSSI for GSM). */
    val dbm: Int?,
    /** Reference Signal Received Quality, LTE/NR. */
    val rsrq: Int?,
    /** Signal-to-noise, LTE RSSNR / NR SS-SINR. */
    val sinr: Int?,
    /** LTE Timing Advance (255 = unavailable). ~0 means emitter very close. */
    val timingAdvance: Int?,
    val registered: Boolean,
    val timestampMs: Long
) {
    /** A cell is identifiable if we at least have operator + id. */
    val isIdentified: Boolean
        get() = mcc != null && mnc != null && cellId != null

    /** Stable key for grouping observations of "the same cell". */
    val key: String
        get() = "${rat.name}:$mcc:$mnc:$areaCode:$cellId"
}
