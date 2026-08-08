package io.github.venompool888.fluidcapsule.keepalive

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.venompool888.fluidcapsule.R
import io.github.venompool888.fluidcapsule.publisher.NotificationFactory

class KeepAliveService : Service() {
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
        startForeground(NotificationFactory.KEEP_ALIVE_NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null
}
