/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.hw

import android.util.Log
import java.io.File
import java.io.FileInputStream

/**
 * Reads trigger hardware state from /dev/gamekey.
 *
 * The driver answers only exact 4-byte reads:
 * [hall_left, hall_right, key_left, key_right], each 0 or 1.
 * hall_* = slider open (trigger popped out), key_* = button pressed.
 */
class GamekeyReader(private val onState: (TriggerState) -> Unit) {

    data class TriggerState(
        val leftOpen: Boolean,
        val rightOpen: Boolean,
        val leftPressed: Boolean,
        val rightPressed: Boolean,
    )

    @Volatile
    private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::readLoop, "GamekeyReader").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun readLoop() {
        val buf = ByteArray(BUF_SIZE)
        var last: TriggerState? = null
        while (running) {
            try {
                val node = File(DEVICE_PATH)
                if (!node.exists() || !node.canRead()) {
                    Thread.sleep(NODE_WAIT_MS)
                    continue
                }
                FileInputStream(node).use { stream ->
                    while (running) {
                        if (stream.read(buf, 0, BUF_SIZE) == BUF_SIZE) {
                            val state = TriggerState(
                                leftOpen = buf[0].toInt() == 1,
                                rightOpen = buf[1].toInt() == 1,
                                leftPressed = buf[2].toInt() == 1,
                                rightPressed = buf[3].toInt() == 1,
                            )
                            if (state != last) {
                                last = state
                                onState(state)
                            }
                        }
                        Thread.sleep(POLL_MS)
                    }
                }
            } catch (e: InterruptedException) {
                // stop() interrupts the sleep; loop exits via `running`
            } catch (e: Exception) {
                Log.w(TAG, "read error, retrying: ${e.message}")
                try {
                    Thread.sleep(ERROR_BACKOFF_MS)
                } catch (ie: InterruptedException) {
                    // exit via `running`
                }
            }
        }
    }

    companion object {
        private const val TAG = "AresParts.Gamekey"
        private const val DEVICE_PATH = "/dev/gamekey"
        private const val BUF_SIZE = 4
        private const val POLL_MS = 12L
        private const val NODE_WAIT_MS = 2000L
        private const val ERROR_BACKOFF_MS = 250L
    }
}
