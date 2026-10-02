package org.cellularprivacy.detector.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.cellularprivacy.detector.R
import org.cellularprivacy.detector.collect.CellCollector
import org.cellularprivacy.detector.data.DetectionEventEntity
import org.cellularprivacy.detector.data.DetectorDatabase
import org.cellularprivacy.detector.detect.DetectionEngine
import org.cellularprivacy.detector.model.ThreatLevel

/**
 * Foreground service (type "location") that runs the collector + engine while
 * the app is monitoring. Foreground is mandatory: a plain background service is
 * killed on Android 8+, which is exactly why the old AimsicdService stopped
 * working on modern devices.
 */
class MonitoringService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var collector: CellCollector
    private val engine = DetectionEngine()

    // Wired to the accelerometer in a later slice; false = treat as stationary.
    @Volatile private var deviceMoving = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        collector = CellCollector(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        val db = DetectorDatabase.get(this)
        val operator = collector.operatorFacts()

        collector.cellUpdates()
            .catch { /* permission revoked or modem error: stop quietly */ }
            .onEach { batch ->
                val assessment = engine.process(batch, operator, deviceMoving)
                if (assessment.level >= ThreatLevel.SUSPICIOUS) {
                    notifyThreat(assessment.level, assessment.score)
                }
                val serving = batch.firstOrNull { it.registered }
                for (r in assessment.contributing) {
                    db.detectionEventDao().insert(
                        DetectionEventEntity(
                            heuristicId = r.id,
                            title = r.title,
                            detail = r.detail,
                            score = r.score,
                            threatLevel = assessment.level.name,
                            cellKey = serving?.key,
                            lat = null,
                            lon = null,
                            timestampMs = r.timestampMs
                        )
                    )
                }
            }
            .launchIn(scope)
    }

    private fun startForegroundCompat() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setContentTitle(getString(R.string.monitoring_active))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID_MONITOR, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIF_ID_MONITOR, notification)
        }
    }

    private fun notifyThreat(level: ThreatLevel, score: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setContentTitle("Alerte: $level (score $score)")
            .setContentText("Possible cellule factice détectée.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(NOTIF_ID_ALERT, notification)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITOR, getString(R.string.monitoring_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT, getString(R.string.alert_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_MONITOR = "monitoring"
        private const val CHANNEL_ALERT = "alerts"
        private const val NOTIF_ID_MONITOR = 1
        private const val NOTIF_ID_ALERT = 2

        fun start(context: Context) {
            val intent = Intent(context, MonitoringService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }
    }
}
