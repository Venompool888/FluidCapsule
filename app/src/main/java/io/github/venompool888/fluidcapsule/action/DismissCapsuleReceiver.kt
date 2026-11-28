package io.github.venompool888.fluidcapsule.action

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.venompool888.fluidcapsule.notification.CapsuleNotificationListenerService
import io.github.venompool888.fluidcapsule.publisher.CapsuleCoordinator

class DismissCapsuleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISS_CAPSULE) return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        // A capsule that simply ran out of time leaves the original notification alone;
        // only the user's own swipe or 关闭 also clears it at the source.
        if (!isTimeout(intent.getLongExtra(EXTRA_TIMES_OUT_AT, 0L), System.currentTimeMillis())) {
            CapsuleNotificationListenerService.requestSourceNotificationDismissal(context, eventId)
        }
        CapsuleCoordinator.consume(context, eventId)
    }

    companion object {
        const val ACTION_DISMISS_CAPSULE =
            "io.github.venompool888.fluidcapsule.action.DISMISS_CAPSULE"
        const val EXTRA_EVENT_ID = "event_id"
        /** Set only on the delete intent: when Android will time the capsule out. */
        const val EXTRA_TIMES_OUT_AT = "times_out_at"

        /** Delivery can lag the timeout slightly, so anything this close to it counts as one. */
        private const val TIMEOUT_GRACE_MILLIS = 1_500L

        fun isTimeout(timesOutAtMillis: Long, nowMillis: Long): Boolean =
            timesOutAtMillis > 0L && nowMillis >= timesOutAtMillis - TIMEOUT_GRACE_MILLIS
    }
}
