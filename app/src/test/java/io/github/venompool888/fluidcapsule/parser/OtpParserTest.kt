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
}
