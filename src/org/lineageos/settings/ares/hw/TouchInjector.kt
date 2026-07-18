/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.hw

import android.content.Context
import android.hardware.input.InputManager
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent

/**
 * Injects synthetic touchscreen pointers for the two triggers via
 * InputManager#injectInputEvent (INJECT_EVENTS, platform signature).
 *
 * Both triggers may be down at once, so single-pointer DOWN/UP and
 * POINTER_DOWN/POINTER_UP transitions are built explicitly.
 *
 * Pointer ids sit near the top of the dispatcher's 0..31 range: real
 * touchscreen fingers count up from 0, and frameworks that key pointer
 * state on the raw id without the device (Flutter, some game engines)
 * wedge a trigger when a real finger reuses its id mid-stream.
 */
class TouchInjector(context: Context) {

    private val inputManager = context.getSystemService(InputManager::class.java)

    private class Pointer(val id: Int) {
        var active = false
        var x = 0f
        var y = 0f
    }

    private val left = Pointer(29)
    private val right = Pointer(30)
    private var downTime = 0L
    private val lock = Any()

    fun press(isLeft: Boolean, x: Float, y: Float) = synchronized(lock) {
        val p = if (isLeft) left else right
        if (p.active) return
        p.x = x
        p.y = y
        p.active = true
        val now = SystemClock.uptimeMillis()
        val count = activePointers().size
        val action = if (count == 1) {
            downTime = now
            MotionEvent.ACTION_DOWN
        } else {
            MotionEvent.ACTION_POINTER_DOWN or
                (indexOf(p) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        }
        inject(action, now)
    }

    fun release(isLeft: Boolean) = synchronized(lock) {
        val p = if (isLeft) left else right
        if (!p.active) return
        val now = SystemClock.uptimeMillis()
        val count = activePointers().size
        val action = if (count == 1) {
            MotionEvent.ACTION_UP
        } else {
            MotionEvent.ACTION_POINTER_UP or
                (indexOf(p) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        }
        inject(action, now)
        p.active = false
    }

    fun releaseAll() {
        release(isLeft = false)
        release(isLeft = true)
    }

    private fun activePointers() = listOf(left, right).filter { it.active }

    private fun indexOf(p: Pointer) = activePointers().indexOfFirst { it.id == p.id }

    private fun inject(action: Int, eventTime: Long) {
        val pointers = activePointers()
        val props = Array(pointers.size) { i ->
            MotionEvent.PointerProperties().apply {
                id = pointers[i].id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(pointers.size) { i ->
            MotionEvent.PointerCoords().apply {
                x = pointers[i].x
                y = pointers[i].y
                pressure = 1f
                size = 1f
            }
        }
        val event = MotionEvent.obtain(
            downTime, eventTime, action, pointers.size, props, coords,
            /* metaState */ 0, /* buttonState */ 0, 1f, 1f,
            /* deviceId */ 0, /* edgeFlags */ 0,
            InputDevice.SOURCE_TOUCHSCREEN, /* flags */ 0,
        )
        try {
            // 0 = INJECT_INPUT_EVENT_MODE_ASYNC: lowest latency, no wait
            val ok = inputManager.injectInputEvent(event, 0)
            Log.d(TAG, "inject ${MotionEvent.actionToString(action)} ${pointers.size}p ok=$ok")
            if (!ok) Log.w(TAG, "dispatcher rejected $event")
        } catch (e: Exception) {
            Log.e(TAG, "inject failed: ${e.message}")
        } finally {
            event.recycle()
        }
    }

    companion object {
        private const val TAG = "AresParts.Injector"
    }
}
