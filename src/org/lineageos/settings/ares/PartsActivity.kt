/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares

import android.os.Bundle
import android.view.MenuItem
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class PartsActivity :
    CollapsingToolbarBaseActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.settings_tile_title)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    MainSettingsFragment(),
                )
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // The collapsing toolbar wires its Up affordance to android.R.id.home.
        // Walk back through our own sub-pages first, matching the system back
        // gesture; only leave the activity once we are at the root screen.
        if (item.itemId == android.R.id.home && supportFragmentManager.popBackStackImmediate()) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference,
    ): Boolean {
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment ?: return false,
        )
        // Carry the tapped entry's title so the sub-page can restore it from
        // its own onResume, including after the back stack pops back onto it.
        fragment.arguments = Bundle(pref.extras).apply {
            putCharSequence(ARG_TITLE, pref.title)
        }
        title = pref.title
        supportFragmentManager
            .beginTransaction()
            .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, fragment)
            .addToBackStack(null)
            .commit()
        return true
    }

    companion object {
        const val ARG_TITLE = "org.lineageos.settings.ares.ARG_TITLE"
    }
}
