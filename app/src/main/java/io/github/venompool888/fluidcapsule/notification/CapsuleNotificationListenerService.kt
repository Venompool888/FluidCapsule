package io.github.venompool888.fluidcapsule.notification

import android.provider.Telephony
import android.app.Notification
import android.os.Build
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.venompool888.fluidcapsule.core.CapsuleAction
import io.github.venompool888.fluidcapsule.core.CapsuleEvent
import io.github.venompool888.fluidcapsule.core.CapsuleKind
import io.github.venompool888.fluidcapsule.core.CapsulePrivacy
import io.github.venompool888.fluidcapsule.diagnostics.DiagnosticsStore
import io.github.venompool888.fluidcapsule.integration.KnownNotificationAdapter
import io.github.venompool888.fluidcapsule.integration.KnownNotificationDecision
import io.github.venompool888.fluidcapsule.parser.OtpParseResult
import io.github.venompool888.fluidcapsule.parser.OtpParser
import io.github.venompool888.fluidcapsule.publisher.PublisherRouter
import io.github.venompool888.fluidcapsule.settings.NotificationWhitelist
import io.github.venompool888.fluidcapsule.settings.UserSettings
import java.util.UUID

class CapsuleNotificationListenerService : NotificationListenerService() {
    private var lastMirroredSourceKey: String? = null
    private var lastSmartReplies: List<String> = emptyList()

    override fun onListenerConnected() {
        super.onListenerConnected()
        DiagnosticsStore.markListenerConnected(this, true)
    }

    override fun onListenerDisconnected() {
        DiagnosticsStore.markListenerConnected(this, false)
        requestRebind(ComponentName(this, CapsuleNotificationListenerService::class.java))
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processNotification(sbn, currentRanking)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        processNotification(sbn, rankingMap)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        val sourceKey = lastMirroredSourceKey ?: return
        val features = rankingFeatures(rankingMap, sourceKey)
        if (features.smartReplies.isEmpty() || features.smartReplies == lastSmartReplies) return
        activeNotifications.firstOrNull { it.key == sourceKey }
            ?.let { processNotification(it, rankingMap) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == lastMirroredSourceKey) {
            lastMirroredSourceKey = null
            lastSmartReplies = emptyList()
        }
    }

    private fun processNotification(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (sbn.packageName == packageName) return

        DiagnosticsStore.markNotificationSeen(this, sbn.packageName)
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
        val isDefaultSms = defaultSmsPackage != null && sbn.packageName == defaultSmsPackage
        val isWhitelisted = NotificationWhitelist.contains(this, sbn.packageName)
        if (!isDefaultSms && !isWhitelisted) {
            DiagnosticsStore.markParse(this, "SKIPPED_NOT_WHITELISTED")
            return
        }

        val normalized = NotificationNormalizer.normalize(sbn)
        if (normalized.isGroupSummary || normalized.combinedText.isBlank()) {
            DiagnosticsStore.markParse(this, "SKIPPED_EMPTY_OR_GROUP_SUMMARY")
            return
        }
        when (val result = OtpParser.parse(normalized.combinedText)) {
            is OtpParseResult.Success -> {
                DiagnosticsStore.markParse(this, "OTP_SUCCESS_${result.confidence}")
                val now = System.currentTimeMillis()
                val ttlMinutes = result.validForMinutes?.coerceIn(1, 30) ?: 5
                val showDirectly = UserSettings.showOtpDirectly(this)
                PublisherRouter.publish(
                    this,
                    CapsuleEvent(
                        sourcePackage = normalized.packageName,
                        eventId = UUID.randomUUID().toString(),
                        kind = CapsuleKind.OTP,
                        title = "验证码",
                        shortText = result.code,
                        body = if (result.validForMinutes != null) {
                            "${result.validForMinutes} 分钟内有效 · 点击复制"
                        } else {
                            "点击复制"
                        },
                        action = CapsuleAction.CopySensitiveText(result.code),
                        privacy = if (showDirectly) CapsulePrivacy.SHOW_FULL else CapsulePrivacy.HIDE_SENSITIVE,
                        createdAtMillis = now,
                        expiresAtMillis = now + ttlMinutes * 60_000L,
                        dedupeKey = "${normalized.packageName}:${result.code}",
                    ),
                )
                return
            }
            is OtpParseResult.Ambiguous -> {
                DiagnosticsStore.markParse(this, "OTP_AMBIGUOUS_${result.candidateCount}")
            }
            OtpParseResult.None -> DiagnosticsStore.markParse(this, "NO_OTP")
        }

        if (!isWhitelisted) return

        val now = System.currentTimeMillis()
        val showContent = UserSettings.showWhitelistContent(this)
        when (val decision = KnownNotificationAdapter.adapt(this, normalized, showContent, now)) {
            is KnownNotificationDecision.Publish -> {
                DiagnosticsStore.markParse(this, "KNOWN_APP_${normalized.packageName}")
                PublisherRouter.publish(this, decision.event)
                return
            }
            KnownNotificationDecision.Suppress -> {
                DiagnosticsStore.markParse(this, "KNOWN_APP_SUPPRESSED_${normalized.packageName}")
                return
            }
            null -> Unit
        }

        val appLabel = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(normalized.packageName, 0),
            ).toString()
        }.getOrDefault(normalized.packageName)
        val displayTitle = normalized.title
            .takeIf { it.isNotBlank() && !it.equals(appLabel, ignoreCase = true) }
            ?: appLabel
        val displayBody = listOf(normalized.primaryText)
            .map { it.trim() }
            .filter {
                it.isNotEmpty() &&
                    !it.equals(appLabel, ignoreCase = true) &&
                    !it.equals(displayTitle, ignoreCase = true)
            }
            .distinct()
            .joinToString(" · ")
            .ifEmpty {
                normalized.combinedText.lines()
                    .map(String::trim)
                    .firstOrNull {
                        it.isNotEmpty() &&
                            !it.equals(appLabel, ignoreCase = true) &&
                            !it.equals(displayTitle, ignoreCase = true)
                    }
                    ?: "有新通知"
            }
            .take(220)
        val rankingFeatures = rankingFeatures(rankingMap, normalized.notificationKey)
        val sourceActions = (normalized.actions + rankingFeatures.smartActions)
            .distinctBy { "${it.semanticAction}:${it.title}" }
        lastMirroredSourceKey = normalized.notificationKey
        lastSmartReplies = rankingFeatures.smartReplies
        DiagnosticsStore.markParse(this, "WHITELIST_NOTIFICATION")
        PublisherRouter.publish(
            this,
            CapsuleEvent(
                sourcePackage = normalized.packageName,
                sourceLabel = appLabel,
                sourceSmallIcon = normalized.smallIcon,
                sourceLargeIcon = normalized.largeIcon ?: normalized.senderIcon,
                sourceActions = sourceActions,
                smartReplies = rankingFeatures.smartReplies,
                eventId = normalized.notificationKey,
                kind = CapsuleKind.NOTIFICATION,
                title = displayTitle,
                shortText = displayTitle.take(8),
                body = displayBody,
                action = normalized.contentIntent
                    ?.let(CapsuleAction::OpenOriginal)
                    ?: CapsuleAction.None,
                privacy = if (showContent) CapsulePrivacy.SHOW_FULL else CapsulePrivacy.HIDE_SENSITIVE,
                createdAtMillis = now,
                expiresAtMillis = now + 60_000L,
                dedupeKey = normalized.notificationKey,
            ),
        )
    }

    private fun rankingFeatures(rankingMap: RankingMap, key: String): RankingFeatures {
        if (Build.VERSION.SDK_INT < 29) return RankingFeatures()
        val ranking = Ranking()
        if (!rankingMap.getRanking(key, ranking)) return RankingFeatures()
        return RankingFeatures(
            smartReplies = ranking.smartReplies
                .map { it.toString().trim() }
                .filter(String::isNotEmpty)
                .distinct()
                .take(3),
            smartActions = ranking.smartActions,
        )
    }

    private data class RankingFeatures(
        val smartReplies: List<String> = emptyList(),
        val smartActions: List<Notification.Action> = emptyList(),
    )
}
