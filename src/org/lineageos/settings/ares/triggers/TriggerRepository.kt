/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.triggers

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Preference-backed storage for trigger settings: global enable, sound
 * style, the game app list, and touch-mapping coordinates (global default
 * plus optional per-app override).
 */
class TriggerRepository(context: Context) {

    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    data class Coords(val leftX: Float, val leftY: Float, val rightX: Float, val rightY: Float)

    val enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)

    val soundStyle: String
        get() = prefs.getString(KEY_SOUND_STYLE, SOUND_STYLE_DEFAULT) ?: SOUND_STYLE_DEFAULT

    val gameApps: Set<String>
        get() = prefs.getStringSet(KEY_GAME_APPS, emptySet()) ?: emptySet()

    fun isGameApp(packageName: String?) = packageName != null && packageName in gameApps

    fun coordsFor(packageName: String?): Coords {
        val suffix = packageName?.let { ":$it" } ?: ""
        fun value(base: String, fallback: Float): Float {
            val perApp = prefs.getFloat(base + suffix, Float.NaN)
            if (!perApp.isNaN()) return perApp
            return prefs.getFloat(base, fallback)
        }
        return Coords(
            leftX = value(KEY_LEFT_X, DEFAULT_LEFT_X),
            leftY = value(KEY_LEFT_Y, DEFAULT_LEFT_Y),
            rightX = value(KEY_RIGHT_X, DEFAULT_RIGHT_X),
            rightY = value(KEY_RIGHT_Y, DEFAULT_RIGHT_Y),
        )
    }

    /** Save per-app coordinates, or the global default when [packageName] is null. */
    fun saveCoords(packageName: String?, coords: Coords) {
        val suffix = packageName?.let { ":$it" } ?: ""
        prefs.edit()
            .putFloat(KEY_LEFT_X + suffix, coords.leftX)
            .putFloat(KEY_LEFT_Y + suffix, coords.leftY)
            .putFloat(KEY_RIGHT_X + suffix, coords.rightX)
            .putFloat(KEY_RIGHT_Y + suffix, coords.rightY)
            .apply()
    }

    companion object {
        const val KEY_ENABLED = "triggers_enabled"
        const val KEY_SOUND_STYLE = "trigger_sound_style"
        const val KEY_GAME_APPS = "trigger_game_apps"
        const val KEY_LEFT_X = "trigger_left_x"
        const val KEY_LEFT_Y = "trigger_left_y"
        const val KEY_RIGHT_X = "trigger_right_x"
        const val KEY_RIGHT_Y = "trigger_right_y"

        const val SOUND_STYLE_DEFAULT = "classic"
        const val SOUND_STYLE_NONE = "none"

        // Sensible landscape defaults on the 1080x2400 panel: upper corners
        // when held horizontally (portrait coords; left trigger sits high).
        private const val DEFAULT_LEFT_X = 540f
        private const val DEFAULT_LEFT_Y = 700f
        private const val DEFAULT_RIGHT_X = 540f
        private const val DEFAULT_RIGHT_Y = 1700f
    }
}
