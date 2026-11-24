package io.github.venompool888.fluidcapsule.sync

import io.github.venompool888.fluidcapsule.core.CapsuleAction
import io.github.venompool888.fluidcapsule.core.CapsuleKind
import io.github.venompool888.fluidcapsule.core.CapsulePrivacy
import io.github.venompool888.fluidcapsule.rules.RulePack
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncedSmsPublisherTest {
    private val display = SyncedSmsDisplay("our.package", "Pixel", 1_000L, false)
    private fun sms(body: String) = JSONObject().put("kind", "sms").put("type", 1).put("address", " +614123 ").put("body", body)

    @Test fun incomingOtpUsesPresentationPrivacyValidityAndSharedDedupe() {
        val event = SyncedSmsPublisher.capsule("source:123", sms("您的验证码为 482913，3 分钟内有效"), RulePack.EMPTY, display)!!
        assertEquals(CapsuleKind.OTP, event.kind)
        assertEquals("sync:source:123", event.eventId)
        assertEquals("our.package", event.sourcePackage)
        assertEquals("短信 · Pixel", event.sourceLabel)
        assertEquals("验证码 · +614123", event.title)
        assertEquals("482913", event.shortText)
        assertEquals("3 分钟内有效 · 点击复制", event.body)
        assertEquals(CapsuleAction.CopySensitiveText("482913"), event.action)
        assertEquals(CapsulePrivacy.HIDE_SENSITIVE, event.privacy)
        assertEquals(181_000L, event.expiresAtMillis)
        assertEquals("sms-otp:482913", event.dedupeKey)
    }

    @Test fun otpWithoutValidityUsesFiveMinutesAndCanShowDirectly() {
        val event = SyncedSmsPublisher.capsule("source:123", sms("Your verification code is 482913"), RulePack.EMPTY,
            display.copy(showOtpDirectly = true))!!
        assertEquals("点击复制", event.body)
        assertEquals(301_000L, event.expiresAtMillis)
        assertEquals(CapsulePrivacy.SHOW_FULL, event.privacy)
        val overridden = SyncedSmsPublisher.capsule("source:124", sms("验证码 482913，3 分钟内有效"), RulePack.EMPTY,
            display.copy(otpTtlOverride = 7))!!
        assertEquals(421_000L, overridden.expiresAtMillis)
    }

    @Test fun ordinarySmsUsesTrimmedBodyFullPrivacyNoActionAndNotificationTtl() {
        val event = SyncedSmsPublisher.capsule("source:123", sms("  下午见面  \n"), RulePack.EMPTY, display.copy(plainTtlMinutes = 8))!!
        assertEquals(CapsuleKind.NOTIFICATION, event.kind)
        assertEquals("+614123", event.title)
        assertEquals("下午见面", event.shortText)
        assertEquals("下午见面", event.body)
        assertEquals(CapsuleAction.None, event.action)
        assertEquals(CapsulePrivacy.SHOW_FULL, event.privacy)
        assertEquals(481_000L, event.expiresAtMillis)
        assertEquals("sync:source:123", event.dedupeKey)
    }

    @Test fun ordinarySmsFallsBackToFiveMinutesWhenNoTtlIsAvailable() {
        val event = SyncedSmsPublisher.capsule("source:123", sms("下午见面"), RulePack.EMPTY,
            display.copy(plainTtlMinutes = 0))!!
        assertEquals(301_000L, event.expiresAtMillis)
        assertEquals(CapsuleKind.NOTIFICATION, event.kind)
    }

    @Test fun ignoresSentSmsRemoteSendCommandsAndUnknownKindsEvenWithOtp() {
        assertNull(SyncedSmsPublisher.capsule("sent", sms("验证码 482913").put("type", 2), RulePack.EMPTY, display))
        assertNull(SyncedSmsPublisher.capsule("command", JSONObject().put("kind", "send_sms"), RulePack.EMPTY, display))
        assertNull(SyncedSmsPublisher.capsule("other", sms("验证码 482913").put("kind", "mms"), RulePack.EMPTY, display))
        assertNull(SyncedSmsPublisher.capsule("missing", sms("验证码 482913").removeType(), RulePack.EMPTY, display))
    }

    private fun JSONObject.removeType(): JSONObject = apply { remove("type") }

    @Test fun ordinarySmsFollowsTheContentSettingButOtpDoesNot() {
        val hidden = display.copy(showContent = false)
        assertEquals(CapsulePrivacy.HIDE_SENSITIVE,
            SyncedSmsPublisher.capsule("source:130", sms("下午见面"), RulePack.EMPTY, hidden)!!.privacy)
        assertEquals(CapsulePrivacy.SHOW_FULL, SyncedSmsPublisher.capsule("source:131",
            sms("Your verification code is 482913"), RulePack.EMPTY, hidden.copy(showOtpDirectly = true))!!.privacy)
    }
}
