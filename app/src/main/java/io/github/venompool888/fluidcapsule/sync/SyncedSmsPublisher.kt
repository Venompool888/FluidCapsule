package io.github.venompool888.fluidcapsule.sync

import android.content.Context
import io.github.venompool888.fluidcapsule.core.CapsuleAction
import io.github.venompool888.fluidcapsule.core.CapsuleEvent
import io.github.venompool888.fluidcapsule.core.CapsuleKind
import io.github.venompool888.fluidcapsule.core.CapsulePrivacy
import io.github.venompool888.fluidcapsule.parser.OtpParseResult
import io.github.venompool888.fluidcapsule.parser.OtpParser
import io.github.venompool888.fluidcapsule.parser.OtpPresentationFormatter
import io.github.venompool888.fluidcapsule.publisher.PublisherRouter
import io.github.venompool888.fluidcapsule.rules.RulePack
import io.github.venompool888.fluidcapsule.rules.RuleRuntime
import io.github.venompool888.fluidcapsule.settings.AppRuleStore
import io.github.venompool888.fluidcapsule.settings.UserSettings
import org.json.JSONObject

data class SyncedSmsDisplay(
    val packageName: String,
    val sourceName: String,
    val now: Long,
    val showOtpDirectly: Boolean,
    val otpTtlOverride: Int = 0,
    val plainTtlMinutes: Int = 5,
    val priority: Int = 0,
    val showContent: Boolean = true,
)

object SyncedSmsPublisher {
    fun publish(context: Context, eventId: String, sourceName: String, payload: JSONObject): Boolean {
        val appRule = AppRuleStore.get(context, context.packageName)
        val display = SyncedSmsDisplay(context.packageName, sourceName, System.currentTimeMillis(),
            UserSettings.showOtpDirectly(context), appRule.ttlMinutes,
            AppRuleStore.effectiveTtlMinutes(context, context.packageName), appRule.priority,
            AppRuleStore.showContent(context, context.packageName))
        val event = capsule(eventId, payload, RuleRuntime.current(context), display) ?: return false
        PublisherRouter.publish(context, event)
        return true
    }

    /** No Android services are accessed here; sent SMS and commands never produce events. */
    fun capsule(eventId: String, payload: JSONObject, rules: RulePack, display: SyncedSmsDisplay): CapsuleEvent? {
        if (payload.optString("kind") != "sms" || payload.optInt("type") != 1) return null
        val address = payload.getString("address").trim()
        val body = payload.getString("body").trim()
        require(address.isNotEmpty() && address.length <= 512 && body.length <= 60_000) { "同步短信内容无效" }
        val sourceLabel = "短信 · ${display.sourceName}"
        val otp = OtpParser.parse(body, rules) as? OtpParseResult.Success
        val ttl = if (otp != null) {
            display.otpTtlOverride.takeIf { it > 0 } ?: otp.validForMinutes?.coerceIn(1, 30) ?: 5
        } else display.plainTtlMinutes.takeIf { it > 0 } ?: 5
        return CapsuleEvent(
            sourcePackage = display.packageName,
            sourceLabel = sourceLabel,
            eventId = "sync:$eventId",
            kind = if (otp != null) CapsuleKind.OTP else CapsuleKind.NOTIFICATION,
            title = if (otp != null) OtpPresentationFormatter.format(sourceLabel, address).title else address,
            shortText = otp?.code ?: body,
            body = if (otp != null) otp.validForMinutes?.let { "$it 分钟内有效 · 点击复制" } ?: "点击复制" else body,
            action = otp?.let { CapsuleAction.CopySensitiveText(it.code) } ?: CapsuleAction.None,
            // Same privacy as notifications from whitelisted apps: OTPs follow "show code directly",
            // other SMS follow the content setting.
            privacy = when {
                otp != null -> if (display.showOtpDirectly) CapsulePrivacy.SHOW_FULL else CapsulePrivacy.HIDE_SENSITIVE
                display.showContent -> CapsulePrivacy.SHOW_FULL
                else -> CapsulePrivacy.HIDE_SENSITIVE
            },
            createdAtMillis = display.now,
            expiresAtMillis = display.now + ttl * 60_000L,
            dedupeKey = otp?.let { "sms-otp:${it.code}" } ?: "sync:$eventId",
            priorityAdjustment = display.priority,
        )
    }
}
