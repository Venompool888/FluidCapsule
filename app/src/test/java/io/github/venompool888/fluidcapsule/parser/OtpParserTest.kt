package io.github.venompool888.fluidcapsule.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpParserTest {
    @Test
    fun parsesChineseOtp() {
        val result = OtpParser.parse("您的验证码为 482913，5 分钟内有效")
        assertTrue(result is OtpParseResult.Success)
        result as OtpParseResult.Success
        assertEquals("482913", result.code)
        assertEquals(5, result.validForMinutes)
    }

    @Test
    fun parsesEnglishOtp() {
        val result = OtpParser.parse("Your verification code is 3812")
        assertEquals("3812", (result as OtpParseResult.Success).code)
    }

    @Test
    fun rejectsBankTailWithoutOtpKeyword() {
        val result = OtpParser.parse("您尾号 1234 的账户支出 88.50 元")
        assertEquals(OtpParseResult.None, result)
    }

    @Test
    fun selectsCodeNearestKeyword() {
        val result = OtpParser.parse("尾号 1234 的账户验证码为 905771，请勿告诉他人")
        assertEquals("905771", (result as OtpParseResult.Success).code)
    }

    @Test
    fun rejectsOrderNumber() {
        val result = OtpParser.parse("订单 20260809123456 已发货")
        assertEquals(OtpParseResult.None, result)
    }

    @Test
    fun rejectsCarrierSubscriptionReminder() {
        val result = OtpParser.parse(
            "【订购提醒】您已成功开通任我享套餐，编号 25JS205838，2026-08-09 生效。如需咨询请登录 https://example.invalid/path",
        )
        assertEquals(OtpParseResult.None, result)
    }

    @Test
    fun rejectsServiceNumberNearOtpSafetyReminder() {
        val result = OtpParser.parse("请勿向任何人提供验证码，如有疑问请拨打客服热线 10086")
        assertEquals(OtpParseResult.None, result)
    }

    @Test
    fun parsesCodeBeforeChineseKeyword() {
        val result = OtpParser.parse("482913 是您的登录验证码，请勿向他人泄露")
        assertEquals("482913", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesEnglishLoginCode() {
        val result = OtpParser.parse("Your login code is 735194. Do not share it with anyone.")
        assertEquals("735194", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesEnglishConfirmationCode() {
        val result = OtpParser.parse("Confirmation code: 281604. It expires in 10 minutes.")
        assertEquals("281604", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesAlphanumericOtp() {
        val result = OtpParser.parse("Your verification code is A7K29Q. Do not share this code.")
        assertEquals("A7K29Q", (result as OtpParseResult.Success).code)
    }

    @Test
    fun rejectsInformationalOtpWarningWithoutCode() {
        val result = OtpParser.parse("警方提醒：不要向陌生人泄露验证码或开启屏幕共享")
        assertEquals(OtpParseResult.None, result)
    }
}
