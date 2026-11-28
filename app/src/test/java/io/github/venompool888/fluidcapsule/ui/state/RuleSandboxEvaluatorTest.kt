package io.github.venompool888.fluidcapsule.ui.state

import io.github.venompool888.fluidcapsule.rules.CodePosition
import io.github.venompool888.fluidcapsule.rules.LiteralRule
import io.github.venompool888.fluidcapsule.rules.OtpBindingRule
import io.github.venompool888.fluidcapsule.rules.RulePack
import io.github.venompool888.fluidcapsule.settings.AppRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleSandboxEvaluatorTest {
    @Test
    fun blankInputReturnsEmptyInput() {
        val result = RuleSandboxEvaluator.evaluate("   \n\t  ", AppRule(), otpOnly = false)
        assertTrue(result is SandboxResult.EmptyInput)
    }

    @Test
    fun excludeKeywordOverridesIncludeKeywordAndOtp() {
        val rule = AppRule(
            includeKeywords = "验证码, 登录",
            excludeKeywords = "广告，促销\n营销",
        )
        val result = RuleSandboxEvaluator.evaluate(
            sampleText = "【促销】您的登录验证码是 482913，5分钟内有效。",
            rule = rule,
            otpOnly = false,
        )

        assertTrue(result is SandboxResult.BlockedByExcludeKeyword)
        result as SandboxResult.BlockedByExcludeKeyword
        assertEquals("促销", result.keyword)
        assertTrue(result.message.contains("促销"))
    }

    @Test
    fun keywordMatchingIsCaseInsensitive() {
        val rule = AppRule(excludeKeywords = "Promo")
        val result = RuleSandboxEvaluator.evaluate(
            sampleText = "Special PROMO offer: verification code is 482913",
            rule = rule,
            otpOnly = true,
        )

        assertTrue(result is SandboxResult.BlockedByExcludeKeyword)
        assertEquals("Promo", (result as SandboxResult.BlockedByExcludeKeyword).keyword)
    }

    @Test
    fun includeKeywordBlocksValidOtpWhenAbsent() {
        val rule = AppRule(includeKeywords = "招商银行, 转账")
        val result = RuleSandboxEvaluator.evaluate(
            sampleText = "您的验证码是 482913，5分钟内有效。",
            rule = rule,
            otpOnly = true,
        )

        assertTrue(result is SandboxResult.BlockedByIncludeKeyword)
    }

    @Test
    fun extractsOtpWithPriorityLabelAndValidity() {
        val low = RuleSandboxEvaluator.evaluate(
            sampleText = "【招商银行】您的验证码是 739201，5分钟内有效。",
            rule = AppRule(priority = -1),
            otpOnly = true,
        ) as SandboxResult.MatchedOtp
        assertEquals("739201", low.code)
        assertEquals(5, low.validForMinutes)
        assertTrue(low.message.contains("优先级 低"))

        val high = RuleSandboxEvaluator.evaluate(
            sampleText = "【招商银行】您的验证码是 739201，5分钟内有效。",
            rule = AppRule(priority = 1),
            otpOnly = true,
        ) as SandboxResult.MatchedOtp
        assertTrue(high.message.contains("优先级 高"))
    }

    @Test
    fun forwardsRulePackToOtpParser() {
        val rulePack = RulePack.EMPTY.copy(
            otpKeywords = listOf(LiteralRule("secure", "secure number")),
            otpBindings = listOf(OtpBindingRule("after", "secure number", CodePosition.AFTER, 12)),
        )
        val result = RuleSandboxEvaluator.evaluate(
            sampleText = "Your secure number is 918273",
            rule = AppRule(),
            otpOnly = true,
            rulePack = rulePack,
        )

        assertTrue(result is SandboxResult.MatchedOtp)
        assertEquals("918273", (result as SandboxResult.MatchedOtp).code)
    }

    @Test
    fun recognizesVerificationLinkWithAndWithoutDirectUrl() {
        val withUrl = RuleSandboxEvaluator.evaluate(
            sampleText = "Please verify your email at https://example.com/verify?token=abc",
            rule = AppRule(),
            otpOnly = true,
        )
        assertTrue(withUrl is SandboxResult.MatchedVerificationLink)
        assertEquals(
            "https://example.com/verify?token=abc",
            (withUrl as SandboxResult.MatchedVerificationLink).url,
        )

        val withoutUrl = RuleSandboxEvaluator.evaluate(
            sampleText = "Please verify your email to finish signup.",
            rule = AppRule(),
            otpOnly = true,
        )
        assertTrue(withoutUrl is SandboxResult.MatchedVerificationLink)
        assertNull((withoutUrl as SandboxResult.MatchedVerificationLink).url)
    }

    @Test
    fun otpOnlySkipsNormalAndAmbiguousNotifications() {
        val plain = RuleSandboxEvaluator.evaluate(
            sampleText = "您的外卖骑手已到楼下，请准备取餐。",
            rule = AppRule(),
            otpOnly = true,
        )
        assertTrue(plain is SandboxResult.SkippedNotOtp)
        assertNull((plain as SandboxResult.SkippedNotOtp).ambiguousCandidates)

        val ambiguous = RuleSandboxEvaluator.evaluate(
            sampleText = "验证码 123456，验证码 654321",
            rule = AppRule(),
            otpOnly = true,
        )
        assertTrue(ambiguous is SandboxResult.SkippedNotOtp)
        assertEquals(2, (ambiguous as SandboxResult.SkippedNotOtp).ambiguousCandidates)
    }

    @Test
    fun whitelistModeTruncatesOnlyWhenExceedingMaxBodyChars() {
        val shortText = "您的外卖骑手已到楼下"
        val shortResult = RuleSandboxEvaluator.evaluate(
            sampleText = shortText,
            rule = AppRule(maxBodyChars = 20),
            otpOnly = false,
        ) as SandboxResult.MatchedWhitelist
        assertFalse(shortResult.truncated)
        assertEquals(shortText, shortResult.previewText)
        assertEquals(20, shortResult.maxBodyChars)

        val longText = "这是一条非常长的普通白名单通知内容用于测试截断逻辑"
        val longResult = RuleSandboxEvaluator.evaluate(
            sampleText = longText,
            rule = AppRule(maxBodyChars = 10),
            otpOnly = false,
        ) as SandboxResult.MatchedWhitelist
        assertTrue(longResult.truncated)
        assertEquals(longText.take(10), longResult.previewText)
        assertTrue(longResult.message.contains("将截断"))
    }
}
