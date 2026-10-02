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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.cellularprivacy.detector.detect.DetectorState
import org.cellularprivacy.detector.panic.PanicController
import org.cellularprivacy.detector.settings.AppSettings
import org.cellularprivacy.detector.settings.PanicAction
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
import org.cellularprivacy.detector.collect.LocationProvider
import org.cellularprivacy.detector.data.DetectorDatabase
import org.cellularprivacy.detector.data.ObservedCellEntity
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
    private lateinit var settings: AppSettings
    private lateinit var panic: PanicController
    @Volatile private var settingsCache = AppSettings.Values()
    @Volatile private var lastPanicMs = 0L

    private lateinit var accelerometer: AccelerometerMonitor
    private lateinit var location: LocationProvider

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        collector = CellCollector(this)
        settings = AppSettings(this)
        panic = PanicController(this)
        accelerometer = AccelerometerMonitor(this)
        location = LocationProvider(this)
        accelerometer.start()
        location.start()
        createChannels()
        scope.launch { settings.values.collect { settingsCache = it } }
        DetectorState.setMonitoring(true)
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
                val moving = accelerometer.isMoving()
                val loc = location.last
                val assessment = engine.process(batch, operator, moving)
                val serving = batch.firstOrNull { it.registered } ?: batch.firstOrNull()
                val summary = serving?.let {
                    "${it.rat.name} ${it.mcc ?: "?"}/${it.mnc ?: "?"} CID:${it.cellId ?: "?"} ${it.dbm ?: "?"}dBm"
                } ?: "--"
                DetectorState.update(assessment, summary)

                if (assessment.level >= ThreatLevel.SUSPICIOUS) {
                    notifyThreat(assessment.level, assessment.score)
                }
                if (assessment.level >= ThreatLevel.HIGH && settingsCache.autoProtect) {
                    maybePanic()
                }
                // Persist the serving cell observation, geo-tagged.
                serving?.let { c ->
                    db.observedCellDao().insert(
                        ObservedCellEntity(
                            cellKey = c.key, rat = c.rat.name,
                            mcc = c.mcc, mnc = c.mnc, areaCode = c.areaCode,
                            cellId = c.cellId, physicalId = c.physicalId, arfcn = c.arfcn,
                            dbm = c.dbm, lat = loc?.latitude, lon = loc?.longitude,
                            timestampMs = c.timestampMs
                        )
                    )
                }
                for (r in assessment.contributing) {
                    db.detectionEventDao().insert(
                        DetectionEventEntity(
                            heuristicId = r.id,
                            title = r.title,
                            detail = r.detail,
                            score = r.score,
                            threatLevel = assessment.level.name,
                            cellKey = serving?.key,
                            lat = loc?.latitude,
                            lon = loc?.longitude,
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

    /** Fire the configured protective action, rate-limited to once per minute. */
    private fun maybePanic() {
        val now = System.currentTimeMillis()
        if (now - lastPanicMs < 60_000L) return
        lastPanicMs = now
        if (settingsCache.panicAction != PanicAction.NONE) {
            panic.execute(settingsCache.panicAction)
        }
    }

    override fun onDestroy() {
        DetectorState.setMonitoring(false)
        runCatching { accelerometer.stop() }
        runCatching { location.stop() }
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
