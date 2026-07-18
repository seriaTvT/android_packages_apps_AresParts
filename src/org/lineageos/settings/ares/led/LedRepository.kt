/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.led

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.preference.PreferenceManager
import org.lineageos.settings.ares.triggers.TriggerRepository

/** Preference-backed storage for the LED effect settings. */
class LedRepository(context: Context) {

    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    val effect: String
        get() = prefs.getString(KEY_EFFECT, EFFECT_NONE) ?: EFFECT_NONE

    fun setEffect(effect: String) {
        prefs.edit().putString(KEY_EFFECT, effect).apply()
    }

    /** Effect the QS tile re-enables; remembered when the tile turns off. */
    var lastEffect: String
        get() = prefs.getString(KEY_LAST_EFFECT, EFFECT_DISCO) ?: EFFECT_DISCO
        set(value) {
            prefs.edit().putString(KEY_LAST_EFFECT, value).apply()
        }

    val color: Int
        get() = runCatching {
            Color.parseColor(prefs.getString(KEY_COLOR, DEFAULT_COLOR))
        }.getOrDefault(Color.WHITE)

    val brightnessPercent: Int
        get() = prefs.getInt(KEY_BRIGHTNESS, 100).coerceIn(1, 100)

    val onlyInGames: Boolean
        get() = prefs.getBoolean(KEY_ONLY_IN_GAMES, false)

    val flashOnCall: Boolean
        get() = prefs.getBoolean(KEY_ON_CALL, false)

    /** Shared with the trigger feature: one game list for the whole app. */
    val gameApps: Set<String>
        get() = prefs.getStringSet(TriggerRepository.KEY_GAME_APPS, emptySet()) ?: emptySet()

    companion object {
        const val KEY_EFFECT = "led_effect"
        const val KEY_LAST_EFFECT = "led_last_effect"
        const val KEY_COLOR = "led_color"
        const val KEY_BRIGHTNESS = "led_brightness"
        const val KEY_ONLY_IN_GAMES = "led_only_in_games"
        const val KEY_ON_CALL = "led_on_call"

        const val EFFECT_NONE = "none"
        const val EFFECT_STATIC = "static"
        const val EFFECT_BREATHING = "breathing"
        const val EFFECT_RAINBOW = "rainbow"
        const val EFFECT_DISCO = "disco"

        const val DEFAULT_COLOR = "#FFFFFF"
    }
}
