/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.triggers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.content.pm.PackageManager
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.lineageos.settings.ares.R

/**
 * Full-screen overlay with two draggable markers (L/R) for placing the
 * trigger touch points. Saves per-app coordinates when [targetPackage]
 * is set, else the global default.
 */
class MappingOverlay(
    private val context: Context,
    private val repo: TriggerRepository,
    private val targetPackage: String?,
    private val onDismissed: () -> Unit,
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null

    private var coords = repo.coordsFor(targetPackage)

    fun show() {
        if (root != null) return
        val markers = MarkerView(context)
        val container = FrameLayout(context)
        container.addView(
            markers,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(TextView(context).apply {
                text = targetLabel()
                setTextColor(Color.WHITE)
                setShadowLayer(6f, 0f, 0f, Color.BLACK)
                textSize = 16f
                gravity = Gravity.CENTER
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Button(context).apply {
                    text = context.getString(R.string.overlay_save)
                    setOnClickListener {
                        repo.saveCoords(targetPackage, coords)
                        dismiss()
                    }
                })
                addView(Button(context).apply {
                    text = context.getString(R.string.overlay_cancel)
                    setOnClickListener { dismiss() }
                })
            })
        }
        container.addView(
            header,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = 48 },
        )

        // The overlay must not disturb system bars or insets: hiding them
        // shifts the underlying app's layout, so markers placed against the
        // shifted layout land off-target during real play. The window merely
        // spans the whole display (bars, cutout and all) so view coordinates
        // equal the display coordinates the injector uses.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        }
        windowManager.addView(container, params)
        root = container
    }

    private fun targetLabel(): String {
        val pkg = targetPackage
            ?: return context.getString(R.string.overlay_target_global)
        val label = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
        return context.getString(R.string.overlay_target_app, label)
    }

    fun dismiss() {
        root?.let { windowManager.removeView(it) }
        root = null
        onDismissed()
    }

    private inner class MarkerView(context: Context) : View(context) {

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x552196F3
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = 0xFF2196F3.toInt()
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            textAlign = Paint.Align.CENTER
        }
        private val radius = 70f
        private var dragging: Int = NONE // LEFT or RIGHT while a pointer drags it

        override fun onDraw(canvas: Canvas) {
            drawMarker(canvas, coords.leftX, coords.leftY, "L")
            drawMarker(canvas, coords.rightX, coords.rightY, "R")
            // Keep edge gestures (back swipe) from firing while a marker is
            // dragged near a screen edge; the bars themselves stay visible so
            // the app layout matches what the markers will target in play.
            fun exclusion(x: Float, y: Float): Rect {
                val r = (radius * 2).toInt()
                return Rect(x.toInt() - r, y.toInt() - r, x.toInt() + r, y.toInt() + r)
            }
            systemGestureExclusionRects = listOf(
                exclusion(coords.leftX, coords.leftY),
                exclusion(coords.rightX, coords.rightY),
            )
        }

        private fun drawMarker(canvas: Canvas, x: Float, y: Float, label: String) {
            canvas.drawCircle(x, y, radius, fillPaint)
            canvas.drawCircle(x, y, radius, strokePaint)
            canvas.drawText(label, x, y + textPaint.textSize / 3, textPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = markerAt(event.x, event.y)
                    return dragging != NONE
                }
                MotionEvent.ACTION_MOVE -> {
                    when (dragging) {
                        LEFT -> coords = coords.copy(leftX = event.x, leftY = event.y)
                        RIGHT -> coords = coords.copy(rightX = event.x, rightY = event.y)
                        else -> return false
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = NONE
                    return true
                }
            }
            return false
        }

        private fun markerAt(x: Float, y: Float): Int {
            fun near(px: Float, py: Float) =
                Math.hypot((x - px).toDouble(), (y - py).toDouble()) <= radius * 1.5
            return when {
                near(coords.leftX, coords.leftY) -> LEFT
                near(coords.rightX, coords.rightY) -> RIGHT
                else -> NONE
            }
        }
    }

    private companion object {
        const val NONE = 0
        const val LEFT = 1
        const val RIGHT = 2
    }
}
