/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.triggers

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent

/**
 * Executes the configured trigger action when a press happens outside a
 * game-list app. Each fired action gets a haptic tick as confirmation.
 */
class TriggerActions(private val context: Context) {

    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)

    private var torchOn = false
    private var torchCameraId: String? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) torchOn = enabled
        }
    }

    init {
        torchCameraId = findTorchCamera()
        torchCameraId?.let {
            cameraManager?.registerTorchCallback(torchCallback, null)
        }
    }

    fun release() {
        if (torchCameraId != null) cameraManager?.unregisterTorchCallback(torchCallback)
    }

    /** Runs [action]; returns true when something was executed. */
    fun run(action: String, appPackage: String?): Boolean {
        val executed = try {
            when (action) {
                ACTION_TORCH -> toggleTorch()
                ACTION_CAMERA -> launchCamera()
                ACTION_MEDIA_PLAY_PAUSE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                ACTION_MEDIA_NEXT -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                ACTION_MEDIA_PREVIOUS -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                ACTION_RINGER -> cycleRinger()
                ACTION_WAKE -> wake()
                ACTION_APP -> launchApp(appPackage)
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "action $action failed", e)
            false
        }
        if (executed) {
            Log.d(TAG, "ran action $action")
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        }
        return executed
    }

    private fun findTorchCamera(): String? = try {
        cameraManager?.cameraIdList?.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: Exception) {
        Log.w(TAG, "no torch camera: ${e.message}")
        null
    }

    private fun toggleTorch(): Boolean {
        val id = torchCameraId ?: return false
        cameraManager?.setTorchMode(id, !torchOn) ?: return false
        return true
    }

    private fun launchCamera(): Boolean {
        wake()
        context.startActivity(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private fun mediaKey(code: Int): Boolean {
        audioManager ?: return false
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return true
    }

    private fun cycleRinger(): Boolean {
        val audio = audioManager ?: return false
        audio.ringerModeInternal = when (audio.ringerModeInternal) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        return true
    }

    private fun wake(): Boolean {
        powerManager?.wakeUp(
            SystemClock.uptimeMillis(),
            PowerManager.WAKE_REASON_GESTURE,
            "$TAG:trigger",
        ) ?: return false
        return true
    }

    private fun launchApp(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        wake()
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    companion object {
        private const val TAG = "AresParts.Actions"

        const val ACTION_NONE = "none"
        const val ACTION_TORCH = "torch"
        const val ACTION_CAMERA = "camera"
        const val ACTION_MEDIA_PLAY_PAUSE = "media_play_pause"
        const val ACTION_MEDIA_NEXT = "media_next"
        const val ACTION_MEDIA_PREVIOUS = "media_previous"
        const val ACTION_RINGER = "ringer"
        const val ACTION_WAKE = "wake"
        const val ACTION_APP = "app"
    }
}
