package io.github.venompool888.fluidcapsule.publisher

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import io.github.venompool888.fluidcapsule.R
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

    fun publishCopiedFeedback(context: Context) {
        NotificationFactory.ensureChannels(context)
        val notification = Notification.Builder(context, NotificationFactory.CAPSULE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_capsule)
            .setContentTitle("验证码已复制")
            .setContentText("可以直接粘贴")
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(1_500L)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NotificationFactory.CAPSULE_NOTIFICATION_ID, notification)
    }
}
