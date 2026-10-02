package org.cellularprivacy.detector.collect

import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellIdentityNr
import android.telephony.CellSignalStrengthNr
import org.cellularprivacy.detector.model.CellSnapshot
import org.cellularprivacy.detector.model.Rat

/**
 * Converts the heterogeneous [CellInfo] hierarchy into our flat [CellSnapshot].
 * All "unavailable" sentinels ([CellInfo.UNAVAILABLE] and its long variant) are
 * normalised to null so downstream code never has to know about them.
 */
object CellInfoMapper {

    private fun Int.orNull(): Int? = if (this == CellInfo.UNAVAILABLE) null else this
    private fun Long.orNull(): Long? = if (this == CellInfo.UNAVAILABLE_LONG) null else this
    private fun String?.toIntOrNull(): Int? = this?.toIntOrNull()

    fun map(info: CellInfo, timestampMs: Long): CellSnapshot? = when (info) {
        is CellInfoLte -> mapLte(info, timestampMs)
        is CellInfoNr -> mapNr(info, timestampMs)
        is CellInfoWcdma -> mapWcdma(info, timestampMs)
        is CellInfoGsm -> mapGsm(info, timestampMs)
        else -> null
    }

    private fun mapLte(info: CellInfoLte, ts: Long): CellSnapshot {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        return CellSnapshot(
            rat = Rat.LTE,
            mcc = id.mccString.toIntOrNull(),
            mnc = id.mncString.toIntOrNull(),
            areaCode = id.tac.orNull(),
            cellId = id.ci.orNull()?.toLong(),
            physicalId = id.pci.orNull(),
            arfcn = id.earfcn.orNull(),
            bands = id.bands.toList(),
            bandwidthKhz = id.bandwidth.orNull(),
            dbm = ss.rsrp.orNull(),
            rsrq = ss.rsrq.orNull(),
            sinr = ss.rssnr.orNull(),
            timingAdvance = ss.timingAdvance.orNull(),
            registered = info.isRegistered,
            timestampMs = ts
        )
    }

    private fun mapNr(info: CellInfoNr, ts: Long): CellSnapshot {
        val id = info.cellIdentity as CellIdentityNr
        val ss = info.cellSignalStrength as CellSignalStrengthNr
        return CellSnapshot(
            rat = Rat.NR,
            mcc = id.mccString.toIntOrNull(),
            mnc = id.mncString.toIntOrNull(),
            areaCode = id.tac.orNull(),
            cellId = id.nci.orNull(),
            physicalId = id.pci.orNull(),
            arfcn = id.nrarfcn.orNull(),
            bands = id.bands.toList(),
            bandwidthKhz = null,
            dbm = ss.ssRsrp.orNull(),
            rsrq = ss.ssRsrq.orNull(),
            sinr = ss.ssSinr.orNull(),
            timingAdvance = null,
            registered = info.isRegistered,
            timestampMs = ts
        )
    }

    private fun mapWcdma(info: CellInfoWcdma, ts: Long): CellSnapshot {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        return CellSnapshot(
            rat = Rat.WCDMA,
            mcc = id.mccString.toIntOrNull(),
            mnc = id.mncString.toIntOrNull(),
            areaCode = id.lac.orNull(),
            cellId = id.cid.orNull()?.toLong(),
            physicalId = id.psc.orNull(),
            arfcn = id.uarfcn.orNull(),
            bands = emptyList(),
            bandwidthKhz = null,
            dbm = ss.dbm.orNull(),
            rsrq = null,
            sinr = null,
            timingAdvance = null,
            registered = info.isRegistered,
            timestampMs = ts
        )
    }

    private fun mapGsm(info: CellInfoGsm, ts: Long): CellSnapshot {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        return CellSnapshot(
            rat = Rat.GSM,
            mcc = id.mccString.toIntOrNull(),
            mnc = id.mncString.toIntOrNull(),
            areaCode = id.lac.orNull(),
            cellId = id.cid.orNull()?.toLong(),
            physicalId = null,
            arfcn = id.arfcn.orNull(),
            bands = emptyList(),
            bandwidthKhz = null,
            dbm = ss.dbm.orNull(),
            rsrq = null,
            sinr = null,
            timingAdvance = ss.timingAdvance.orNull(),
            registered = info.isRegistered,
            timestampMs = ts
        )
    }
}
