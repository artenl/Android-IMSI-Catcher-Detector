package org.cellularprivacy.detector.harden

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager

/**
 * Tier 1b: the single most effective, fully legal defence is to switch on the
 * protections the OS already ships. A third-party app cannot read or toggle
 * these states, but it can detect the Android version, explain each one, and
 * deep-link the user straight into the right Settings page.
 *
 * Coverage:
 *  - Disable 2G per SIM            (Android 12+)
 *  - Require encryption / no null cipher   (Android 14+, modem dependent)
 *  - Identifier-disclosure + null-cipher notifications (Android 16+, IRadio 3.0)
 *
 * France note: with 2G being switched off by all operators, disabling 2G has
 * almost no downside and removes the main downgrade attack surface. The
 * rationale is adapted when the SIM is French (MCC 208).
 */
class HardeningAdvisor(private val context: Context) {

    private val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private fun isFrenchSim(): Boolean =
        tm.simOperator.take(3).toIntOrNull() == 208

    fun buildAdvice(): List<HardeningAdvice> {
        val sdk = Build.VERSION.SDK_INT
        val list = mutableListOf<HardeningAdvice>()

        val twoGRationale = if (isFrenchSim()) {
            "La 2G est en cours d'extinction en France (Orange/SFR fin 2026). " +
                "La désactiver supprime la principale voie de rétrogradation d'un " +
                "IMSI-catcher, quasiment sans inconvénient."
        } else {
            "Désactiver la 2G empêche la rétrogradation forcée vers un réseau non " +
                "chiffré, technique de base des IMSI-catchers."
        }
        list += HardeningAdvice(
            id = "disable_2g",
            title = "Désactiver la 2G",
            rationale = twoGRationale,
            available = sdk >= Build.VERSION_CODES.S, // Android 12
            settingsIntent = radioInfoOrWireless()
        )

        list += HardeningAdvice(
            id = "require_encryption",
            title = "Exiger le chiffrement (refuser le chiffrement nul)",
            rationale = "Empêche la connexion aux cellules qui désactivent le " +
                "chiffrement. Dépend du modem.",
            available = sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE, // Android 14
            settingsIntent = securitySettings()
        )

        list += HardeningAdvice(
            id = "network_notifications",
            title = "Alertes réseau (connexion non chiffrée, divulgation IMSI/IMEI)",
            rationale = "Android 16 affiche une notification quand le téléphone se " +
                "connecte sans chiffrement ou communique son IMSI/IMEI. Nécessite un " +
                "modem IRadio 3.0 (Pixel récents et quelques flagships).",
            available = sdk >= 36, // Android 16
            settingsIntent = securitySettings()
        )

        return list
    }

    /** Hidden "Radio info" screen exposes per-SIM 2G; fall back to wireless settings. */
    private fun radioInfoOrWireless(): Intent? {
        val radioInfo = Intent().setClassName(
            "com.android.phone", "com.android.phone.settings.RadioInfo"
        )
        return if (radioInfo.resolveActivity(context.packageManager) != null) radioInfo
        else Intent(Settings.ACTION_WIRELESS_SETTINGS)
    }

    private fun securitySettings(): Intent? =
        Intent(Settings.ACTION_SECURITY_SETTINGS).takeIf {
            it.resolveActivity(context.packageManager) != null
        }
}
