package io.github.venompool888.fluidcapsule.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class OtpPresentationTest {
    @Test
    fun showsSourceAppAndSender() {
        val result = OtpPresentationFormatter.format("Telegram", "开源软件")

        assertEquals("验证码 · 开源软件", result.title)
        assertEquals("Telegram", result.sourceLabel)
    }

    @Test
    fun avoidsRepeatingAppNameAsSender() {
        val result = OtpPresentationFormatter.format("QQ", "QQ")

        assertEquals("验证码", result.title)
        assertEquals("QQ", result.sourceLabel)
    }

    @Test
    fun fallsBackForMissingLabels() {
        val result = OtpPresentationFormatter.format(" ", " ")

        assertEquals("验证码", result.title)
        assertEquals("未知应用", result.sourceLabel)
    }
}
