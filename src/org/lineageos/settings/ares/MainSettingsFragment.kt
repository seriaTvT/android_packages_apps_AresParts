/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares

import android.os.Bundle
import com.android.settingslib.widget.SettingsBasePreferenceFragment

// SettingsBasePreferenceFragment (not plain PreferenceFragmentCompat) is what
// installs SettingsPreferenceGroupAdapter on Expressive builds: it sets the
// first/middle/last drawable states that select the rounded group backgrounds
// with the stock gray pressed-state ripple. Without it the rows hit the
// adapter-less fallback ripple and the highlight color diverges from Settings.
class MainSettingsFragment : SettingsBasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_settings, rootKey)
    }
}
