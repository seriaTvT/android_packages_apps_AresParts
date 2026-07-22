/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import org.lineageos.settings.ares.PartsActivity
import org.lineageos.settings.ares.PartsService
import org.lineageos.settings.ares.R
import org.lineageos.settings.ares.triggers.TriggerActions
import org.lineageos.settings.ares.triggers.TriggerRepository

class TriggersSettingsFragment : SettingsBasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.triggers_settings, rootKey)

        val launchables = launchableApps()
        populateGameApps(launchables)

        findPreference<Preference>(KEY_PLACE_MARKERS)?.setOnPreferenceClickListener {
            PartsService.showMappingOverlay(requireContext())
            true
        }

        setupActionPicker(
            TriggerRepository.KEY_ACTION_LEFT, TriggerRepository.KEY_ACTION_LEFT_APP, launchables,
        )
        setupActionPicker(
            TriggerRepository.KEY_ACTION_RIGHT, TriggerRepository.KEY_ACTION_RIGHT_APP, launchables,
        )
    }

    override fun onResume() {
        super.onResume()
        arguments?.getCharSequence(PartsActivity.ARG_TITLE)?.let { requireActivity().title = it }
    }

    /** Launchable apps as package -> label pairs, sorted by label. */
    private fun launchableApps(): List<Pair<String, String>> {
        val pm = requireContext().packageManager
        return pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.ResolveInfoFlags.of(0),
        )
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    private fun populateGameApps(launchables: List<Pair<String, String>>) {
        val pref = findPreference<MultiSelectListPreference>(TriggerRepository.KEY_GAME_APPS)
            ?: return
        pref.entries = launchables.map { it.second }.toTypedArray()
        pref.entryValues = launchables.map { it.first }.toTypedArray()
    }

    /** Show the app picker only while its action preference is "app". */
    private fun setupActionPicker(
        actionKey: String,
        appKey: String,
        launchables: List<Pair<String, String>>,
    ) {
        val actionPref = findPreference<ListPreference>(actionKey) ?: return
        val appPref = findPreference<ListPreference>(appKey) ?: return

        appPref.entries = launchables.map { it.second }.toTypedArray()
        appPref.entryValues = launchables.map { it.first }.toTypedArray()

        appPref.isVisible = actionPref.value == TriggerActions.ACTION_APP
        actionPref.setOnPreferenceChangeListener { _, newValue ->
            appPref.isVisible = newValue == TriggerActions.ACTION_APP
            true
        }
    }

    companion object {
        private const val KEY_PLACE_MARKERS = "trigger_place_markers"
    }
}
