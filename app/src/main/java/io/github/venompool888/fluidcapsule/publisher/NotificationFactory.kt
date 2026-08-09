package io.github.venompool888.fluidcapsule.publisher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import io.github.venompool888.fluidcapsule.R
import io.github.venompool888.fluidcapsule.action.CopyOtpReceiver
import io.github.venompool888.fluidcapsule.action.ForwardNotificationActionReceiver
import io.github.venompool888.fluidcapsule.action.OpenOriginalActivity
import io.github.venompool888.fluidcapsule.action.ReplyActivity
import io.github.venompool888.fluidcapsule.core.CapsuleAction
import io.github.venompool888.fluidcapsule.core.CapsuleEvent
import io.github.venompool888.fluidcapsule.core.CapsuleKind
import io.github.venompool888.fluidcapsule.core.CapsulePrivacy

internal object NotificationFactory {
    const val CAPSULE_CHANNEL_ID = "capsule_events"
    const val KEEP_ALIVE_CHANNEL_ID = "keep_alive"
    const val CAPSULE_NOTIFICATION_ID = 2001
    const val KEEP_ALIVE_NOTIFICATION_ID = 1001

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CAPSULE_CHANNEL_ID,
                "流体胶囊事件",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "验证码与其他实时胶囊事件"
                setSound(null, null)
                enableVibration(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                KEEP_ALIVE_CHANNEL_ID,
                "后台保活",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "维持用户启用的通知监听与胶囊更新"
            },
        )
    }

    fun baseBuilder(context: Context, event: CapsuleEvent): Notification.Builder {
        val showFull = event.privacy == CapsulePrivacy.SHOW_FULL
        val clickIntent = when (val action = event.action) {
            is CapsuleAction.CopySensitiveText -> PendingIntent.getBroadcast(
                context,
                event.eventId.hashCode(),
                Intent(context, CopyOtpReceiver::class.java)
                    .setAction(CopyOtpReceiver.ACTION_COPY_OTP)
                    .putExtra(CopyOtpReceiver.EXTRA_OTP, action.value),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            is CapsuleAction.OpenOriginal -> PendingIntent.getActivity(
                context,
                event.eventId.hashCode(),
                Intent(context, OpenOriginalActivity::class.java)
                    .setAction(OpenOriginalActivity.ACTION_OPEN_ORIGINAL)
                    .putExtra(OpenOriginalActivity.EXTRA_ORIGINAL_INTENT, action.pendingIntent)
                    .putExtra(OpenOriginalActivity.EXTRA_SOURCE_PACKAGE, event.sourcePackage),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            CapsuleAction.None -> null
        }

        val contentText = if (showFull && event.action is CapsuleAction.CopySensitiveText) {
            "${event.shortText} · ${event.body}"
        } else {
            event.body
        }

        val subText = when (event.action) {
            is CapsuleAction.CopySensitiveText -> "点击复制"
            is CapsuleAction.OpenOriginal -> event.sourceLabel
            CapsuleAction.None -> null
        }

        val builder = Notification.Builder(context, CAPSULE_CHANNEL_ID)
            .setContentTitle(event.title)
            .setContentText(contentText)
            .setSubText(subText)
            .setContentIntent(clickIntent)
            .setCategory(
                if (event.kind == CapsuleKind.NOTIFICATION) {
                    Notification.CATEGORY_MESSAGE
                } else {
                    Notification.CATEGORY_STATUS
                },
            )
            .setVisibility(
                if (showFull) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE,
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAllowSystemGeneratedContextualActions(true)
            .setShowWhen(false)
            .setTimeoutAfter((event.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(1_000L))

        event.sourceSmallIcon?.let(builder::setSmallIcon) ?: builder.setSmallIcon(R.drawable.ic_capsule)
        event.sourceLargeIcon?.let(builder::setLargeIcon)
        event.progress?.let { progress ->
            val maximum = event.progressMax.coerceAtLeast(1)
            builder.setProgress(
                maximum,
                progress.coerceIn(0, maximum),
                event.progressIndeterminate,
            )
        }
        if (event.kind == CapsuleKind.NOTIFICATION) {
            addForwardedActions(context, builder, event)
        }

        if (!showFull) {
            val publicTitle = if (event.kind == io.github.venompool888.fluidcapsule.core.CapsuleKind.OTP) {
                "收到验证码"
            } else {
                "${event.title} 有新通知"
            }
            builder.setPublicVersion(
                Notification.Builder(context, CAPSULE_CHANNEL_ID)
                    .also {
                        event.sourceSmallIcon?.let(it::setSmallIcon)
                            ?: it.setSmallIcon(R.drawable.ic_capsule)
                    }
                    .setContentTitle(publicTitle)
                    .setContentText("解锁后查看")
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .build(),
            )
        }
        return builder
    }

    private fun addForwardedActions(
        context: Context,
        builder: Notification.Builder,
        event: CapsuleEvent,
    ) {
        var count = 0
        event.sourceActions.forEach { sourceAction ->
            if (count >= MAX_VISIBLE_ACTIONS) return@forEach
            val isReply = sourceAction.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY ||
                !sourceAction.remoteInputs.isNullOrEmpty()
            builder.addAction(
                if (isReply) {
                    replyActivityAction(context, event, sourceAction)
                } else {
                    forwardedAction(context, event, sourceAction)
                },
            )
            count++
        }
    }

    private fun forwardedAction(
        context: Context,
        event: CapsuleEvent,
        sourceAction: Notification.Action,
    ): Notification.Action {
        val wrapperIntent = Intent(context, ForwardNotificationActionReceiver::class.java)
            .setAction(ForwardNotificationActionReceiver.ACTION_FORWARD_NOTIFICATION_ACTION)
            .putExtra(ForwardNotificationActionReceiver.EXTRA_SOURCE_ACTION, sourceAction)
        val requestKey = "${event.eventId}:${sourceAction.semanticAction}:${sourceAction.title}"
        val wrapper = PendingIntent.getBroadcast(
            context,
            requestKey.hashCode(),
            wrapperIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val actionBuilder = Notification.Action.Builder(
            sourceAction.getIcon() ?: Icon.createWithResource(context, R.drawable.ic_capsule),
            sourceAction.title,
            wrapper,
        )
            .setAllowGeneratedReplies(sourceAction.allowGeneratedReplies)
            .setSemanticAction(sourceAction.semanticAction)
        sourceAction.remoteInputs.orEmpty().forEach(actionBuilder::addRemoteInput)
        sourceAction.dataOnlyRemoteInputs.orEmpty().forEach(actionBuilder::addRemoteInput)
        if (Build.VERSION.SDK_INT >= 29) {
            actionBuilder.setContextual(sourceAction.isContextual)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            actionBuilder.setAuthenticationRequired(sourceAction.isAuthenticationRequired)
        }
        return actionBuilder.build()
    }

    private fun replyActivityAction(
        context: Context,
        event: CapsuleEvent,
        sourceAction: Notification.Action,
    ): Notification.Action {
        val replyIntent = Intent(context, ReplyActivity::class.java)
            .setAction(ReplyActivity.ACTION_REPLY)
            .putExtra(ReplyActivity.EXTRA_SOURCE_ACTION, sourceAction)
            .putExtra(ReplyActivity.EXTRA_CONVERSATION_TITLE, event.title)
            .putExtra(ReplyActivity.EXTRA_SOURCE_LABEL, event.sourceLabel)
            .putExtra(ReplyActivity.EXTRA_SOURCE_PACKAGE, event.sourcePackage)
            .putExtra(
                ReplyActivity.EXTRA_ORIGINAL_MESSAGE,
                if (event.privacy == CapsulePrivacy.SHOW_FULL) event.body else "内容已隐藏",
            )
            .putStringArrayListExtra(
                ReplyActivity.EXTRA_SMART_REPLIES,
                ArrayList(event.smartReplies.take(3)),
            )
        val pendingIntent = PendingIntent.getActivity(
            context,
            "${event.eventId}:reply".hashCode(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(
            sourceAction.getIcon() ?: Icon.createWithResource(context, R.drawable.ic_capsule),
            sourceAction.title,
            pendingIntent,
        )
            .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    private const val MAX_VISIBLE_ACTIONS = 2
}
