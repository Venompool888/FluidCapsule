package io.github.venompool888.fluidcapsule.notification

import android.app.Notification
import android.os.Build
import android.os.Parcelable
import android.service.notification.StatusBarNotification

object NotificationNormalizer {
    fun normalize(sbn: StatusBarNotification): NormalizedNotification {
        val notification = sbn.notification
        val extras = notification.extras
        val parts = linkedSetOf<String>()

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val regularText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it.toString().trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        add(parts, title)
        add(parts, regularText)
        add(parts, bigText)
        textLines.forEach { add(parts, it) }

        val messageBundles = if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }
        val messageObjects = if (messageBundles != null) {
            @Suppress("DEPRECATION")
            Notification.MessagingStyle.Message
                .getMessagesFromBundleArray(messageBundles as Array<Parcelable>)
        } else {
            emptyList()
        }
        val messages = messageObjects.mapNotNull {
            it.text?.toString()?.trim()?.takeIf(String::isNotEmpty)
        }
        messages.forEach { add(parts, it) }
        val senderIcon = if (Build.VERSION.SDK_INT >= 28) {
            messageObjects.asReversed().firstNotNullOfOrNull { it.senderPerson?.icon }
        } else {
            null
        }

        val primaryText = messages.lastOrNull()
            ?: bigText.takeIf { it.isNotEmpty() }
            ?: textLines.lastOrNull()
            ?: regularText

        return NormalizedNotification(
            packageName = sbn.packageName,
            notificationKey = sbn.key,
            title = title,
            primaryText = primaryText,
            combinedText = parts.joinToString("\n"),
            postedAtMillis = sbn.postTime,
            contentIntent = notification.contentIntent,
            smallIcon = notification.smallIcon,
            largeIcon = notification.getLargeIcon(),
            senderIcon = senderIcon,
            actions = notification.actions?.toList().orEmpty(),
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = sbn.isOngoing,
            channelId = notification.channelId.orEmpty(),
        )
    }

    private fun add(parts: MutableSet<String>, value: CharSequence?) {
        val normalized = value?.toString()?.trim().orEmpty()
        if (normalized.isNotEmpty()) parts += normalized
    }
}
