package io.github.venompool888.fluidcapsule.keepalive

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import io.github.venompool888.fluidcapsule.R
import io.github.venompool888.fluidcapsule.publisher.NotificationFactory

class KeepAliveService : Service() {
    private var startRefused = false

    override fun onCreate() {
        super.onCreate()
        NotificationFactory.ensureChannels(this)
        val notification = Notification.Builder(this, NotificationFactory.KEEP_ALIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_capsule)
            .setContentTitle("流体胶囊正在运行")
            .setContentText("监听通知并维护胶囊更新")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            startForeground(NotificationFactory.KEEP_ALIVE_NOTIFICATION_ID, notification)
        } catch (error: IllegalStateException) {
            // Android 12+ refuses foreground starts from the background (e.g. a sticky restart).
            // Stop instead of crashing the process; opening the app or a reboot starts it again.
            Log.w(TAG, "foreground start refused type=${error.javaClass.simpleName}")
            startRefused = true
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (startRefused) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FluidCapsuleKeepAlive"
    }
}
