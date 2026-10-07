package com.androidautobrowser.browser.mirror

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.androidautobrowser.browser.R

/**
 * Holds the screen-capture grant. The car activity supplies the surface that shows the frames.
 */
class ScreenMirrorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            MirrorController.stop()
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Mirror service was not in the foreground", error)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data = intent?.captureData()
        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = try {
            manager.getMediaProjection(resultCode, data)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Screen capture was not granted", error)
            null
        }
        if (projection == null) {
            MirrorController.onDenied()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        MirrorController.setProjection(this, projection)
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        MirrorController.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (MirrorController.state == MirrorController.State.ACTIVE) {
            MirrorController.stop()
        }
        super.onDestroy()
    }

    private fun Intent.captureData(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_DATA)
        }
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.mirror_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, ScreenMirrorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mirror)
            .setContentTitle(getString(R.string.mirror_notification_title))
            .setContentText(getString(R.string.mirror_notification_text))
            .setOngoing(true)
            .addAction(0, getString(R.string.action_mirror_stop), stop)
            .build()
    }

    companion object {
        private const val TAG = "ScreenMirrorService"
        const val ACTION_STOP = "com.androidautobrowser.browser.action.MIRROR_STOP"
        const val ACTION_STATE = "com.androidautobrowser.browser.action.MIRROR_STATE"
        const val EXTRA_STATE = "state"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "result_data"
        private const val CHANNEL_ID = "screen_mirror"
        private const val NOTIFICATION_ID = 42

        fun createConsentIntent(context: Context): Intent {
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            } else {
                manager.createScreenCaptureIntent()
            }
        }

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenMirrorService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenMirrorService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
