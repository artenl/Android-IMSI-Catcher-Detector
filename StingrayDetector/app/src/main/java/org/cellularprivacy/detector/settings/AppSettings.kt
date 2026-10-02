package org.cellularprivacy.detector.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What the app does automatically when the threat level reaches HIGH.
 *
 * Ordered by how much it actually protects against *radio-layer* data
 * collection. A screen lock does NOT stop an IMSI-catcher: the modem stays
 * registered and keeps answering identity requests. Only cutting the radio
 * (airplane mode / power off) stops ongoing collection, and even then only
 * from that moment on. So CUT_RADIO is the meaningful protective action; LOCK
 * is kept only as an anti-seizure measure and labelled as such.
 */
enum class PanicAction {
    NONE,        // only the standing notification
    ALERT,       // high-priority full-screen alert + alarm (so the user can act)
    LOCK,        // ALERT + lock screen (anti-seizure only; does NOT stop collection)
    CUT_RADIO;   // ALERT + cut the radio: airplane/power-off via root, else open airplane settings

    companion object {
        fun fromName(name: String?): PanicAction =
            entries.firstOrNull { it.name == name } ?: ALERT
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

/** Thin typed wrapper over DataStore for the few settings we have. */
class AppSettings(private val context: Context) {

    private val keyAutoProtect = booleanPreferencesKey("auto_protect")
    private val keyPanicAction = stringPreferencesKey("panic_action")
    private val keyRequireConfirm = booleanPreferencesKey("panic_require_confirm")
    private val keyHideTwoGPrompt = booleanPreferencesKey("hide_2g_prompt")
    private val keyExpertMode = booleanPreferencesKey("expert_mode")

    data class Values(
        val autoProtect: Boolean = true,
        val panicAction: PanicAction = PanicAction.ALERT,
        val requireConfirm: Boolean = true,
        val hideTwoGPrompt: Boolean = false,
        val expertMode: Boolean = false
    )

    val values: Flow<Values> = context.dataStore.data.map { p ->
        Values(
            autoProtect = p[keyAutoProtect] ?: true,
            panicAction = PanicAction.fromName(p[keyPanicAction]),
            requireConfirm = p[keyRequireConfirm] ?: true,
            hideTwoGPrompt = p[keyHideTwoGPrompt] ?: false,
            expertMode = p[keyExpertMode] ?: false
        )
    }

    suspend fun setAutoProtect(on: Boolean) =
        context.dataStore.edit { it[keyAutoProtect] = on }.let { }

    suspend fun setPanicAction(action: PanicAction) =
        context.dataStore.edit { it[keyPanicAction] = action.name }.let { }

    suspend fun setRequireConfirm(on: Boolean) =
        context.dataStore.edit { it[keyRequireConfirm] = on }.let { }

    suspend fun setHideTwoGPrompt(on: Boolean) =
        context.dataStore.edit { it[keyHideTwoGPrompt] = on }.let { }

    suspend fun setExpertMode(on: Boolean) =
        context.dataStore.edit { it[keyExpertMode] = on }.let { }
}
