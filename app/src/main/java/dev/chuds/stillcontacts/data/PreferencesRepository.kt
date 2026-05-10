package dev.chuds.stillcontacts.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "still_contacts_settings",
)

private val FONT_PRESET_KEY = stringPreferencesKey("font_preset")
private val NAME_DISPLAY_ORDER_KEY = stringPreferencesKey("name_display_order")
private val SORT_ORDER_KEY = stringPreferencesKey("sort_order")
private val DEFAULT_ACCOUNT_NAME_KEY = stringPreferencesKey("default_account_name")
private val DEFAULT_ACCOUNT_TYPE_KEY = stringPreferencesKey("default_account_type")
private val HAPTICS_ENABLED_KEY = booleanPreferencesKey("haptics_enabled")

enum class FontPreset { System, Editorial, Terminal, Grotesk }

enum class NameDisplayOrder { GivenFamily, FamilyGiven, System }

enum class SortOrder { Given, Family }

data class StillContactsPreferences(
    val fontPreset: FontPreset = FontPreset.System,
    val nameDisplayOrder: NameDisplayOrder = NameDisplayOrder.GivenFamily,
    val sortOrder: SortOrder = SortOrder.Given,
    val defaultAccountName: String? = null,
    val defaultAccountType: String? = null,
    val hapticsEnabled: Boolean = true,
) {
    val accountTarget: AccountTarget
        get() = if (defaultAccountName != null && defaultAccountType != null) {
            AccountTarget.Named(defaultAccountName, defaultAccountType)
        } else {
            AccountTarget.PhoneOnly
        }
}

class PreferencesRepository(private val context: Context) {

    val settings: Flow<StillContactsPreferences> = context.preferencesDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            StillContactsPreferences(
                fontPreset = prefs[FONT_PRESET_KEY]
                    ?.let { runCatching { FontPreset.valueOf(it) }.getOrNull() }
                    ?: FontPreset.System,
                nameDisplayOrder = prefs[NAME_DISPLAY_ORDER_KEY]
                    ?.let { runCatching { NameDisplayOrder.valueOf(it) }.getOrNull() }
                    ?: NameDisplayOrder.GivenFamily,
                sortOrder = prefs[SORT_ORDER_KEY]
                    ?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() }
                    ?: SortOrder.Given,
                defaultAccountName = prefs[DEFAULT_ACCOUNT_NAME_KEY],
                defaultAccountType = prefs[DEFAULT_ACCOUNT_TYPE_KEY],
                hapticsEnabled = prefs[HAPTICS_ENABLED_KEY] ?: true,
            )
        }

    suspend fun setFontPreset(preset: FontPreset) {
        context.preferencesDataStore.edit { it[FONT_PRESET_KEY] = preset.name }
    }

    suspend fun setNameDisplayOrder(order: NameDisplayOrder) {
        context.preferencesDataStore.edit { it[NAME_DISPLAY_ORDER_KEY] = order.name }
    }

    suspend fun setSortOrder(order: SortOrder) {
        context.preferencesDataStore.edit { it[SORT_ORDER_KEY] = order.name }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.preferencesDataStore.edit { it[HAPTICS_ENABLED_KEY] = enabled }
    }

    suspend fun setDefaultAccount(name: String?, type: String?) {
        context.preferencesDataStore.edit { prefs ->
            if (name == null || type == null) {
                prefs.remove(DEFAULT_ACCOUNT_NAME_KEY)
                prefs.remove(DEFAULT_ACCOUNT_TYPE_KEY)
            } else {
                prefs[DEFAULT_ACCOUNT_NAME_KEY] = name
                prefs[DEFAULT_ACCOUNT_TYPE_KEY] = type
            }
        }
    }
}
