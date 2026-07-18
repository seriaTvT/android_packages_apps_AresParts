/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.ui

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import org.lineageos.settings.ares.R
import org.lineageos.settings.ares.led.LedRepository

class LightingSettingsFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.lighting_settings, rootKey)
        updateDependentPrefs()
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        updateDependentPrefs()
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == LedRepository.KEY_EFFECT) updateDependentPrefs()
    }

    /** The color choice only matters for the static and breathing effects. */
    private fun updateDependentPrefs() {
        val effect = preferenceManager.sharedPreferences
            ?.getString(LedRepository.KEY_EFFECT, LedRepository.EFFECT_NONE)
        findPreference<ListPreference>(LedRepository.KEY_COLOR)?.isEnabled =
            effect == LedRepository.EFFECT_STATIC || effect == LedRepository.EFFECT_BREATHING
    }
}
