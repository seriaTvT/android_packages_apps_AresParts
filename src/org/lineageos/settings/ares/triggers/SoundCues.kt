/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.triggers

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import org.lineageos.settings.ares.R

/**
 * Slider open/close sound cues. Four styles, each with distinct
 * left/right open/close samples (stock-style ogg assets).
 */
class SoundCues(context: Context) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // style -> [openLeft, openRight, closeLeft, closeRight]
    private val samples: Map<String, IntArray> = mapOf(
        "classic" to intArrayOf(
            soundPool.load(context, R.raw.keys_kanata_open_l, 1),
            soundPool.load(context, R.raw.keys_kanata_open_r, 1),
            soundPool.load(context, R.raw.keys_kanata_close_l, 1),
            soundPool.load(context, R.raw.keys_kanata_close_r, 1),
        ),
        "mechanical" to intArrayOf(
            soundPool.load(context, R.raw.keys_mechanicals_open_l, 1),
            soundPool.load(context, R.raw.keys_mechanicals_open_r, 1),
            soundPool.load(context, R.raw.keys_mechanicals_close_l, 1),
            soundPool.load(context, R.raw.keys_mechanicals_close_r, 1),
        ),
        "car" to intArrayOf(
            soundPool.load(context, R.raw.keys_car_open_l, 1),
            soundPool.load(context, R.raw.keys_car_open_r, 1),
            soundPool.load(context, R.raw.keys_car_close_l, 1),
            soundPool.load(context, R.raw.keys_car_close_r, 1),
        ),
        "scifi" to intArrayOf(
            soundPool.load(context, R.raw.keys_scifi_open_l, 1),
            soundPool.load(context, R.raw.keys_scifi_open_r, 1),
            soundPool.load(context, R.raw.keys_scifi_close_l, 1),
            soundPool.load(context, R.raw.keys_scifi_close_r, 1),
        ),
    )

    fun play(style: String, isLeft: Boolean, open: Boolean) {
        val set = samples[style] ?: return
        val index = (if (open) 0 else 2) + (if (isLeft) 0 else 1)
        soundPool.play(set[index], 1f, 1f, 1, 0, 1f)
    }

    fun release() = soundPool.release()
}
