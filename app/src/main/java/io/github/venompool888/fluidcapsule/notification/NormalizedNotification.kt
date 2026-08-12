package io.github.venompool888.fluidcapsule.notification

import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.app.Notification

data class NormalizedNotification(
    val packageName: String,
    val notificationKey: String,
    val title: String,
    val primaryText: String,
    val messageTexts: List<String>,
    val combinedText: String,
    val postedAtMillis: Long,
    val contentIntent: PendingIntent?,
    val smallIcon: Icon?,
    val largeIcon: Icon?,
    val senderIcon: Icon?,
    val actions: List<Notification.Action>,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
    val channelId: String,
)
