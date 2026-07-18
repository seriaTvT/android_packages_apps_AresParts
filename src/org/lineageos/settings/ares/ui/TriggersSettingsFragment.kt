/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.lineageos.settings.ares.PartsService
import org.lineageos.settings.ares.R
import org.lineageos.settings.ares.triggers.TriggerRepository

class TriggersSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.triggers_settings, rootKey)

        populateGameApps()

        findPreference<Preference>(KEY_PLACE_MARKERS)?.setOnPreferenceClickListener {
            PartsService.showMappingOverlay(requireContext())
            true
        }
    }

    /** Fill the game-app picker with launchable apps, sorted by label. */
    private fun populateGameApps() {
        val pref = findPreference<MultiSelectListPreference>(TriggerRepository.KEY_GAME_APPS)
            ?: return
        val pm = requireContext().packageManager
        val launchables = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.ResolveInfoFlags.of(0),
        )
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }

        pref.entries = launchables.map { it.second }.toTypedArray()
        pref.entryValues = launchables.map { it.first }.toTypedArray()
    }

    companion object {
        private const val KEY_PLACE_MARKERS = "trigger_place_markers"
    }
}
