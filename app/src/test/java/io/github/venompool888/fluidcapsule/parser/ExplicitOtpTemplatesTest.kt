package io.github.venompool888.fluidcapsule.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplicitOtpTemplatesTest {
    @Test
    fun parsesSmsCodeAndValidity() {
        val result = ExplicitOtpTemplates.parse(
            "Enter SMS Code: 482913 to confirm the payment. Valid for 3 minutes.",
        )

        assertSuccess(result, "482913", 3)
    }

    @Test
    fun parsesNetCodeAcrossInstructionText() {
        val result = ExplicitOtpTemplates.parse(
            "Never tell anyone this code, including the bank. Your NetCode to register the app is 481205.",
        )

        assertSuccess(result, "481205")
    }

    @Test
    fun parsesTelegramPaytrAndMexcLabels() {
        assertSuccess(
            ExplicitOtpTemplates.parse("Telegram code: 516204 You can also tap this link to log in."),
            "516204",
        )
        assertSuccess(
            ExplicitOtpTemplates.parse("PAYTR code: 604218. Valid for 3 minutes."),
            "604218",
        )
        assertSuccess(
            ExplicitOtpTemplates.parse("[MEXC] Code: 739104. You requested to link your phone number."),
            "739104",
        )
    }

    @Test
    fun normalizesWhatsAppAndRevolutThreeThreeCodes() {
        assertSuccess(
            ExplicitOtpTemplates.parse("Your WhatsApp code: 482-913. Don't share this code."),
            "482913",
        )
        assertSuccess(
            ExplicitOtpTemplates.parse("Authorise a login. Use code: 604 218. Never share it. Revolut"),
            "604218",
        )
    }

    @Test
    fun ignoresUrlAndEmailHashAfterExplicitCode() {
        val result = ExplicitOtpTemplates.parse(
            "[WeChat] Use the code (725604) on WeChat to log in: " +
                "https://example.invalid/login/w0lkcmTZkKh @example.invalid #w0lkcmTZkKh",
        )

        assertSuccess(result, "725604")
    }

    @Test
    fun parsesBrandBeforeAndAfterCode() {
        assertSuccess(
            ExplicitOtpTemplates.parse("482913 is your Flybuys verification code."),
            "482913",
        )
        assertSuccess(
            ExplicitOtpTemplates.parse("[Uber] Your code: 604218. Never share this code."),
            "604218",
        )
        assertSuccess(
            ExplicitOtpTemplates.parse("Here's the one-time code. Your code is: 739104. Boost Mobile"),
            "739104",
        )
    }

    @Test
    fun parsesGermanTelekomAndGooglePrefixWithoutPrefix() {
        assertSuccess(
            ExplicitOtpTemplates.parse("Der Bestätigungscode zur Vertragsverknüpfung lautet 516204."),
            "516204",
        )
        assertSuccess(
            ExplicitOtpTemplates.parse("G-604218 is your Google verification code."),
            "604218",
        )
    }

    @Test
    fun parsesAuthenticatorAndOctopusActionTemplates() {
        assertSuccess(
            ExplicitOtpTemplates.parse("To disable or move your Mobile Authenticator use code: 739104"),
            "739104",
        )
        assertSuccess(
            ExplicitOtpTemplates.parse("Octopus: Don't share this Verification Code. Enter LDs-516204."),
            "516204",
        )
    }

    @Test
    fun returnsAmbiguousForTwoDifferentExplicitCandidates() {
        val result = ExplicitOtpTemplates.parse(
            "SMS Code: 482913. A second SMS Code: 604218 was also issued.",
        )

        assertTrue(result is OtpParseResult.Ambiguous)
        assertEquals(2, (result as OtpParseResult.Ambiguous).candidateCount)
    }

    @Test
    fun parsesActionBoundCodesWithoutAnAdjacentBrandLabel() {
        listOf(
            "482913 is your verification,expires in 4 hours!",
            "Your code for authorization in the online chat: 482913",
            "To finish creating your PayID, enter the code 482913 in-app",
            "Hi, your code to transfer to Telstra is 482913. It expires in 24 hours.",
            "[WeChat] Your Weixin is linking or verifying mobile number (482913). Don't forward the code!",
            "Enter 482913 SMS Code to complete your cardless deposit.",
        ).forEach { assertSuccess(ExplicitOtpTemplates.parse(it), "482913") }
    }

    @Test
    fun preservesAlphanumericCodesAndIgnoresUrlCandidates() {
        assertSuccess(ExplicitOtpTemplates.parse("Telegram code: G482913"), "G482913")
        assertNull(ExplicitOtpTemplates.parse("Visit https://example.invalid/OTP:482913"))
        assertSuccess(
            ExplicitOtpTemplates.parse("Telegram verification code: 482913. Promo code SAVE20 is unrelated."),
            "482913",
        )
    }

    @Test
    fun rejectsNonAuthCodesAndStatusNotifications() {
        val rejected = listOf(
            "Use promo code SAVE20 to receive a discount on your next order.",
            "BPay Biller Code: 123456. Pay the outstanding amount.",
            "Apple Store pickup code is available in your order wallet.",
            "Your device has been enrolled to receive Security Codes via the app. Call support 1234567.",
            "Click here to enroll Okta Verify: https://example.invalid/enroll/w0lkcmTZkKh",
            "Your account order number is 20260809123456.",
            "Apple Store pickup code: 482913",
            "Westpac BPay Biller Code: 482913",
            "Uber promo code SAVE20",
            "Google verification code support hotline 10086",
            "Your Internet Banking Customer Access Number is 73918426.",
        )

        rejected.forEach { assertNull(ExplicitOtpTemplates.parse(it)) }
    }

    @Test
    fun rejectsPhoneAndEmailValuesWithoutExplicitOtpBinding() {
        assertNull(
            ExplicitOtpTemplates.parse(
                "Your contact number is 0412345678. Email support@example.invalid for help.",
            ),
        )
    }

    @Test
    fun parsesHyphenatedAlphanumericAndNewActionBoundTemplates() {
        assertSuccess(ExplicitOtpTemplates.parse("Slack confirmation code: 7AB-C4D"), "7AB-C4D")
        assertNull(ExplicitOtpTemplates.parse("Slack confirmation code: opt-2fa"))
        assertNull(ExplicitOtpTemplates.parse("Slack confirmation code: PRE-PAY"))

        assertSuccess(
            ExplicitOtpTemplates.parse("Your ServiceX code to link your account to ShopY is: 482913"),
            "482913",
        )
        assertSuccess(
            ExplicitOtpTemplates.parse(
                "Your verification code\nHello Alex\n" +
                    "Here is the code required to complete your form on the Careers website.\nAB12C",
            ),
            "AB12C",
        )
        assertSuccess(ExplicitOtpTemplates.parse("Use code 482913 to log in"), "482913")
    }

    private fun assertSuccess(result: OtpParseResult?, expectedCode: String, expectedMinutes: Int? = null) {
        assertTrue("Expected explicit OTP success but got $result", result is OtpParseResult.Success)
        result as OtpParseResult.Success
        assertEquals(expectedCode, result.code)
        if (expectedMinutes != null) assertEquals(expectedMinutes, result.validForMinutes)
    }
}
