/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.triggers

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.lineageos.settings.ares.hw.GamekeyReader
import org.lineageos.settings.ares.hw.TouchInjector

/**
 * Trigger state machine. Consumes GamekeyReader events and:
 *  - plays slider open/close sound cues,
 *  - injects touchscreen pointers for trigger presses at the configured
 *    (per-app or global) coordinates,
 *  - auto-shows the mapping overlay when both sliders open within a
 *    6-second window while a listed game app is in the foreground.
 */
class TriggerController(private val context: Context) {

    private val repo = TriggerRepository(context)
    private val injector = TouchInjector(context)
    private val sounds = SoundCues(context)
    private val handler = Handler(Looper.getMainLooper())
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    private val reader = GamekeyReader { state -> handler.post { process(state) } }

    private var leftOpen = false
    private var rightOpen = false
    private var leftPressed = false
    private var rightPressed = false
    private var leftOpenTime = 0L
    private var rightOpenTime = 0L
    private var overlayAutoShown = false

    private var overlay: MappingOverlay? = null

    fun start() = reader.start()

    fun stop() {
        reader.stop()
        injector.releaseAll()
        sounds.release()
        hideOverlay()
    }

    fun showOverlay(forApp: String? = foregroundPackage()) {
        hideOverlay()
        // The overlay swallows press handling below; drop any held synthetic
        // pointers so none stay stuck down underneath it.
        injector.releaseAll()
        val target = forApp?.takeIf { repo.isGameApp(it) }
        overlay = MappingOverlay(context, repo, target) { overlay = null }.also { it.show() }
    }

    private fun hideOverlay() {
        overlay?.dismiss()
        overlay = null
    }

    private fun process(state: GamekeyReader.TriggerState) {
        if (!repo.enabled) {
            if (leftPressed || rightPressed) injector.releaseAll()
            leftPressed = false
            rightPressed = false
            leftOpen = state.leftOpen
            rightOpen = state.rightOpen
            return
        }

        val now = SystemClock.uptimeMillis()

        if (state.leftOpen != leftOpen) {
            leftOpen = state.leftOpen
            if (leftOpen) leftOpenTime = now
            onSliderToggled(isLeft = true, open = leftOpen)
        }
        if (state.rightOpen != rightOpen) {
            rightOpen = state.rightOpen
            if (rightOpen) rightOpenTime = now
            onSliderToggled(isLeft = false, open = rightOpen)
        }

        checkDualOpen()

        if (state.leftPressed != leftPressed) {
            leftPressed = state.leftPressed
            onButton(isLeft = true, pressed = leftPressed)
        }
        if (state.rightPressed != rightPressed) {
            rightPressed = state.rightPressed
            onButton(isLeft = false, pressed = rightPressed)
        }
    }

    private fun onSliderToggled(isLeft: Boolean, open: Boolean) {
        Log.d(TAG, "${if (isLeft) "left" else "right"} slider ${if (open) "open" else "closed"}")
        val style = repo.soundStyle
        if (style != TriggerRepository.SOUND_STYLE_NONE) {
            sounds.play(style, isLeft, open)
        }
        if (!open) injector.release(isLeft)
    }

    private fun checkDualOpen() {
        if (leftOpen && rightOpen) {
            if (overlayAutoShown) return
            val delta = Math.abs(leftOpenTime - rightOpenTime)
            val fg = foregroundPackage()
            if (delta <= DUAL_OPEN_WINDOW_MS && repo.isGameApp(fg)) {
                Log.i(TAG, "dual-open within ${delta}ms in $fg, showing mapping overlay")
                showOverlay(fg)
                overlayAutoShown = true
            }
        } else if (!leftOpen && !rightOpen) {
            overlayAutoShown = false
        }
    }

    private fun onButton(isLeft: Boolean, pressed: Boolean) {
        // Releases must always go through, whatever gated the press: a
        // guarded release leaves a synthetic pointer stuck down (e.g. press
        // → overlay auto-shows → release swallowed).
        if (!pressed) {
            injector.release(isLeft)
            return
        }
        val sliderOpen = if (isLeft) leftOpen else rightOpen
        if (!sliderOpen) return
        if (overlay != null) return // overlay owns the screen while mapping
        val fg = foregroundPackage()
        val coords = repo.coordsFor(fg)
        Log.d(TAG, "press ${if (isLeft) "L" else "R"} in $fg -> " +
            if (isLeft) "(${coords.leftX}, ${coords.leftY})" else "(${coords.rightX}, ${coords.rightY})")
        if (isLeft) {
            injector.press(true, coords.leftX, coords.leftY)
        } else {
            injector.press(false, coords.rightX, coords.rightY)
        }
    }

    @Suppress("DEPRECATION") // getRunningTasks: fine for system uid
    private fun foregroundPackage(): String? = try {
        activityManager.getRunningTasks(1).firstOrNull()?.topActivity?.packageName
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val TAG = "AresParts.Triggers"
        private const val DUAL_OPEN_WINDOW_MS = 6000L
    }
}
