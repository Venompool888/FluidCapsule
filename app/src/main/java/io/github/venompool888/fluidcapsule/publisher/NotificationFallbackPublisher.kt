package io.github.venompool888.fluidcapsule.publisher

import android.app.NotificationManager
import android.content.Context
import io.github.venompool888.fluidcapsule.core.CapsuleEvent

object NotificationFallbackPublisher : CapsulePublisher {
    override fun publish(context: Context, event: CapsuleEvent): PublishResult {
        NotificationFactory.ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(
            NotificationFactory.CAPSULE_NOTIFICATION_ID,
            NotificationFactory.baseBuilder(context, event).build(),
        )
        return PublishResult("NOTIFICATION_FALLBACK", "posted")
    }
}
