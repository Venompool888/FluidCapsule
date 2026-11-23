package io.github.venompool888.fluidcapsule.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import io.github.venompool888.fluidcapsule.R
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal const val SYNC_LOG_TAG = "FcSync"

class SyncRealtimeService : Service() {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "FcSync-realtime") }
    private val waitWatchdog = ScheduledThreadPoolExecutor(1) { Thread(it, "FcSync-watchdog") }.apply {
        removeOnCancelPolicy = true
    }
    private val running = AtomicBoolean(false)
    private val realtimeStateLock = Any()
    @Volatile private var realtimeConnected = false

    override fun onCreate() {
        super.onCreate()
        activeService = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "短信云同步", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        startForeground(NOTIFICATION_ID, notification("短信云同步连接中"))
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
        val config = SyncConfig(this)
        val deviceId = config.deviceId
        var retryMs = 1_000L
        try {
            while (!Thread.currentThread().isInterrupted && isCurrentConnection(config, deviceId) && config.isConnected) {
                Log.d(SYNC_LOG_TAG, "loop iteration start")
                try {
                    SyncEngine.executor.submit(Runnable {
                        if (!isCurrentConnection(config, deviceId)) return@Runnable
                        SyncEngine.pull(this)
                        // Publish on the pull queue before a newer manual or CLI pull can fail.
                        updateRealtimeState(true, "realtime pull succeeded")
                    }).get()
                    if (!isCurrentConnection(config, deviceId)) break
                    waitForMessages(config, deviceId)
                    retryMs = 1_000L
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (error: Exception) {
                    if (!isCurrentConnection(config, deviceId)) break
                    Log.d(SYNC_LOG_TAG, "retry delay ${retryMs}ms")
                    if (!pauseBeforeRetry(retryMs)) break
                    retryMs = (retryMs * 2).coerceAtMost(60_000L)
                }
            }
        } finally {
            running.set(false)
            updateRealtimeState(false, "loop stopped")
            stopSelf()
        }
    }

    private fun waitForMessages(config: SyncConfig, deviceId: String) {
        val waitLock = Any()
        var finished = false
        var deadline: ScheduledFuture<*>? = null
        val timeout = SocketTimeoutException("实时连接等待超时")

        fun finishWait(report: () -> Unit): Boolean = synchronized(waitLock) {
            if (finished) return@synchronized false
            finished = true
            report()
            true
        }

        Log.d(SYNC_LOG_TAG, "wait start")
        try {
            val available = SyncApi.forConfig(this, config).waitForMessages { connection ->
                // An HTTP read timeout applies to individual reads, not the entire long poll.
                deadline = waitWatchdog.schedule({
                    if (finishWait { reportWaitFailure(config, deviceId, timeout) }) connection.disconnect()
                }, 15, TimeUnit.SECONDS)
            }
            if (!finishWait { Log.d(SYNC_LOG_TAG, "wait end available=$available") }) throw timeout
        } catch (error: Exception) {
            finishWait { reportWaitFailure(config, deviceId, error) }
            throw error
        } finally {
            deadline?.cancel(false)
        }
    }

    private fun reportWaitFailure(config: SyncConfig, deviceId: String, error: Exception) {
        Log.w(SYNC_LOG_TAG, "wait failure type=${error.javaClass.simpleName}")
        if (!isCurrentConnection(config, deviceId)) return
        config.recordRealtimeError(deviceId, error.message ?: error.javaClass.simpleName)
        updateRealtimeState(false, "wait failed")
    }

    private fun updateRealtimeState(connected: Boolean, reason: String) {
        synchronized(realtimeStateLock) {
            val active = running.get() && activeService === this
            if (connected && !active) return
            val changed = realtimeConnected != connected
            if (changed) {
                realtimeConnected = connected
                Log.d(SYNC_LOG_TAG, "realtimeConnected=$connected ($reason)")
            }
            if (active && (changed || !connected)) {
                updateNotification(if (connected) "短信云同步已连接" else "短信云同步重连中")
            }
        }
    }

    private fun isCurrentConnection(config: SyncConfig, deviceId: String): Boolean =
        running.get() && activeService === this && config.deviceId == deviceId

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
        updateRealtimeState(false, "service destroyed")
        if (activeService === this) activeService = null
        worker.shutdownNow()
        waitWatchdog.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "sms_sync_connection"
        private const val NOTIFICATION_ID = 0xFC551
        @Volatile private var activeService: SyncRealtimeService? = null
        val connected: Boolean get() = activeService?.realtimeConnected == true

        internal fun onPullFailure() {
            activeService?.updateRealtimeState(false, "pull failed")
        }

        private fun shouldRun(context: Context): Boolean = SyncConfig(context).isConnected

        fun update(context: Context) {
            val intent = Intent(context, SyncRealtimeService::class.java)
            if (shouldRun(context)) context.startForegroundService(intent) else context.stopService(intent)
        }
    }
}
