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

    /** App theme: [THEME_AUTO], [THEME_LIGHT] or [THEME_DARK]. */
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, THEME_AUTO) ?: THEME_AUTO
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

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

    companion object {
        const val KEY_FEED_URL = "feed_url"
        const val KEY_APP_GROUPS = "app_groups"
        const val KEY_WIDGET_GROUPS = "widget_groups"
        const val KEY_COLOR_MODE = "color_mode"
        const val KEY_SINGLE_COLOR = "single_color"
        const val KEY_LAST_SYNC = "last_sync"

        const val KEY_THEME_MODE = "theme_mode"
        const val THEME_AUTO = "auto"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val COLOR_MODE_AUTO = "auto"
        const val COLOR_MODE_SINGLE = "single"
        const val COLOR_MODE_MANUAL = "manual"
        const val DEFAULT_SINGLE_COLOR = 0xFF3949AB.toInt()
    }
}
