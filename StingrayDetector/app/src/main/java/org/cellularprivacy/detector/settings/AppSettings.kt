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

/** What the app does automatically when the threat level reaches HIGH. */
enum class PanicAction {
    NONE,        // only the standing notification
    NOTIFY,      // high-priority full-screen alert
    LOCK,        // alert + lock the screen now (needs Device Admin)
    PANIC_FULL;  // alert + lock + cut radios / power off if root is available

    companion object {
        fun fromName(name: String?): PanicAction =
            entries.firstOrNull { it.name == name } ?: NOTIFY
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

/** Thin typed wrapper over DataStore for the few settings we have. */
class AppSettings(private val context: Context) {

    private val keyAutoProtect = booleanPreferencesKey("auto_protect")
    private val keyPanicAction = stringPreferencesKey("panic_action")
    private val keyRequireConfirm = booleanPreferencesKey("panic_require_confirm")

    data class Values(
        val autoProtect: Boolean = true,
        val panicAction: PanicAction = PanicAction.NOTIFY,
        val requireConfirm: Boolean = true
    )

    val values: Flow<Values> = context.dataStore.data.map { p ->
        Values(
            autoProtect = p[keyAutoProtect] ?: true,
            panicAction = PanicAction.fromName(p[keyPanicAction]),
            requireConfirm = p[keyRequireConfirm] ?: true
        )
    }

    suspend fun setAutoProtect(on: Boolean) =
        context.dataStore.edit { it[keyAutoProtect] = on }.let { }

    suspend fun setPanicAction(action: PanicAction) =
        context.dataStore.edit { it[keyPanicAction] = action.name }.let { }

    suspend fun setRequireConfirm(on: Boolean) =
        context.dataStore.edit { it[keyRequireConfirm] = on }.let { }
}
