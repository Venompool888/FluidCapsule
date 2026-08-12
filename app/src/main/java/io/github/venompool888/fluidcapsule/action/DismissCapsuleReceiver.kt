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
        CapsuleNotificationListenerService.requestSourceNotificationDismissal(context, eventId)
        CapsuleCoordinator.consume(context, eventId)
    }

    companion object {
        const val ACTION_DISMISS_CAPSULE =
            "io.github.venompool888.fluidcapsule.action.DISMISS_CAPSULE"
        const val EXTRA_EVENT_ID = "event_id"
    }
}
