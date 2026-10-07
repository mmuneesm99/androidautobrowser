package com.androidautobrowser.browser.mirror

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface

/**
 * View-only mirror of the phone's default display onto a surface owned by the car activity.
 * Taps on the car screen are not sent back to the phone.
 */
object MirrorController {
    private const val TAG = "MirrorController"
    private const val MAX_EDGE = 1280

    enum class State { IDLE, ACTIVE, DENIED }

    @Volatile
    var state: State = State.IDLE
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<(State) -> Unit>()

    private var appContext: Context? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var consumer: Surface? = null
    private var width = 0
    private var height = 0
    private var density = 160

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            handler.post {
                if (projection == null) return@post
                projection = null
                virtualDisplay?.release()
                virtualDisplay = null
                setState(State.IDLE)
            }
        }
    }

    fun addListener(listener: (State) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (State) -> Unit) {
        listeners.remove(listener)
    }

    fun onDenied() {
        setState(State.DENIED)
    }

    fun setProjection(context: Context, mediaProjection: MediaProjection) {
        appContext = context.applicationContext
        releaseCapture(notify = false)
        projection = mediaProjection
        mediaProjection.registerCallback(projectionCallback, handler)
        setState(State.ACTIVE)
        tryStart()
    }

    fun setConsumer(surface: Surface, width: Int, height: Int, densityDpi: Int) {
        val (w, h) = fitSize(width, height)
        val same = consumer == surface && this.width == w && this.height == h && density == densityDpi
        consumer = surface
        this.width = w
        this.height = h
        density = densityDpi.coerceAtLeast(1)
        if (!same) tryStart()
    }

    fun clearConsumer(surface: Surface) {
        if (consumer != surface) return
        virtualDisplay?.release()
        virtualDisplay = null
        consumer = null
    }

    fun stop() {
        releaseCapture(notify = true)
    }

    fun fitSize(width: Int, height: Int): Pair<Int, Int> {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val longEdge = maxOf(safeW, safeH)
        if (longEdge <= MAX_EDGE) return safeW to safeH
        val scale = MAX_EDGE.toFloat() / longEdge
        return (safeW * scale).toInt().coerceAtLeast(1) to (safeH * scale).toInt().coerceAtLeast(1)
    }

    private fun tryStart() {
        val projection = projection ?: return
        val surface = consumer ?: return
        if (!surface.isValid || width < 16 || height < 16) return
        virtualDisplay?.release()
        virtualDisplay = null
        try {
            virtualDisplay = projection.createVirtualDisplay(
                "MyBrowserMirror",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                handler,
            )
        } catch (error: RuntimeException) {
            Log.e(TAG, "Could not start screen mirror", error)
            stop()
        }
    }

    private fun releaseCapture(notify: Boolean) {
        virtualDisplay?.release()
        virtualDisplay = null
        val current = projection
        projection = null
        if (current != null) {
            try {
                current.unregisterCallback(projectionCallback)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Projection callback already removed", error)
            }
            try {
                current.stop()
            } catch (error: RuntimeException) {
                Log.w(TAG, "Projection already stopped", error)
            }
        }
        if (notify && state != State.IDLE) setState(State.IDLE)
    }

    private fun setState(next: State) {
        state = next
        val snapshot = listeners.toList()
        snapshot.forEach { listener -> listener(next) }
        val context = appContext ?: return
        context.sendBroadcast(
            Intent(ScreenMirrorService.ACTION_STATE).setPackage(context.packageName).apply {
                putExtra(ScreenMirrorService.EXTRA_STATE, next.name)
            },
        )
    }
}
