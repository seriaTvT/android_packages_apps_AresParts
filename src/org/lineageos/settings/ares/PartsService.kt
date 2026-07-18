/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.UserHandle
import android.util.Log
import org.lineageos.settings.ares.led.LedController
import org.lineageos.settings.ares.triggers.TriggerController

/**
 * Owns the device state machines: the gamekey trigger pipeline (M1) and
 * the LED effect/handoff logic (M3).
 */
class PartsService : Service() {

    private lateinit var triggers: TriggerController
    private lateinit var leds: LedController

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "PartsService created")
        triggers = TriggerController(this)
        triggers.start()
        leds = LedController(this)
        leds.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_MAPPING_OVERLAY) {
            triggers.showOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "PartsService destroyed")
        triggers.stop()
        leds.stop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AresParts.Service"
        const val ACTION_SHOW_MAPPING_OVERLAY = "org.lineageos.settings.ares.SHOW_MAPPING_OVERLAY"

        fun start(context: Context) {
            context.startServiceAsUser(
                Intent(context, PartsService::class.java),
                UserHandle.CURRENT,
            )
        }

        fun showMappingOverlay(context: Context) {
            context.startServiceAsUser(
                Intent(context, PartsService::class.java)
                    .setAction(ACTION_SHOW_MAPPING_OVERLAY),
                UserHandle.CURRENT,
            )
        }
    }
}
