package io.github.venompool888.fluidcapsule.publisher

import android.content.Context
import android.os.Build
import io.github.venompool888.fluidcapsule.core.CapsuleEvent
import io.github.venompool888.fluidcapsule.diagnostics.DiagnosticsStore

object PublisherRouter {
    fun publish(context: Context, event: CapsuleEvent) {
        CapsuleCoordinator.submit(context, event)
    }

    internal fun publishDirect(context: Context, event: CapsuleEvent): PublishResult {
        val result = if (Build.VERSION.SDK_INT >= 36) {
            AospLiveUpdatePublisher.publish(context, event)
        } else {
            NotificationFallbackPublisher.publish(context, event)
        }
        DiagnosticsStore.markPublish(context, result.publisher, result.detail)
        return result
    }
}
