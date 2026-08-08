package io.github.venompool888.fluidcapsule.core

import android.graphics.drawable.Icon
import android.app.Notification

data class CapsuleEvent(
    val sourcePackage: String,
    val sourceLabel: String? = null,
    val sourceSmallIcon: Icon? = null,
    val sourceLargeIcon: Icon? = null,
    val sourceActions: List<Notification.Action> = emptyList(),
    val smartReplies: List<String> = emptyList(),
    val eventId: String,
    val kind: CapsuleKind,
    val title: String,
    val shortText: String,
    val body: String,
    val action: CapsuleAction,
    val privacy: CapsulePrivacy,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val dedupeKey: String,
)

enum class CapsuleKind {
    OTP,
    NOTIFICATION,
    CUSTOM,
}

enum class CapsulePrivacy {
    SHOW_FULL,
    HIDE_SENSITIVE,
}

sealed interface CapsuleAction {
    data class CopySensitiveText(val value: String) : CapsuleAction
    data class OpenOriginal(val pendingIntent: android.app.PendingIntent) : CapsuleAction
    data object None : CapsuleAction
}
