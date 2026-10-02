package org.cellularprivacy.detector.panic

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import org.cellularprivacy.detector.settings.PanicAction

/**
 * Executes the protective response when the threat level reaches HIGH.
 *
 * Honesty about limits: a normally-installed app cannot power off the phone or
 * force airplane mode. The strongest actions it can take unaided are:
 *   - lock the screen now (Device Admin),
 *   - raise a loud full-screen alert + vibration,
 *   - fire a PanicKit TRIGGER broadcast for installed panic responders.
 * Real radio cut / power-off is only possible with root, attempted on a
 * best-effort basis in [PanicAction.PANIC_FULL]; otherwise we fall back to the
 * above and send the user to airplane-mode settings.
 */
class PanicController(private val context: Context) {

    private val dpm =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, LockAdminReceiver::class.java)

    fun isDeviceAdminActive(): Boolean = dpm.isAdminActive(admin)

    /** Intent the UI launches so the user can grant Device Admin. */
    fun deviceAdminRequestIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Permet au mode panic de verrouiller l'écran immédiatement."
            )
        }

    /** Run the configured action. [PanicAction.NONE]/[NOTIFY] handled by caller. */
    fun execute(action: PanicAction) {
        when (action) {
            PanicAction.NONE, PanicAction.NOTIFY -> alert()
            PanicAction.LOCK -> { alert(); lockNow() }
            PanicAction.PANIC_FULL -> { alert(); triggerPanicKit(); if (!tryRootCutoff()) lockNow() }
        }
    }

    private fun lockNow() {
        if (isDeviceAdminActive()) runCatching { dpm.lockNow() }
    }

    /** Loud alarm tone + strong vibration pattern. */
    private fun alert() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM).build()
                play()
            }
        }
        runCatching {
            val pattern = longArrayOf(0, 400, 200, 400, 200, 800)
            vibrator()?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** PanicKit (Guardian Project) trigger for any installed responder app. */
    private fun triggerPanicKit() {
        runCatching {
            context.sendBroadcast(Intent("info.guardianproject.panic.action.TRIGGER"))
        }
    }

    /**
     * Best-effort radio cut-off / power-off, root only. Returns true if a root
     * command was accepted. We prefer cutting the radio (airplane mode) over a
     * full power-off, then power-off as the last resort if requested.
     */
    private fun tryRootCutoff(): Boolean = runCatching {
        val cmds = arrayOf(
            "settings put global airplane_mode_on 1",
            "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true",
            "svc wifi disable",
            "svc data disable"
        )
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmds.joinToString(" ; ")))
        p.waitFor() == 0
    }.getOrDefault(false)

    /** Deep-link the user to airplane-mode settings when we cannot force it. */
    fun airplaneSettingsIntent(): Intent =
        Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
