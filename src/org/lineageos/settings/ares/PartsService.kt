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

/**
 * Owns the device state machines: gamekey trigger reader (M1) and LED
 * effect/handoff logic (M3). M0 ships the empty shell so boot wiring,
 * process/domain placement and service lifetime can be validated early.
 */
class PartsService : Service() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "PartsService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "PartsService destroyed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AresParts.Service"

        fun start(context: Context) {
            context.startServiceAsUser(
                Intent(context, PartsService::class.java),
                UserHandle.CURRENT,
            )
        }
    }
}
