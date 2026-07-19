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
 * Vibration strength via the awinic haptic driver's vmax sysfs attr
 * (the kernel folds writes into bst_vol_ram/rtp so they persist across
 * effects). The valid boost-voltage code range depends on which chip the
 * unit shipped with; the bst_vol attr only exists on the aw86927, so its
 * presence is the chip detector.
 */
class HapticStrength(context: Context) {

    private val vmax = File(context.getString(R.string.config_haptic_vmax_path))
    private val isAw86927 = File(context.getString(R.string.config_haptic_bst_vol_path)).exists()

    val available: Boolean
        get() = vmax.exists()

    /** Map percent (0-100) onto the chip's boost-voltage code range and write it. */
    fun apply(percent: Int) {
        if (!available) return
        val min = if (isAw86927) AW86927_MIN else AW8697_MIN
        val max = if (isAw86927) AW86927_MAX else AW8697_MAX
        val code = min + ((max - min) * percent.coerceIn(0, 100) + 50) / 100
        try {
            vmax.writeText(code.toString())
            Log.d(TAG, "strength $percent% -> code 0x%02x (aw86927=$isAw86927)".format(code))
        } catch (e: Exception) {
            Log.e(TAG, "failed writing $vmax", e)
        }
    }

    companion object {
        private const val TAG = "AresParts.Haptic"

        // aw8697: 5-bit BSTDBG4 boost code; DT default 0x11 (~55%).
        private const val AW8697_MIN = 0x00
        private const val AW8697_MAX = 0x1F

        // aw86927: PLAYCFG1 codes for 6.0V..10.5V; driver clamps to this range.
        private const val AW86927_MIN = 0x28
        private const val AW86927_MAX = 0x70
    }
}
