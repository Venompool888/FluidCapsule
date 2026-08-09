package io.github.venompool888.fluidcapsule.action

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import io.github.venompool888.fluidcapsule.publisher.CapsuleCoordinator

class ForwardNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FORWARD_NOTIFICATION_ACTION) return

        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        val sourceAction = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_SOURCE_ACTION, Notification.Action::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_SOURCE_ACTION)
        }
        if (sourceAction == null) {
            CapsuleCoordinator.consume(context, eventId)
            return
        }

        val fillInIntent = Intent()
        val remoteInputs = sourceAction.remoteInputs.orEmpty()
        val presetReply = intent.getStringExtra(EXTRA_PRESET_REPLY)
        val textResults = if (presetReply != null && remoteInputs.isNotEmpty()) {
            Bundle().apply { putCharSequence(remoteInputs.first().resultKey, presetReply) }
        } else {
            RemoteInput.getResultsFromIntent(intent)
        }
        if (textResults != null && remoteInputs.isNotEmpty()) {
            RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, textResults)
        }
        sourceAction.dataOnlyRemoteInputs.orEmpty().forEach { input ->
            RemoteInput.getDataResultsFromIntent(intent, input.resultKey)
                ?.let { RemoteInput.addDataResultToIntent(input, fillInIntent, it) }
        }

        try {
            sourceAction.actionIntent.send(context, 0, fillInIntent)
        } catch (_: PendingIntent.CanceledException) {
            // The source app invalidated the action; consume the stale capsule below.
        } finally {
            CapsuleCoordinator.consume(context, eventId)
        }
    }

    companion object {
        const val ACTION_FORWARD_NOTIFICATION_ACTION =
            "io.github.venompool888.fluidcapsule.action.FORWARD_NOTIFICATION_ACTION"
        const val EXTRA_SOURCE_ACTION = "source_action"
        const val EXTRA_PRESET_REPLY = "preset_reply"
        const val EXTRA_EVENT_ID = "event_id"
    }
}
