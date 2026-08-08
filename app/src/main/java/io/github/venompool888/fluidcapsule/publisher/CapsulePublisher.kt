package io.github.venompool888.fluidcapsule.publisher

import android.content.Context
import io.github.venompool888.fluidcapsule.core.CapsuleEvent

data class PublishResult(
    val publisher: String,
    val detail: String,
)

interface CapsulePublisher {
    fun publish(context: Context, event: CapsuleEvent): PublishResult
}
