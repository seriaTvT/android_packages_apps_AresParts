/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.hw

import android.content.Context
import android.util.Log
import org.lineageos.settings.ares.R
import java.io.File

/**
 * Direct sysfs driver for the rear RGB light strips. Channel paths come
 * from config resources so another device can override them via RRO.
 *
 * Ownership handoff: the kernel `trigger` attribute is saved and forced to
 * "none" while the app animates the LEDs, then restored on release so any
 * kernel-side behavior (and the charging light written by the lights HAL)
 * resumes cleanly.
 */
class RgbLed(context: Context) {

    private class Channel(dir: String) {
        val brightness = File(dir, "brightness")
        val trigger = File(dir, "trigger")
        val maxBrightness = runCatching {
            File(dir, "max_brightness").readText().trim().toInt()
        }.getOrDefault(255).coerceAtLeast(1)
        var savedTrigger: String? = null
    }

    private val red = Channel(context.getString(R.string.config_led_red_dir))
    private val green = Channel(context.getString(R.string.config_led_green_dir))
    private val blue = Channel(context.getString(R.string.config_led_blue_dir))
    private val channels = listOf(red, green, blue)

    val available: Boolean = channels.all { it.brightness.exists() }

    /** Set the color; each component 0..255, scaled to the channel's max. */
    fun setColor(r: Int, g: Int, b: Int) {
        write(red, r)
        write(green, g)
        write(blue, b)
    }

    fun off() = setColor(0, 0, 0)

    /**
     * Save each channel's active kernel trigger and force it to "none" so
     * nothing else drives the LED while an effect runs.
     */
    fun takeOwnership() {
        for (c in channels) {
            c.savedTrigger = readActiveTrigger(c)
            if (c.savedTrigger != null && c.savedTrigger != "none") {
                writeText(c.trigger, "none")
            }
        }
    }

    /** Restore saved triggers and turn the LEDs off. */
    fun releaseOwnership() {
        off()
        for (c in channels) {
            val saved = c.savedTrigger ?: continue
            if (saved != "none") writeText(c.trigger, saved)
            c.savedTrigger = null
        }
    }

    /** The bracketed entry of a sysfs trigger list, e.g. "[none] timer". */
    private fun readActiveTrigger(c: Channel): String? = runCatching {
        val text = c.trigger.readText()
        Regex("""\[([^\]]+)\]""").find(text)?.groupValues?.get(1)
    }.getOrNull()

    private fun write(c: Channel, value: Int) {
        val scaled = value.coerceIn(0, 255) * c.maxBrightness / 255
        writeText(c.brightness, scaled.toString())
    }

    private fun writeText(file: File, value: String) {
        try {
            file.writeText(value)
        } catch (e: Exception) {
            Log.e(TAG, "write ${file.path} <- $value failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AresParts.RgbLed"
    }
}
