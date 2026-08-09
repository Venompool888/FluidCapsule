package io.github.venompool888.fluidcapsule.integration

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.view.accessibility.AccessibilityNodeInfo
import io.github.venompool888.fluidcapsule.core.CapsuleAction
import io.github.venompool888.fluidcapsule.core.CapsuleEvent
import io.github.venompool888.fluidcapsule.core.CapsuleKind
import io.github.venompool888.fluidcapsule.core.CapsulePrivacy
import io.github.venompool888.fluidcapsule.publisher.PublisherRouter
import io.github.venompool888.fluidcapsule.settings.UserSettings

class SpeedtestAccessibilityAdapter(private val context: Context) {
    private var lastPublishedAt = 0L
    private var lastBody = ""

    fun inspect(root: AccessibilityNodeInfo?) {
        if (!UserSettings.speedtestCloudEnabled(context) || root == null) return
        val now = System.currentTimeMillis()
        if (now - lastPublishedAt < MIN_UPDATE_INTERVAL_MS) return
        val snapshot = SpeedtestTextParser.parse(collectVisibleTexts(root)) ?: return
        val body = snapshot.body()
        if (body.isBlank() || body == lastBody) return

        PublisherRouter.publish(
            context,
            CapsuleEvent(
                sourcePackage = SPEEDTEST_PACKAGE,
                sourceLabel = "Speedtest",
                sourceSmallIcon = sourceIcon(),
                eventId = EVENT_ID,
                kind = CapsuleKind.CUSTOM,
                title = "Speedtest",
                shortText = snapshot.shortText,
                body = body,
                action = launchAction(),
                privacy = CapsulePrivacy.SHOW_FULL,
                createdAtMillis = now,
                expiresAtMillis = now + 2 * 60_000L,
                dedupeKey = EVENT_ID,
                progress = snapshot.progress,
            ),
        )
        lastPublishedAt = now
        lastBody = body
    }

    private fun collectVisibleTexts(root: AccessibilityNodeInfo): List<String> {
        val result = linkedSetOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_NODES) {
            val node = queue.removeFirst()
            node.text?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let(result::add)
            node.contentDescription?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let(result::add)
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return result.toList()
    }

    private fun sourceIcon(): Icon? = runCatching {
        val applicationInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getApplicationInfo(
                SPEEDTEST_PACKAGE,
                PackageManager.ApplicationInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(SPEEDTEST_PACKAGE, 0)
        }
        Icon.createWithResource(SPEEDTEST_PACKAGE, applicationInfo.icon)
    }.getOrNull()

    private fun launchAction(): CapsuleAction {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SPEEDTEST_PACKAGE)
            ?: return CapsuleAction.None
        val pendingIntent = PendingIntent.getActivity(
            context,
            EVENT_ID.hashCode(),
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return CapsuleAction.OpenOriginal(pendingIntent)
    }

    companion object {
        const val SPEEDTEST_PACKAGE = "org.zwanoo.android.speedtest"
        private const val EVENT_ID = "speedtest-live-session"
        private const val MIN_UPDATE_INTERVAL_MS = 400L
        private const val MAX_NODES = 240
    }
}
