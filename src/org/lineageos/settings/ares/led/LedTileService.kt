/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.led

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile toggling the LED effect: off <-> the last effect
 * that was active (Disco on first use, legacy-tile parity). The running
 * PartsService applies the change through its preference listener.
 */
class LedTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val repo = LedRepository(this)
        if (repo.effect == LedRepository.EFFECT_NONE) {
            repo.setEffect(repo.lastEffect)
        } else {
            repo.lastEffect = repo.effect
            repo.setEffect(LedRepository.EFFECT_NONE)
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.state = if (LedRepository(this).effect == LedRepository.EFFECT_NONE) {
            Tile.STATE_INACTIVE
        } else {
            Tile.STATE_ACTIVE
        }
        tile.updateTile()
    }
}
