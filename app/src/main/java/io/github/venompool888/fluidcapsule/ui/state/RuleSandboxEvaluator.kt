package io.github.venompool888.fluidcapsule.ui.state

import android.content.Context
import io.github.venompool888.fluidcapsule.parser.OtpParseResult
import io.github.venompool888.fluidcapsule.parser.OtpParser
import io.github.venompool888.fluidcapsule.parser.VerificationLinkParser
import io.github.venompool888.fluidcapsule.rules.RulePack
import io.github.venompool888.fluidcapsule.rules.RuleRuntime
import io.github.venompool888.fluidcapsule.settings.AppRule

sealed interface SandboxResult {
    val message: String

    data class MatchedOtp(
        val code: String,
        val confidence: Int,
        val validForMinutes: Int?,
        override val message: String,
    ) : SandboxResult

    data class MatchedVerificationLink(
        val url: String?,
        override val message: String,
    ) : SandboxResult

    data class MatchedWhitelist(
        val previewText: String,
        val maxBodyChars: Int,
        val truncated: Boolean,
        override val message: String,
    ) : SandboxResult

    data class BlockedByExcludeKeyword(
        val keyword: String,
        override val message: String,
    ) : SandboxResult

    data class BlockedByIncludeKeyword(
        override val message: String,
    ) : SandboxResult

    data class SkippedNotOtp(
        val ambiguousCandidates: Int? = null,
        override val message: String,
    ) : SandboxResult

    data class EmptyInput(
        override val message: String = "请输入一段测试通知正文",
    ) : SandboxResult
}

object RuleSandboxEvaluator {
    val DEFAULT_SAMPLES: List<Pair<String, String>> = listOf(
        "银行验证码" to "【招商银行】您的验证码是 739201，5分钟内有效。",
        "广告推送" to "【限时红包】周末立减 50 元优惠券已发放，点击抢购",
        "普通通知" to "您的外卖骑手已到楼下，请准备取餐。",
    )

    fun evaluate(
        context: Context,
        sampleText: String,
        rule: AppRule,
        otpOnly: Boolean,
    ): SandboxResult = evaluate(
        sampleText = sampleText,
        rule = rule,
        otpOnly = otpOnly,
        rulePack = runCatching { RuleRuntime.current(context) }.getOrDefault(RulePack.EMPTY),
    )

    fun evaluate(
        sampleText: String,
        rule: AppRule,
        otpOnly: Boolean,
        rulePack: RulePack = RulePack.EMPTY,
    ): SandboxResult {
        val trimmed = sampleText.trim()
        if (trimmed.isEmpty()) return SandboxResult.EmptyInput()

        val ruleMatch = rule.matches(trimmed)
        if (!ruleMatch.matches) {
            val normalized = trimmed.lowercase()
            val matchedExclude = AppRule.keywordList(rule.excludeKeywords)
                .firstOrNull { it.lowercase() in normalized }
            return if (matchedExclude != null) {
                SandboxResult.BlockedByExcludeKeyword(
                    keyword = matchedExclude,
                    message = "将被拦截：命中排除关键词「$matchedExclude」",
                )
            } else {
                SandboxResult.BlockedByIncludeKeyword(
                    message = "将被拦截：${ruleMatch.detail}",
                )
            }
        }

        val otpResult = OtpParser.parse(trimmed, rulePack)
        if (otpResult is OtpParseResult.Success) {
            return SandboxResult.MatchedOtp(
                code = otpResult.code,
                confidence = otpResult.confidence,
                validForMinutes = otpResult.validForMinutes,
                message = "命中验证码胶囊：提取到「${otpResult.code}」（优先级 ${priorityLabel(rule.priority)}）",
            )
        }

        val verificationRequest = VerificationLinkParser.parse(trimmed, rulePack)
        if (verificationRequest != null) {
            return SandboxResult.MatchedVerificationLink(
                url = verificationRequest.url,
                message = if (verificationRequest.url != null) {
                    "命中验证链接胶囊：将提供快捷打开验证链接"
                } else {
                    "命中验证请求胶囊：无直达链接时将跳转原通知"
                },
            )
        }

        if (otpOnly) {
            val ambiguousCount = (otpResult as? OtpParseResult.Ambiguous)?.candidateCount
            return SandboxResult.SkippedNotOtp(
                ambiguousCandidates = ambiguousCount,
                message = if (ambiguousCount != null) {
                    "将被跳过：存在 $ambiguousCount 个候选数字无法唯一确认，且已开启「仅在识别到验证码时展示」"
                } else {
                    "将被跳过：未识别到验证码，且已开启「仅在识别到验证码时展示」"
                },
            )
        }

        val truncated = trimmed.length > rule.maxBodyChars
        val preview = trimmed.take(rule.maxBodyChars)
        return SandboxResult.MatchedWhitelist(
            previewText = preview,
            maxBodyChars = rule.maxBodyChars,
            truncated = truncated,
            message = if (truncated) {
                "将作为白名单胶囊展示（正文超过 ${rule.maxBodyChars} 字，将截断）"
            } else {
                "将作为白名单胶囊展示（最长截断 ${rule.maxBodyChars} 字）"
            },
        )
    }

    private fun priorityLabel(priority: Int): String = when (priority.coerceIn(-1, 1)) {
        -1 -> "低"
        1 -> "高"
        else -> "普通"
    }
}
