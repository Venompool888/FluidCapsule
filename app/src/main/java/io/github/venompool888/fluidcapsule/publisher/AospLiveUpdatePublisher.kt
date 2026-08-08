package io.github.venompool888.fluidcapsule.publisher

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import io.github.venompool888.fluidcapsule.core.CapsuleEvent
import io.github.venompool888.fluidcapsule.core.CapsuleKind
import io.github.venompool888.fluidcapsule.core.CapsulePrivacy

object AospLiveUpdatePublisher : CapsulePublisher {
    override fun publish(context: Context, event: CapsuleEvent): PublishResult {
        NotificationFactory.ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val builder = NotificationFactory.baseBuilder(context, event)
            .setShortCriticalText(
                if (event.privacy == CapsulePrivacy.SHOW_FULL ||
                    event.kind != io.github.venompool888.fluidcapsule.core.CapsuleKind.OTP
                ) {
                    event.shortText
                } else {
                    "验证码"
                },
            )

        if (event.kind != CapsuleKind.NOTIFICATION) {
            builder.setProgress(100, 100, false)
                .setStyle(
                Notification.ProgressStyle()
                    .setStyledByProgress(false)
                    .setProgress(100),
            )
        }
        val notification = builder
            .addExtras(android.os.Bundle().apply {
                putBoolean("android.requestPromotedOngoing", true)
            })
            .build()

        manager.notify(NotificationFactory.CAPSULE_NOTIFICATION_ID, notification)
        return PublishResult(
            publisher = "AOSP_LIVE_UPDATE",
            detail = "promotable=${notification.hasPromotableCharacteristics()}, allowed=${manager.canPostPromotedNotifications()}",
        )
    }
}
