/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.hw

import android.content.Context
import android.util.Log
import java.io.File
import org.lineageos.settings.ares.R

/**
 * Vibration strength via the awinic driver's gain_scale sysfs attr (our
 * kernel addition): a percent scale applied to the drive gain of every
 * effect. Boost voltage (vmax) turned out not to modulate perceived
 * strength on this motor, so gain is the knob; 100 = stock.
 */
class HapticStrength(context: Context) {

    private val node = File(context.getString(R.string.config_haptic_gain_scale_path))

    val available: Boolean
        get() = node.exists()

    fun apply(percent: Int) {
        if (!available) return
        try {
            node.writeText(percent.coerceIn(1, 100).toString())
            Log.d(TAG, "gain_scale <- $percent%")
        } catch (e: Exception) {
            Log.e(TAG, "failed writing $node", e)
        }
    }

    companion object {
        private const val TAG = "AresParts.Haptic"
    }
}
