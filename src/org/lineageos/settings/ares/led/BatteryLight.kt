/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.led

import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.BatteryManager
import android.provider.Settings
import lineageos.providers.LineageSettings

/**
 * Mirror of the framework battery light policy (LineageBatteryLights.calcLights,
 * solid cases only) so the LED can be seeded with the color the lights HAL is
 * about to maintain. BatteryService only rewrites the light on battery events,
 * so without a seed the strips would stay dark from the moment we release them
 * until the next event.
 */
class BatteryLight(private val context: Context) {

    /**
     * The solid color the system battery light should currently show, with
     * the user's battery-light brightness applied, or null for off.
     */
    fun currentColor(level: Int, status: Int): Int? {
        val resolver = context.contentResolver
        if (getLineageInt(LineageSettings.System.BATTERY_LIGHT_ENABLED, 1) == 0) return null

        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val full = status == BatteryManager.BATTERY_STATUS_FULL || level >= 100
        if (!charging && !full) return null

        val fullChargeDisabled =
            getLineageInt(LineageSettings.System.BATTERY_LIGHT_FULL_CHARGE_DISABLED, 1) != 0
        if (fullChargeDisabled && full) return null

        val low = level <= context.resources.getInteger(
            com.android.internal.R.integer.config_lowBatteryWarningLevel,
        )
        val color = when {
            low -> lightColor(
                LineageSettings.System.BATTERY_LIGHT_LOW_COLOR,
                com.android.internal.R.integer.config_notificationsBatteryLowARGB,
            )
            full || level >= 90 -> lightColor(
                LineageSettings.System.BATTERY_LIGHT_FULL_COLOR,
                com.android.internal.R.integer.config_notificationsBatteryFullARGB,
            )
            else -> lightColor(
                LineageSettings.System.BATTERY_LIGHT_MEDIUM_COLOR,
                com.android.internal.R.integer.config_notificationsBatteryMediumARGB,
            )
        }

        val zenMode = context.getSystemService(NotificationManager::class.java).zenMode
        val brightness = if (zenMode == Settings.Global.ZEN_MODE_OFF) {
            getLineageInt(LineageSettings.System.BATTERY_LIGHT_BRIGHTNESS_LEVEL, 255)
        } else {
            getLineageInt(LineageSettings.System.BATTERY_LIGHT_BRIGHTNESS_LEVEL_ZEN, 255)
        }.coerceIn(0, 255)

        return Color.rgb(
            Color.red(color) * brightness / 255,
            Color.green(color) * brightness / 255,
            Color.blue(color) * brightness / 255,
        )
    }

    private fun lightColor(setting: String, configRes: Int): Int =
        getLineageInt(setting, context.resources.getInteger(configRes))

    private fun getLineageInt(setting: String, default: Int): Int =
        LineageSettings.System.getInt(context.contentResolver, setting, default)
}
