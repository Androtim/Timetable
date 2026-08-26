package com.androtim.timetable.data

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences-backed settings. Kept synchronous on purpose so the
 * widget provider (which runs outside a coroutine scope) can read them directly.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("timetable_settings", Context.MODE_PRIVATE)

    init {
        migrateLegacyGroups()
    }

    /** iCal feed URL; null until the user completes first-launch setup. */
    var feedUrl: String?
        get() = prefs.getString(KEY_FEED_URL, null)
        set(value) = prefs.edit().putString(KEY_FEED_URL, value).apply()

    /** Group tokens selected for the in-app filter. Empty = show everything. */
    var appGroups: Set<String>
        get() = prefs.getStringSet(KEY_APP_GROUPS, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_APP_GROUPS, value.toSet()).apply()

    /** Group tokens the widget is locked to; independent of [appGroups]. */
    var widgetGroups: Set<String>
        get() = prefs.getStringSet(KEY_WIDGET_GROUPS, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_WIDGET_GROUPS, value.toSet()).apply()

    /** Course color mode: [COLOR_MODE_AUTO], [COLOR_MODE_SINGLE] or [COLOR_MODE_MANUAL]. */
    var colorMode: String
        get() = prefs.getString(KEY_COLOR_MODE, COLOR_MODE_AUTO) ?: COLOR_MODE_AUTO
        set(value) = prefs.edit().putString(KEY_COLOR_MODE, value).apply()

    /** The one color used for every course in single-color mode (ARGB). */
    var singleColor: Int
        get() = prefs.getInt(KEY_SINGLE_COLOR, DEFAULT_SINGLE_COLOR)
        set(value) = prefs.edit().putInt(KEY_SINGLE_COLOR, value).apply()

    /** Epoch millis of the last successful sync, or 0 when never synced. */
    var lastSyncMillis: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    /**
     * Earlier versions hardcoded the AMU feed and stored a single group id
     * (e.g. "GB-1"). Expand that into the equivalent token set and keep the
     * old feed URL so existing installs keep working after the update.
     */
    private fun migrateLegacyGroups() {
        val legacyApp = prefs.getString(KEY_LEGACY_APP_GROUP, null)
        val legacyWidget = prefs.getString(KEY_LEGACY_WIDGET_GROUP, null)
        if (legacyApp == null && legacyWidget == null) return

        val editor = prefs.edit()
        if (legacyApp != null && !prefs.contains(KEY_APP_GROUPS)) {
            editor.putStringSet(KEY_APP_GROUPS, legacyTokens(legacyApp))
        }
        if (legacyWidget != null && !prefs.contains(KEY_WIDGET_GROUPS)) {
            editor.putStringSet(KEY_WIDGET_GROUPS, legacyTokens(legacyWidget))
        }
        if (!prefs.contains(KEY_FEED_URL)) {
            editor.putString(KEY_FEED_URL, LEGACY_AMU_URL)
        }
        editor.remove(KEY_LEGACY_APP_GROUP).remove(KEY_LEGACY_WIDGET_GROUP).apply()
    }

    private fun legacyTokens(id: String): Set<String> {
        val classToken = when {
            id.startsWith("GA1") -> "Groupe A1 an2"
            id.startsWith("GA2") -> "Groupe A2 an2"
            else -> "Groupe B an2"
        }
        return setOf(id, classToken, "2ème année")
    }

    companion object {
        const val KEY_FEED_URL = "feed_url"
        const val KEY_APP_GROUPS = "app_groups"
        const val KEY_WIDGET_GROUPS = "widget_groups"
        const val KEY_COLOR_MODE = "color_mode"
        const val KEY_SINGLE_COLOR = "single_color"
        const val KEY_LAST_SYNC = "last_sync"

        private const val KEY_LEGACY_APP_GROUP = "app_group"
        private const val KEY_LEGACY_WIDGET_GROUP = "widget_group"

        const val COLOR_MODE_AUTO = "auto"
        const val COLOR_MODE_SINGLE = "single"
        const val COLOR_MODE_MANUAL = "manual"
        const val DEFAULT_SINGLE_COLOR = 0xFF3949AB.toInt()

        private const val LEGACY_AMU_URL =
            "https://agenda-web-consult.univ-amu.fr/jsp/custom/modules/plannings/anonymous_cal.jsp" +
                "?projectId=8&resources=8400,8401,8402,8403,8404,8405&calType=ical" +
                "&firstDate=2026-08-17&lastDate=2027-08-15"
    }
}
