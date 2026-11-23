package io.github.venompool888.fluidcapsule.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import io.github.venompool888.fluidcapsule.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SyncRealtimeService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    @Volatile private var realtimeConnected = false

    override fun onCreate() {
        super.onCreate()
        activeService = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "短信云同步", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        startForeground(NOTIFICATION_ID, notification("短信云同步已连接"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!shouldRun(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (running.compareAndSet(false, true)) worker.execute { runLoop() }
        return START_STICKY
    }

    private fun runLoop() {
        var retryMs = 1_000L
        try {
            while (running.get() && !Thread.currentThread().isInterrupted && shouldRun(this)) {
                try {
                    SyncEngine.executor.submit<Int> { SyncEngine.pull(this) }.get()
                    if (!shouldRun(this)) break
                    realtimeConnected = true
                    updateNotification("短信云同步已连接")
                    SyncApi.forConfig(this, SyncConfig(this)).waitForMessages()
                    retryMs = 1_000L
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (error: Exception) {
                    if (!running.get() || !shouldRun(this)) break
                    realtimeConnected = false
                    val cause = error.cause ?: error
                    SyncConfig(this).recordError(cause.message ?: cause.javaClass.simpleName)
                    updateNotification("短信云同步重连中")
                    if (!pauseBeforeRetry(retryMs)) break
                    retryMs = (retryMs * 2).coerceAtMost(60_000L)
                }
            }
        } finally {
            realtimeConnected = false
            running.set(false)
            stopSelf()
        }
    }

    private fun pauseBeforeRetry(delayMs: Long): Boolean = try {
        Thread.sleep(delayMs)
        true
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private fun notification(status: String): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_capsule)
        .setContentTitle(status)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, SyncSettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).setOnlyAlertOnce(true).build()

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(status))
    }

    override fun onDestroy() {
        running.set(false)
        realtimeConnected = false
        if (activeService === this) activeService = null
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "sms_sync_connection"
        private const val NOTIFICATION_ID = 0xFC551
        @Volatile private var activeService: SyncRealtimeService? = null
        val connected: Boolean get() = activeService?.realtimeConnected == true

        private fun shouldRun(context: Context): Boolean = SyncConfig(context).let { it.isConnected && it.receiveFrom.isNotEmpty() }

        fun update(context: Context) {
            val intent = Intent(context, SyncRealtimeService::class.java)
            if (shouldRun(context)) context.startForegroundService(intent) else context.stopService(intent)
        }
    }
}
