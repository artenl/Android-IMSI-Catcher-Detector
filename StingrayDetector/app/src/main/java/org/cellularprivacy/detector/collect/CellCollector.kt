package org.cellularprivacy.detector.collect

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.CellInfo
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.cellularprivacy.detector.model.CellSnapshot

/**
 * Streams [CellSnapshot] batches from the modem using [TelephonyCallback]
 * (API 31+). Each emission is the full set of cells currently visible; the
 * first entry of the serving/registered cells is treated as "serving" by the
 * engine.
 *
 * This replaces the old getNeighboringCellInfo() path (removed in API 29) and
 * the deprecated PhoneStateListener.
 */
class CellCollector(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Cold flow of cell batches. Registers the callback on collection and
     * unregisters it on cancellation. Throws [SecurityException] handling to
     * the caller via an empty flow if permission is missing.
     */
    @SuppressLint("MissingPermission")
    fun cellUpdates(): Flow<List<CellSnapshot>> = callbackFlow {
        if (!hasPermission()) {
            close(SecurityException("ACCESS_FINE_LOCATION not granted"))
            return@callbackFlow
        }

        val callback = object : TelephonyCallback(), TelephonyCallback.CellInfoListener {
            override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                val now = System.currentTimeMillis()
                val snapshots = cellInfo.mapNotNull { CellInfoMapper.map(it, now) }
                if (snapshots.isNotEmpty()) trySend(snapshots)
            }
        }

        val executor = ContextCompat.getMainExecutor(context)
        telephonyManager.registerTelephonyCallback(executor, callback)

        // Nudge the modem for an immediate fresh sample instead of waiting for
        // the next natural change.
        telephonyManager.requestCellInfoUpdate(
            executor,
            object : TelephonyManager.CellInfoCallback() {
                override fun onCellInfo(activeCellInfo: MutableList<CellInfo>) {
                    val now = System.currentTimeMillis()
                    val snapshots = activeCellInfo.mapNotNull { CellInfoMapper.map(it, now) }
                    if (snapshots.isNotEmpty()) trySend(snapshots)
                }
            }
        )

        awaitClose { telephonyManager.unregisterTelephonyCallback(callback) }
    }

    /** Facts about the active SIM, used by the OperatorMismatch heuristic. */
    fun operatorFacts(): OperatorFacts {
        val simOperator = telephonyManager.simOperator // MCC+MNC of the SIM
        val simMcc = simOperator.takeIf { it.length >= 3 }?.substring(0, 3)?.toIntOrNull()
        val simMnc = simOperator.takeIf { it.length >= 5 }?.substring(3)?.toIntOrNull()
        return OperatorFacts(
            simMcc = simMcc,
            simMnc = simMnc,
            isRoaming = telephonyManager.isNetworkRoaming
        )
    }
}
