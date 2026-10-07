package com.androidautobrowser.browser.mirror

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Shares the page open in My Browser between the phone display and the car display.
 * Live updates use an in-app broadcast. A freshly opened screen also reads the
 * last page if the other display published it recently.
 */
object BrowserSync {
    const val ACTION = "com.androidautobrowser.browser.action.URL_SYNC"
    const val EXTRA_URL = "url"
    const val EXTRA_DISPLAY = "display"

    private const val PREFS = "browser_prefs"
    private const val KEY_URL = "sync_url"
    private const val KEY_DISPLAY = "sync_display"
    private const val KEY_AT = "sync_at"

    const val RECENT_WINDOW_MS = 10 * 60 * 1000L

    fun isSyncable(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val scheme = Uri.parse(url).scheme?.lowercase() ?: return false
        return scheme == "http" || scheme == "https"
    }

    fun publish(context: Context, url: String, displayId: Int) {
        if (!isSyncable(url)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, url)
            .putInt(KEY_DISPLAY, displayId)
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
        context.sendBroadcast(
            Intent(ACTION).setPackage(context.packageName).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_DISPLAY, displayId)
            },
        )
    }

    fun recentFromOtherDisplay(context: Context, displayId: Int): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_URL, null) ?: return null
        if (!isSyncable(url)) return null
        if (prefs.getInt(KEY_DISPLAY, displayId) == displayId) return null
        val at = prefs.getLong(KEY_AT, 0L)
        if (System.currentTimeMillis() - at > RECENT_WINDOW_MS) return null
        return url
    }
}
