/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ares.util

import android.app.ActivityManager

object ForegroundApp {

    @Suppress("DEPRECATION") // getRunningTasks: fine for system uid
    fun packageName(activityManager: ActivityManager?): String? = try {
        activityManager?.getRunningTasks(1)?.firstOrNull()?.topActivity?.packageName
    } catch (e: Exception) {
        null
    }
}
