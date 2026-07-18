/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.led

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.telephony.TelephonyManager
import android.util.Log
import org.lineageos.settings.ares.hw.RgbLed
import org.lineageos.settings.ares.util.ForegroundApp
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * LED effect engine. Decides who owns the light strips and animates them:
 *
 *  - While no effect is active the app leaves the sysfs nodes alone, so
 *    the lights HAL charging/battery light works untouched.
 *  - When an effect activates, the kernel `trigger` of each channel is
 *    saved and forced to "none" (RgbLed.takeOwnership); on deactivation
 *    the triggers are restored and the LEDs zeroed, and the battery light
 *    reasserts itself on the next battery event.
 *  - Effects pause while the screen is off (no point burning battery on a
 *    light nobody sees), except the flash-during-calls override.
 *  - "Only in games" reuses the trigger feature's game list.
 */
class LedController(private val context: Context) {

    private val repo = LedRepository(context)
    private val rgb = RgbLed(context)
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    private val thread = HandlerThread("AresParts.Led")
    private lateinit var handler: Handler

    private var screenOn = true
    private var inCall = false
    private var owned = false
    private var activeEffect = LedRepository.EFFECT_NONE

    // Animation state
    private val random = Random()
    private var hue = 0f
    private var breathStep = 0

    private var started = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            LedRepository.KEY_EFFECT, LedRepository.KEY_COLOR,
            LedRepository.KEY_BRIGHTNESS, LedRepository.KEY_ONLY_IN_GAMES,
            LedRepository.KEY_ON_CALL,
            -> handler.post { apply() }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> screenOn = true
                Intent.ACTION_SCREEN_OFF -> screenOn = false
                TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                    val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                    inCall = state == TelephonyManager.EXTRA_STATE_RINGING ||
                        state == TelephonyManager.EXTRA_STATE_OFFHOOK
                }
                else -> return
            }
            handler.post { apply() }
        }
    }

    fun start() {
        if (!rgb.available) {
            Log.i(TAG, "LED nodes not present, controller idle")
            return
        }
        thread.start()
        handler = Handler(thread.looper)
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            },
        )
        repo.prefs.registerOnSharedPreferenceChangeListener(prefListener)
        started = true
        handler.post { apply() }
    }

    fun stop() {
        if (!started) return
        started = false
        context.unregisterReceiver(receiver)
        repo.prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        handler.post { deactivate() }
        thread.quitSafely()
    }

    /** What should be running right now, given every gate. */
    private fun desiredEffect(): String {
        if (inCall && repo.flashOnCall) return LedRepository.EFFECT_DISCO
        if (!screenOn) return LedRepository.EFFECT_NONE
        val effect = repo.effect
        if (effect == LedRepository.EFFECT_NONE) return effect
        if (repo.onlyInGames && !isGameForeground()) return LedRepository.EFFECT_NONE
        return effect
    }

    private fun apply() {
        handler.removeCallbacks(tick)
        // The game gate has no event source; poll slowly while it is
        // relevant so effects both stop on leaving a game and start on
        // entering one.
        handler.removeCallbacks(gameRecheck)
        if (repo.onlyInGames && repo.effect != LedRepository.EFFECT_NONE && screenOn) {
            handler.postDelayed(gameRecheck, GAME_POLL_MS)
        }
        val effect = desiredEffect()
        if (effect == LedRepository.EFFECT_NONE) {
            deactivate()
            return
        }
        if (!owned) {
            rgb.takeOwnership()
            owned = true
        }
        if (effect != activeEffect) {
            hue = 0f
            breathStep = 0
            activeEffect = effect
            Log.d(TAG, "effect -> $effect")
        }
        if (effect == LedRepository.EFFECT_STATIC) {
            setScaled(repo.color)
        } else {
            tick.run()
        }
    }

    private fun deactivate() {
        activeEffect = LedRepository.EFFECT_NONE
        if (owned) {
            rgb.releaseOwnership()
            owned = false
            Log.d(TAG, "released LED ownership")
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val delay = when (activeEffect) {
                LedRepository.EFFECT_DISCO -> {
                    setScaled(
                        Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)),
                    )
                    DISCO_TICK_MS
                }
                LedRepository.EFFECT_RAINBOW -> {
                    hue = (hue + RAINBOW_HUE_STEP) % 360f
                    setScaled(Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                    ANIM_TICK_MS
                }
                LedRepository.EFFECT_BREATHING -> {
                    breathStep = (breathStep + 1) % BREATH_STEPS
                    val phase = sin(PI * breathStep / BREATH_STEPS).toFloat()
                    setScaled(repo.color, phase)
                    ANIM_TICK_MS
                }
                else -> return
            }
            handler.postDelayed(this, delay)
        }
    }

    private fun setScaled(color: Int, factor: Float = 1f) {
        val scale = factor * repo.brightnessPercent / 100f
        rgb.setColor(
            (Color.red(color) * scale).toInt(),
            (Color.green(color) * scale).toInt(),
            (Color.blue(color) * scale).toInt(),
        )
    }

    private fun isGameForeground(): Boolean {
        val fg = ForegroundApp.packageName(activityManager)
        return fg != null && fg in repo.gameApps
    }

    private val gameRecheck = Runnable { apply() }

    companion object {
        private const val TAG = "AresParts.Led"
        private const val DISCO_TICK_MS = 1000L
        private const val ANIM_TICK_MS = 100L
        private const val RAINBOW_HUE_STEP = 3f
        private const val BREATH_STEPS = 40 // 4s period at ANIM_TICK_MS
        private const val GAME_POLL_MS = 3000L
    }
}
