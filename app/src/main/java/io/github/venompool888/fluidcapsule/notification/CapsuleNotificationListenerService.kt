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
import io.github.venompool888.fluidcapsule.history.NotificationHistoryStore
import io.github.venompool888.fluidcapsule.integration.KnownNotificationAdapter
import io.github.venompool888.fluidcapsule.integration.KnownNotificationDecision
import io.github.venompool888.fluidcapsule.parser.OtpParseResult
import io.github.venompool888.fluidcapsule.parser.OtpParser
import io.github.venompool888.fluidcapsule.parser.OtpPresentationFormatter
import io.github.venompool888.fluidcapsule.publisher.CapsuleCoordinator
import io.github.venompool888.fluidcapsule.publisher.PublisherRouter
import io.github.venompool888.fluidcapsule.settings.NotificationWhitelist
import io.github.venompool888.fluidcapsule.settings.AppRuleStore
import io.github.venompool888.fluidcapsule.settings.UserSettings
import java.util.concurrent.Executors

class CapsuleNotificationListenerService : NotificationListenerService() {
    private var lastMirroredSourceKey: String? = null
    private var lastSmartReplies: List<String> = emptyList()
    private val historyExecutor = Executors.newSingleThreadExecutor()

    override fun onListenerConnected() {
        super.onListenerConnected()
        DiagnosticsStore.markListenerConnected(this, true)
        val recentCutoff = System.currentTimeMillis() -
            UserSettings.capsuleDisplayDurationMillis(this)
        val currentNotifications = runCatching { activeNotifications.orEmpty().toList() }
            .getOrDefault(emptyList())
        historyExecutor.execute {
            NotificationHistoryStore.reconcileActiveNotifications(
                applicationContext,
                currentNotifications.mapTo(mutableSetOf()) { it.key },
            )
        }
        currentNotifications
            .asSequence()
            .filter { it.packageName != packageName && it.postTime >= recentCutoff }
            .sortedBy { it.postTime }
            .forEach { processNotification(it, currentRanking, recordHistory = true) }
    }

    override fun onListenerDisconnected() {
        DiagnosticsStore.markListenerConnected(this, false)
        requestRebind(ComponentName(this, CapsuleNotificationListenerService::class.java))
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processNotification(sbn, currentRanking, recordHistory = true)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        processNotification(sbn, rankingMap, recordHistory = true)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        val sourceKey = lastMirroredSourceKey ?: return
        val features = rankingFeatures(rankingMap, sourceKey)
        if (features.smartReplies.isEmpty() || features.smartReplies == lastSmartReplies) return
        activeNotifications.firstOrNull { it.key == sourceKey }
            ?.let { processNotification(it, rankingMap, recordHistory = false) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != packageName) {
            CapsuleCoordinator.removeSourceEvent(this, sbn.key)
            historyExecutor.execute {
                NotificationHistoryStore.markRemoved(applicationContext, sbn.key)
            }
        }
        if (sbn.key == lastMirroredSourceKey) {
            lastMirroredSourceKey = null
            lastSmartReplies = emptyList()
        }
    }

    override fun onDestroy() {
        historyExecutor.shutdown()
        super.onDestroy()
    }

    private fun processNotification(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        recordHistory: Boolean,
    ) {
        if (sbn.packageName == packageName) return

        DiagnosticsStore.markNotificationSeen(this, sbn.packageName)
        val normalized = NotificationNormalizer.normalize(sbn)
        val appRule = AppRuleStore.get(this, sbn.packageName)
        val shouldRecordHistory = recordHistory &&
            UserSettings.notificationHistoryEnabled(this) && appRule.recordHistory
        if (shouldRecordHistory) {
            historyExecutor.execute {
                NotificationHistoryStore.record(applicationContext, normalized)
                NotificationHistoryStore.purgeOlderThanDays(
                    applicationContext,
                    UserSettings.notificationHistoryRetentionDays(applicationContext),
                )
            }
        }
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
        val isDefaultSms = defaultSmsPackage != null && sbn.packageName == defaultSmsPackage
        val isWhitelisted = NotificationWhitelist.contains(this, sbn.packageName)
        if (!isDefaultSms && !isWhitelisted) {
            DiagnosticsStore.markParse(this, "SKIPPED_NOT_WHITELISTED")
            recordDecision(shouldRecordHistory, normalized.notificationKey, "SKIPPED", "未加入通知岛白名单")
            return
        }

        if (normalized.isGroupSummary || normalized.combinedText.isBlank()) {
            DiagnosticsStore.markParse(this, "SKIPPED_EMPTY_OR_GROUP_SUMMARY")
            recordDecision(shouldRecordHistory, normalized.notificationKey, "SKIPPED", "群组摘要或通知正文为空")
            return
        }
        val ruleMatch = appRule.matches(normalized.combinedText)
        if (!ruleMatch.matches) {
            DiagnosticsStore.markParse(this, "SKIPPED_APP_RULE")
            recordDecision(shouldRecordHistory, normalized.notificationKey, "FILTERED", ruleMatch.detail)
            return
        }
        val appLabel = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(normalized.packageName, 0),
            ).toString()
        }.getOrDefault(normalized.packageName)
        // Parse only the currently displayed message. Aggregated text can contain the
        // conversation title and stale MessagingStyle messages, which must never become
        // OTP candidates for the latest notification.
        when (val result = OtpParser.parse(normalized.primaryText)) {
            is OtpParseResult.Success -> {
                DiagnosticsStore.markParse(this, "OTP_SUCCESS_${result.confidence}")
                val now = System.currentTimeMillis()
                val ttlMinutes = appRule.ttlMinutes.takeIf { it > 0 }
                    ?: result.validForMinutes?.coerceIn(1, 30)
                    ?: 5
                val showDirectly = UserSettings.showOtpDirectly(this)
                val presentation = OtpPresentationFormatter.format(appLabel, normalized.title)
                PublisherRouter.publish(
                    this,
                    CapsuleEvent(
                        sourcePackage = normalized.packageName,
                        sourceLabel = presentation.sourceLabel,
                        sourceSmallIcon = normalized.smallIcon,
                        sourceLargeIcon = normalized.largeIcon ?: normalized.senderIcon,
                        eventId = normalized.notificationKey,
                        kind = CapsuleKind.OTP,
                        title = presentation.title,
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
                        priorityAdjustment = appRule.priority,
                    ),
                )
                recordDecision(
                    shouldRecordHistory,
                    normalized.notificationKey,
                    "PUBLISHED",
                    "验证码识别成功并已提交到流体云",
                )
                return
            }
            is OtpParseResult.Ambiguous -> {
                DiagnosticsStore.markParse(this, "OTP_AMBIGUOUS_${result.candidateCount}")
            }
            OtpParseResult.None -> DiagnosticsStore.markParse(this, "NO_OTP")
        }

        if (!isWhitelisted) return
        if (NotificationWhitelist.isOtpOnly(this, normalized.packageName)) {
            DiagnosticsStore.markParse(this, "SKIPPED_OTP_ONLY_NON_OTP")
            recordDecision(
                shouldRecordHistory,
                normalized.notificationKey,
                "FILTERED",
                "已开启仅验证码上云，但本条未识别出可靠验证码",
            )
            return
        }

        val now = System.currentTimeMillis()
        val showContent = AppRuleStore.showContent(this, normalized.packageName)
        when (val decision = KnownNotificationAdapter.adapt(this, normalized, showContent, now)) {
            is KnownNotificationDecision.Publish -> {
                DiagnosticsStore.markParse(this, "KNOWN_APP_${normalized.packageName}")
                PublisherRouter.publish(
                    this,
                    decision.event.copy(
                        expiresAtMillis = now + AppRuleStore.effectiveTtlMinutes(this, normalized.packageName) * 60_000L,
                        body = decision.event.body.take(appRule.maxBodyChars),
                        priorityAdjustment = appRule.priority,
                    ),
                )
                recordDecision(shouldRecordHistory, normalized.notificationKey, "PUBLISHED", "专属适配器已提交到流体云")
                return
            }
            KnownNotificationDecision.Suppress -> {
                DiagnosticsStore.markParse(this, "KNOWN_APP_SUPPRESSED_${normalized.packageName}")
                recordDecision(shouldRecordHistory, normalized.notificationKey, "FILTERED", "专属适配器判断无需显示")
                return
            }
            null -> Unit
        }

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
            .take(appRule.maxBodyChars)
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
                expiresAtMillis = now + AppRuleStore.effectiveTtlMinutes(this, normalized.packageName) * 60_000L,
                dedupeKey = normalized.notificationKey,
                priorityAdjustment = appRule.priority,
            ),
        )
        recordDecision(shouldRecordHistory, normalized.notificationKey, "PUBLISHED", "白名单规则通过并已提交到流体云")
    }

    private fun recordDecision(enabled: Boolean, key: String, decision: String, detail: String) {
        if (!enabled) return
        historyExecutor.execute {
            NotificationHistoryStore.updateDecision(applicationContext, key, decision, detail)
        }
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
