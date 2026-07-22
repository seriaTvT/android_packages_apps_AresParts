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
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.telephony.TelephonyManager
import android.util.Log
import lineageos.providers.LineageSettings
import org.lineageos.settings.ares.hw.RgbLed
import org.lineageos.settings.ares.util.ForegroundApp
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * LED effect engine. Decides who owns the light strips and animates them.
 *
 * These strips double as the system battery/charging light: the framework
 * (LineageBatteryLights in BatteryService) drives them whenever a charger is
 * connected. That makes the framework a second, independent writer, so the
 * controller never fights it blindly:
 *
 *  - Charging indicator ON (default): while plugged in, effects yield
 *    entirely. The app writes nothing and the system charging light owns the
 *    strips, screen on or off. This is stock behavior — no race, no cleanup.
 *  - Charging indicator OFF: effects win even while charging. Because the
 *    framework would otherwise overwrite them on every battery event, the app
 *    suppresses the system battery light (LineageSettings BATTERY_LIGHT_ENABLED)
 *    for as long as it is actively driving an effect, and restores it on
 *    release — the restore write nudges BatteryService to repaint immediately,
 *    so the charging light returns with no dark gap.
 *  - Effects otherwise pause while the screen is off, except flash-during-calls.
 *  - "Only in games" reuses the trigger feature's game list.
 *
 * Priority: flash-during-calls > charging indicator (when on) > user effect.
 */
class LedController(private val context: Context) {

    private val repo = LedRepository(context)
    private val rgb = RgbLed(context)
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    private val thread = HandlerThread("AresParts.Led")
    private lateinit var handler: Handler

    private var screenOn = true
    private var inCall = false
    private var plugged = false
    private var owned = false
    private var suppressing = false
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
            LedRepository.KEY_ON_CALL, LedRepository.KEY_CHARGING_LIGHT,
            -> handler.post { apply() }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> screenOn = true
                Intent.ACTION_SCREEN_OFF -> screenOn = false
                Intent.ACTION_POWER_CONNECTED -> plugged = true
                Intent.ACTION_POWER_DISCONNECTED -> plugged = false
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
        // If a previous run died while it had the battery light suppressed,
        // restore it now so charging never stays dark across a crash.
        recoverBatteryLight()
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            },
        )
        plugged = batterySticky()
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
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
        // Charging indicator on: hand the strips to the system battery light.
        if (plugged && repo.chargingLight) return LedRepository.EFFECT_NONE
        if (!screenOn) return LedRepository.EFFECT_NONE
        val effect = repo.effect
        if (effect == LedRepository.EFFECT_NONE) return effect
        if (repo.onlyInGames && !isGameForeground()) return LedRepository.EFFECT_NONE
        return effect
    }

    private fun apply() {
        handler.removeCallbacks(tick)
        // The game gate has no event source; poll slowly while it is relevant
        // so effects stop on leaving a game and start on entering one.
        handler.removeCallbacks(gameRecheck)
        if (repo.onlyInGames && repo.effect != LedRepository.EFFECT_NONE && screenOn &&
            !(plugged && repo.chargingLight)
        ) {
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
        // While we drive an effect over a charger, keep the system battery
        // light from overwriting it. Off the charger there is nothing to fight.
        setBatteryLightSuppressed(plugged)
        if (effect != activeEffect) {
            hue = 0f
            breathStep = 0
            activeEffect = effect
            Log.d(TAG, "effect -> $effect")
        }
        tick.run()
    }

    private fun deactivate() {
        activeEffect = LedRepository.EFFECT_NONE
        if (owned) {
            rgb.releaseOwnership()
            owned = false
            Log.d(TAG, "released LED ownership")
        }
        // Restoring the setting also nudges BatteryService to repaint the
        // charging light immediately, so it returns without a dark gap.
        setBatteryLightSuppressed(false)
    }

    private val tick = object : Runnable {
        override fun run() {
            val delay = when (activeEffect) {
                // Static re-asserts on a slow tick: when we first suppress the
                // battery light BatteryService zeroes the strips once, async,
                // and a single write would lose that race. Re-writing keeps the
                // color regardless of ordering.
                LedRepository.EFFECT_STATIC -> {
                    setScaled(repo.color)
                    STATIC_TICK_MS
                }
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

    /**
     * Turn the system battery light off (or back on) by driving its
     * LineageSettings switch, saving the user's value so it can be restored.
     * Idempotent; the saved value and a recovery flag persist across a crash.
     */
    private fun setBatteryLightSuppressed(suppress: Boolean) {
        if (suppress == suppressing) return
        if (suppress) {
            val current = readBatteryLight() ?: return
            if (current == 0) return // already off in settings, nothing to fight
            repo.prefs.edit()
                .putInt(LedRepository.KEY_SAVED_BATTERY_LIGHT, current)
                .putBoolean(LedRepository.KEY_BATTERY_LIGHT_SUPPRESSED, true)
                .apply()
            if (writeBatteryLight(0)) suppressing = true
        } else {
            val saved = repo.prefs.getInt(LedRepository.KEY_SAVED_BATTERY_LIGHT, 1)
            repo.prefs.edit()
                .putBoolean(LedRepository.KEY_BATTERY_LIGHT_SUPPRESSED, false)
                .apply()
            writeBatteryLight(saved)
            suppressing = false
        }
    }

    private fun recoverBatteryLight() {
        if (!repo.prefs.getBoolean(LedRepository.KEY_BATTERY_LIGHT_SUPPRESSED, false)) return
        val saved = repo.prefs.getInt(LedRepository.KEY_SAVED_BATTERY_LIGHT, 1)
        repo.prefs.edit()
            .putBoolean(LedRepository.KEY_BATTERY_LIGHT_SUPPRESSED, false)
            .apply()
        writeBatteryLight(saved)
        Log.i(TAG, "recovered battery light after unclean shutdown -> $saved")
    }

    private fun readBatteryLight(): Int? = runCatching {
        LineageSettings.System.getInt(context.contentResolver, BATTERY_LIGHT_ENABLED, 1)
    }.getOrNull()

    /** Returns false (and logs) if the write is denied, so state stays honest. */
    private fun writeBatteryLight(value: Int): Boolean = runCatching {
        LineageSettings.System.putInt(context.contentResolver, BATTERY_LIGHT_ENABLED, value)
    }.onFailure { Log.e(TAG, "battery light write <- $value failed: ${it.message}") }.isSuccess

    private fun batterySticky(): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun isGameForeground(): Boolean {
        val fg = ForegroundApp.packageName(activityManager)
        return fg != null && fg in repo.gameApps
    }

    private val gameRecheck = Runnable { apply() }

    companion object {
        private const val TAG = "AresParts.Led"

        // lineageos.providers.LineageSettings.System.BATTERY_LIGHT_ENABLED
        private const val BATTERY_LIGHT_ENABLED = "battery_light_enabled"

        private const val DISCO_TICK_MS = 1000L
        private const val ANIM_TICK_MS = 100L
        private const val STATIC_TICK_MS = 1000L
        private const val RAINBOW_HUE_STEP = 3f
        private const val BREATH_STEPS = 40 // 4s period at ANIM_TICK_MS
        private const val GAME_POLL_MS = 3000L
    }
}
