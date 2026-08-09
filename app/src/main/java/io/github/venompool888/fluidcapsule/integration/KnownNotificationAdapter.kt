package io.github.venompool888.fluidcapsule.integration

import android.content.Context
import io.github.venompool888.fluidcapsule.core.CapsuleAction
import io.github.venompool888.fluidcapsule.core.CapsuleEvent
import io.github.venompool888.fluidcapsule.core.CapsuleKind
import io.github.venompool888.fluidcapsule.core.CapsulePrivacy
import io.github.venompool888.fluidcapsule.notification.NormalizedNotification

object KnownNotificationAdapter {
    fun adapt(
        context: Context,
        notification: NormalizedNotification,
        showContent: Boolean,
        now: Long,
    ): KnownNotificationDecision? {
        if (notification.packageName == SpeedtestTextParser.SPEEDTEST_PACKAGE) {
            return adaptCompletedSpeedtest(notification, showContent, now)
        }
        val stage = NotificationStageClassifier.classify(
            notification.packageName,
            notification.combinedText,
        ) ?: return null
        if (stage.suppress) return KnownNotificationDecision.Suppress

        val appLabel = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(notification.packageName, 0),
            ).toString()
        }.getOrDefault(notification.packageName)
        val ttl = when {
            stage.terminal -> 60_000L
            notification.packageName == NotificationStageClassifier.LOCALSEND_PACKAGE -> 5 * 60_000L
            else -> 20 * 60_000L
        }
        val title = when (notification.packageName) {
            NotificationStageClassifier.LOCALSEND_PACKAGE -> "LocalSend"
            NotificationStageClassifier.MEITUAN_PACKAGE -> "美团订单"
            else -> appLabel
        }
        val body = notification.primaryText.trim()
            .ifEmpty { notification.combinedText.lineSequence().firstOrNull().orEmpty() }
            .take(220)
            .ifEmpty { stage.label }

        return KnownNotificationDecision.Publish(
            CapsuleEvent(
                sourcePackage = notification.packageName,
                sourceLabel = appLabel,
                sourceSmallIcon = notification.smallIcon,
                sourceLargeIcon = notification.largeIcon ?: notification.senderIcon,
                eventId = notification.notificationKey,
                kind = CapsuleKind.CUSTOM,
                title = title,
                shortText = stage.label.take(12),
                body = body,
                action = notification.contentIntent?.let(CapsuleAction::OpenOriginal) ?: CapsuleAction.None,
                privacy = if (showContent) CapsulePrivacy.SHOW_FULL else CapsulePrivacy.HIDE_SENSITIVE,
                createdAtMillis = now,
                expiresAtMillis = now + ttl,
                dedupeKey = notification.notificationKey,
                progress = stage.progress,
                progressIndeterminate = stage.indeterminate,
            ),
        )
    }

    private fun adaptCompletedSpeedtest(
        notification: NormalizedNotification,
        showContent: Boolean,
        now: Long,
    ): KnownNotificationDecision? {
        val snapshot = SpeedtestTextParser.parse(notification.combinedText.lines()) ?: return null
        if (snapshot.downloadMbps == null && snapshot.uploadMbps == null) return null
        return KnownNotificationDecision.Publish(
            CapsuleEvent(
                sourcePackage = notification.packageName,
                sourceLabel = "Speedtest",
                sourceSmallIcon = notification.smallIcon,
                sourceLargeIcon = notification.largeIcon,
                eventId = notification.notificationKey,
                kind = CapsuleKind.NOTIFICATION,
                title = notification.title.ifBlank { "Test Complete" },
                shortText = snapshot.completedNotificationShortText(),
                body = snapshot.completedNotificationBody(),
                action = notification.contentIntent?.let(CapsuleAction::OpenOriginal) ?: CapsuleAction.None,
                privacy = if (showContent) CapsulePrivacy.SHOW_FULL else CapsulePrivacy.HIDE_SENSITIVE,
                createdAtMillis = now,
                expiresAtMillis = now + 5 * 60_000L,
                dedupeKey = notification.notificationKey,
            ),
        )
    }
}

sealed interface KnownNotificationDecision {
    data class Publish(val event: CapsuleEvent) : KnownNotificationDecision
    data object Suppress : KnownNotificationDecision
}
