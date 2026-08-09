package io.github.venompool888.fluidcapsule.publisher

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.venompool888.fluidcapsule.core.CapsuleEvent
import io.github.venompool888.fluidcapsule.settings.UserSettings

object CapsuleCoordinator {
    private val queue = CapsuleQueueReducer(maxSize = 8)
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var applicationContext: Context? = null
    private var visibleEventId: String? = null
    private var visibleUntilMillis = 0L
    private val deadlineRunnable = Runnable { handleDeadline() }

    @Synchronized
    fun submit(context: Context, event: CapsuleEvent) {
        applicationContext = context.applicationContext
        val now = System.currentTimeMillis()
        val winner = queue.submit(event, now)
        val submittedIsWinner = winner?.eventId == event.eventId
        updateVisible(winner, now, resetWindow = submittedIsWinner)
        renderAndSchedule(now)
    }

    @Synchronized
    fun consume(context: Context, eventId: String?) {
        applicationContext = context.applicationContext
        val targetEventId = eventId?.takeIf(String::isNotBlank) ?: visibleEventId
        if (targetEventId == null) {
            cancelCapsule(context)
            return
        }
        val now = System.currentTimeMillis()
        val winner = queue.removeEvent(targetEventId, now)
        updateVisible(winner, now, resetWindow = true)
        renderAndSchedule(now)
    }

    @Synchronized
    fun removeSourceEvent(context: Context, eventId: String) {
        applicationContext = context.applicationContext
        val now = System.currentTimeMillis()
        val previousVisible = visibleEventId
        val winner = queue.removeEvent(eventId, now)
        updateVisible(winner, now, resetWindow = previousVisible == eventId)
        renderAndSchedule(now)
    }

    @Synchronized
    private fun handleDeadline() {
        val context = applicationContext ?: return
        val now = System.currentTimeMillis()
        var winner = queue.current(now)
        if (visibleEventId != null && now >= visibleUntilMillis) {
            winner = queue.removeEvent(visibleEventId.orEmpty(), now)
            updateVisible(winner, now, resetWindow = true)
        } else if (winner?.eventId != visibleEventId) {
            updateVisible(winner, now, resetWindow = true)
        }
        renderAndSchedule(now)
    }

    private fun updateVisible(winner: CapsuleEvent?, now: Long, resetWindow: Boolean) {
        val changed = winner?.eventId != visibleEventId
        visibleEventId = winner?.eventId
        if (winner == null) {
            visibleUntilMillis = 0L
        } else if (changed || resetWindow) {
            val context = applicationContext ?: return
            visibleUntilMillis = minOf(
                winner.expiresAtMillis,
                now + UserSettings.capsuleDisplayDurationMillis(context),
            )
        }
    }

    private fun renderAndSchedule(now: Long) {
        val context = applicationContext ?: return
        handler.removeCallbacks(deadlineRunnable)
        val winner = queue.current(now)
        if (winner == null) {
            visibleEventId = null
            visibleUntilMillis = 0L
            cancelCapsule(context)
            return
        }
        if (winner.eventId != visibleEventId) {
            updateVisible(winner, now, resetWindow = true)
        }
        PublisherRouter.publishDirect(
            context,
            winner.copy(expiresAtMillis = minOf(winner.expiresAtMillis, visibleUntilMillis)),
        )
        val nextDeadline = listOfNotNull(
            queue.nextExpiry(now),
            visibleUntilMillis.takeIf { it > 0L },
        ).minOrNull() ?: return
        handler.postDelayed(deadlineRunnable, (nextDeadline - now).coerceAtLeast(1L))
    }

    private fun cancelCapsule(context: Context) {
        handler.removeCallbacks(deadlineRunnable)
        context.getSystemService(NotificationManager::class.java)
            .cancel(NotificationFactory.CAPSULE_NOTIFICATION_ID)
    }
}
