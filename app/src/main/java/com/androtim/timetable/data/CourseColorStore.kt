package com.androtim.timetable.data

import android.content.Context
import android.content.SharedPreferences

/** Manual per-course color overrides, keyed by course key (code or name). */
class CourseColorStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("course_colors", Context.MODE_PRIVATE)

    @Suppress("UNCHECKED_CAST")
    fun all(): Map<String, Int> = prefs.all.filterValues { it is Int } as Map<String, Int>

    fun set(courseKey: String, color: Int) =
        prefs.edit().putInt(courseKey, color).apply()

    fun clear(courseKey: String) = prefs.edit().remove(courseKey).apply()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
}
