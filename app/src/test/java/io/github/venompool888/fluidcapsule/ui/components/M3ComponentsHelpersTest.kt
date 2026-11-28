package io.github.venompool888.fluidcapsule.ui.components

import io.github.venompool888.fluidcapsule.settings.AppRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class M3ComponentsHelpersTest {
    @Test
    fun relativeTimeHandlesEmptyFutureSecondsMinutesHoursDaysAndDates() {
        val now = 1_780_000_000_000L
        assertEquals("尚无记录", RelativeTimeFormatter.format(0L, now))
        assertEquals("未记录", RelativeTimeFormatter.format(-5L, now, emptyLabel = "未记录"))
        assertEquals("刚刚", RelativeTimeFormatter.format(now + 5_000L, now))
        assertEquals("刚刚", RelativeTimeFormatter.format(now - 59_999L, now))
        assertEquals("1 分钟前", RelativeTimeFormatter.format(now - 60_000L, now))
        assertEquals("59 分钟前", RelativeTimeFormatter.format(now - 59 * 60_000L, now))
        assertEquals("1 小时前", RelativeTimeFormatter.format(now - 60 * 60_000L, now))
        assertEquals("23 小时前", RelativeTimeFormatter.format(now - 23 * 60 * 60_000L, now))
        assertEquals("1 天前", RelativeTimeFormatter.format(now - 24 * 60 * 60_000L, now))
        assertEquals("6 天前", RelativeTimeFormatter.format(now - 6 * 24 * 60 * 60_000L, now))

        val sevenDaysAgo = now - 7 * 24 * 60 * 60_000L
        val expected = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(sevenDaysAgo))
        assertEquals(expected, RelativeTimeFormatter.format(sevenDaysAgo, now))
    }

    @Test
    fun summarizeRuleChipsReflectsDefaultAndCustomFields() {
        val defaultChips = summarizeRuleChips(AppRule(), otpOnly = false)
        assertEquals(
            listOf(
                RuleChipSpec("跟随全局时长", highlighted = false, warning = false),
                RuleChipSpec("普通优先级", highlighted = false, warning = false),
            ),
            defaultChips,
        )

        val customRule = AppRule(
            ttlMinutes = 10,
            priority = 1,
            contentMode = AppRule.CONTENT_HIDE,
            maxBodyChars = 120,
            includeKeywords = "验证码, 登录",
            excludeKeywords = "广告",
            recordHistory = false,
        )
        val customChips = summarizeRuleChips(customRule, otpOnly = true)
        assertEquals(
            listOf(
                RuleChipSpec("胶囊 10 分钟", highlighted = true),
                RuleChipSpec("高优先级", highlighted = true),
                RuleChipSpec("仅识别验证码", highlighted = true),
                RuleChipSpec("始终隐藏正文", highlighted = true),
                RuleChipSpec("上限 120 字", highlighted = true),
                RuleChipSpec("关键词 含2/排1", highlighted = true),
                RuleChipSpec("不记历史", highlighted = false, warning = true),
            ),
            customChips,
        )
    }

    @Test
    fun summarizeRuleChipsDistinguishesIncludeOnlyAndExcludeOnlyKeywords() {
        val includeOnly = summarizeRuleChips(
            AppRule(priority = -1, contentMode = AppRule.CONTENT_SHOW, includeKeywords = "登录"),
            otpOnly = false,
        )
        assertTrue(includeOnly.any { it.label == "低优先级" && it.highlighted })
        assertTrue(includeOnly.any { it.label == "始终显示正文" && it.highlighted })
        assertTrue(includeOnly.any { it.label == "包含关键词 1" && it.highlighted })
        assertFalse(includeOnly.any { it.warning })

        val excludeOnly = summarizeRuleChips(
            AppRule(excludeKeywords = "广告, 营销"),
            otpOnly = false,
        )
        assertTrue(excludeOnly.any { it.label == "排除关键词 2" && it.highlighted })
    }
}
