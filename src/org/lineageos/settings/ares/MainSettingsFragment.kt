/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares

import android.os.Bundle
import androidx.preference.PreferenceCategory
import androidx.preference.SeekBarPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import org.lineageos.settings.ares.hw.HapticStrength

// SettingsBasePreferenceFragment (not plain PreferenceFragmentCompat) is what
// installs SettingsPreferenceGroupAdapter on Expressive builds: it sets the
// first/middle/last drawable states that select the rounded group backgrounds
// with the stock gray pressed-state ripple. Without it the rows hit the
// adapter-less fallback ripple and the highlight color diverges from Settings.
class MainSettingsFragment : SettingsBasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_settings, rootKey)

        val haptics = HapticStrength(requireContext())
        if (haptics.available) {
            findPreference<SeekBarPreference>(KEY_VIBRATION_STRENGTH)
                ?.setOnPreferenceChangeListener { _, newValue ->
                    haptics.apply(newValue as Int)
                    true
                }
        } else {
            findPreference<PreferenceCategory>(KEY_CATEGORY_VIBRATION)?.isVisible = false
        }
    }

    companion object {
        const val KEY_VIBRATION_STRENGTH = "vibration_strength"
        private const val KEY_CATEGORY_VIBRATION = "category_vibration"
    }
}
