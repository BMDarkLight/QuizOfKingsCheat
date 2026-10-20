package dev.qokhelper

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Holds the screen capture and the floating button for as long as the helper is running. */
class CaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var projection: MediaProjection? = null
    private var grabber: ScreenGrabber? = null
    private var overlay: OverlayController? = null
    private var engine: Answerer? = null
    private var inFlight: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || projection != null) {
            if (intent?.action == ACTION_STOP) stopSelf()
            return START_NOT_STICKY
        }

        // Android 14+ requires the foreground service to be running before getMediaProjection().
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val prefs = Prefs(this)
        engine = prefs.provider.let { it.create(prefs.settings(it), prefs.webSearch) }

        val mp = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
        if (mp == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = stopSelf()
        }, Handler(Looper.getMainLooper()))
        projection = mp
        grabber = ScreenGrabber(this, mp)
        overlay = OverlayController(this, ::ask).also { it.show() }
        return START_NOT_STICKY
    }

    private fun ask() {
        if (inFlight?.isActive == true) return
        val overlay = overlay ?: return
        inFlight = scope.launch {
            overlay.showLoading()
            // Let a frame without the answer panel reach the grabber.
            delay(150)
            val answer = withContext(Dispatchers.IO) {
                val jpeg = grabber?.captureJpegBase64()
                    ?: return@withContext Answer.Failed("No screen frame yet, try again")
                engine?.answer(jpeg) ?: Answer.Failed("Not ready")
            }
            overlay.showAnswer(answer)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        overlay?.remove()
        grabber?.release()
        projection?.stop()
        overlay = null
        grabber = null
        projection = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "QoK Helper", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("QoK Helper is running")
            .setContentText("Tap the floating ? during a question")
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "helper"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val ACTION_STOP = "dev.qokhelper.STOP"

        fun start(context: Context, resultCode: Int, data: Intent) {
            context.startForegroundService(
                Intent(context, CaptureService::class.java)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_DATA, data)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CaptureService::class.java))
        }
    }
}
