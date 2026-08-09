package io.github.venompool888.fluidcapsule.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRuleTest {
    @Test fun includeAndExcludeKeywordsAreApplied() {
        val rule = AppRule(includeKeywords = "验证码, login", excludeKeywords = "广告")
        assertTrue(rule.matches("你的登录验证码是 482913").matches)
        assertFalse(rule.matches("普通邮件").matches)
        assertFalse(rule.matches("验证码广告").matches)
    }
}
