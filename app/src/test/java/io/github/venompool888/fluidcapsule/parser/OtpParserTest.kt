package io.github.venompool888.fluidcapsule.parser

import io.github.venompool888.fluidcapsule.rules.CodePosition
import io.github.venompool888.fluidcapsule.rules.LiteralRule
import io.github.venompool888.fluidcapsule.rules.OtpBindingRule
import io.github.venompool888.fluidcapsule.rules.RulePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpParserTest {
    @Test
    fun remoteLiteralAndBindingsCaptureOnlyRealCodeContexts() {
        val rules = RulePack.EMPTY.copy(
            otpKeywords = listOf(LiteralRule("secure", "secure number")),
            otpBindings = listOf(
                OtpBindingRule("after", "secure number", CodePosition.AFTER, 12),
                OtpBindingRule("before", "secure number", CodePosition.BEFORE, 12),
            ),
        )
        assertEquals("482913", (OtpParser.parse("Your secure number is 482913", rules) as OtpParseResult.Success).code)
        assertEquals("482913", (OtpParser.parse("482913 is your secure number", rules) as OtpParseResult.Success).code)
        assertEquals(OtpParseResult.None, OtpParser.parse("Your order number is 482913", rules))
        val broad = rules.copy(otpKeywords = listOf(LiteralRule("order", "order number")))
        assertEquals(OtpParseResult.None, OtpParser.parse("Your order number is 482913", broad))
        val money = rules.copy(otpKeywords = listOf(LiteralRule("amount", "Amount")))
        assertEquals(OtpParseResult.None, OtpParser.parse("Amount 482913 USD", money))
    }

    @Test
    fun remoteExclusionSuppressesOtherwiseValidCode() {
        val rules = RulePack.EMPTY.copy(otpExclusions = listOf(LiteralRule("deny", "test promotion")))
        assertEquals(OtpParseResult.None, OtpParser.parse("Your verification code is 482913. Test promotion.", rules))
    }

    @Test
    fun parsesChineseOtp() {
        val result = OtpParser.parse("您的验证码为 482913，5 分钟内有效")
        assertTrue(result is OtpParseResult.Success)
        result as OtpParseResult.Success
        assertEquals("482913", result.code)
        assertEquals(5, result.validForMinutes)
    }

    @Test
    fun parsesChineseOtpAdjacentToLabel() {
        val result = OtpParser.parse("支付宝验证码482913，请勿泄露。唯一热线 95188")
        assertEquals("482913", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesChineseOtpBeforeVerificationPhrase() {
        val result = OtpParser.parse("482913是您的登录验证码，请勿向他人泄露")
        assertEquals("482913", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesChineseTransactionCodeAdjacentToLabel() {
        val result = OtpParser.parse("手机交易码482913，请勿告知他人")
        assertEquals("482913", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesTraditionalOneTimePassword() {
        val result = OtpParser.parse("你的一次性密碼為482913。")
        assertEquals("482913", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesTraditionalOneTimeVerificationCode() {
        val result = OtpParser.parse("你的一次性驗證碼為：482913，十分鐘後失效。")
        assertEquals("482913", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesAppleAccountCode() {
        val result = OtpParser.parse("Apple 账户代码为：482913。请勿与他人共享。")
        assertEquals("482913", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesInputCodeForRegistration() {
        val result = OtpParser.parse("輸入代碼482913以確認你的帳戶註冊電話號碼；切勿向他人提供此代碼。")
        assertEquals("482913", (result as OtpParseResult.Success).code)
    }

    @Test
    fun rejectsGenericAccountIdentifiersAndChinesePromotions() {
        listOf(
            "您的账户代码为482913，请保存用于账单查询。",
            "输入代码SAVE20享受优惠。",
            "輸入代碼482913領取優惠券。",
            "验证码咨询热线10086",
        ).forEach { assertEquals(it, OtpParseResult.None, OtpParser.parse(it)) }
    }

    @Test
    fun keepsCodeAfterAnEarlierSupportWarning() {
        val result = OtpParser.parse(
            "If you did not request this, call 13 22 00. " +
                "Beware of scam calls and never disclose your personal details. Your code is 482913.",
        )
        assertEquals("482913", (result as OtpParseResult.Success).code)
    }

    @Test
    fun explicitFallbackDoesNotPromoteAccountNumbersOrBrandedPromotions() {
        listOf(
            "Your Internet Banking Customer Access Number is 73918426.",
            "Apple Store pickup code: 482913",
            "Westpac BPay Biller Code: 482913",
            "Uber promo code SAVE20",
        ).forEach { assertEquals(it, OtpParseResult.None, OtpParser.parse(it)) }
    }

    @Test
    fun extractsMessageCodeInsteadOfAnAlphanumericBrandHeading() {
        val result = OtpParser.parse(
            "From another device · Life360\n" +
                "482913 is your one-time Life360 verification code. Please do not share this with anyone.",
        )
        assertEquals("482913", (result as OtpParseResult.Success).code)
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
    fun rejectsServiceNumberAfterOtpSafetyReminder() {
        val result = OtpParser.parse("请勿向任何人提供验证码，如有疑问详询客服热线 10086")
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
    fun parsesHyphenatedPrefixedOtp() {
        val result = OtpParser.parse("G-482913 是您的 Google 验证码。请勿分享此码。")
        assertEquals("482913", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesSecretCode() {
        val result = OtpParser.parse(
            "Enter the following code to confirm the login attempt. Secret code: 90779837",
        )
        assertEquals("90779837", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesSixDigitCodeSeparatedFromKeywordByAppName() {
        val result = OtpParser.parse(
            "Verify your email. Use this 6-digit code in the felix mobile app: 418428. " +
                "This code is valid for 60 mins.",
        )
        assertEquals("418428", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesDeliveryPin() {
        val result = OtpParser.parse(
            "Time to meet BEHNAM. Tell them your PIN is 1662 to confirm the delivery.",
        )
        assertEquals("1662", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesBarePin() {
        val result = OtpParser.parse("PIN: 4831")
        assertEquals("4831", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesAccessCode() {
        val result = OtpParser.parse("Your access code is 745920. It expires in 10 minutes.")
        assertEquals("745920", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesVerificationNumber() {
        val result = OtpParser.parse("Verification number: 583104")
        assertEquals("583104", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesTemporaryPassword() {
        val result = OtpParser.parse(
            "Your login information is: Temporary Password: 0205. " +
                "Next time you login, you will be prompted to change your password.",
        )
        assertEquals("0205", (result as OtpParseResult.Success).code)
    }

    @Test
    fun rejectsPromoCode() {
        val result = OtpParser.parse("Use promo code SAVE20 to receive 20% off your next order.")
        assertEquals(OtpParseResult.None, result)
    }

    @Test
    fun rejectsSubmissionConfirmationNumber() {
        val result = OtpParser.parse(
            "Assignment submitted successfully. Confirmation number: " +
                "92f28db62fb54153ad411a53cc9c8a6c. Submitted in 2026.",
        )
        assertEquals(OtpParseResult.None, result)
    }

    @Test
    fun rejectsTemporaryPasswordPolicyYear() {
        val result = OtpParser.parse("The temporary password policy was updated in 2026.")
        assertEquals(OtpParseResult.None, result)
    }

    @Test
    fun rejectsInformationalOtpWarningWithoutCode() {
        val result = OtpParser.parse("警方提醒：不要向陌生人泄露验证码或开启屏幕共享")
        assertEquals(OtpParseResult.None, result)
    }

    @Test
    fun rejectsPickupCodeAsOtp() {
        val result = OtpParser.parse("请凭取件码 1234-5678 到驿站取货")
        assertEquals(OtpParseResult.None, result)
    }

    @Test
    fun parsesUnidaysPasscodeWhenRepeatedInEmailPreview() {
        val result = OtpParser.parse(
            "482913 is your UNiDAYS passcode\nOne-time passcode\n482913\n" +
                "Here is your passcode.\nIt will expire in 5 minutes.",
        )
        assertEquals("482913", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesChineseLoginCodeEmail() {
        val result = OtpParser.parse(
            "登录代码：482913\n登入代码\n以下是你的登入代码：\n482913\n此代码将很快过期。",
        )
        assertEquals("482913", (result as OtpParseResult.Success).code)
    }

    @Test
    fun parsesHyphenatedAlphanumericAndNumericThreeThreeCodes() {
        val slack = OtpParser.parse(
            "Slack confirmation code: 7AB-C4D Enter this code to join Workspace in Slack.",
        )
        assertEquals("7AB-C4D", (slack as OtpParseResult.Success).code)

        val whatsapp = OtpParser.parse("WhatsApp Verification Code: 482-913")
        assertEquals("482913", (whatsapp as OtpParseResult.Success).code)

        val googlePrefixed = OtpParser.parse("G-482913 is your Google verification code.")
        assertEquals("482913", (googlePrefixed as OtpParseResult.Success).code)
    }

    @Test
    fun parsesAccountLinkingAndDistantVerificationEmailsAndShortUseCode() {
        val linking = OtpParser.parse(
            "Your Afterpay code to link your account to Store.example is: 482913 " +
                "If you did not request this code, no action is required.",
        )
        assertEquals("482913", (linking as OtpParseResult.Success).code)

        val distant = OtpParser.parse(
            "Your verification code\nHello Alex\n" +
                "Here is the code required to complete your form on the Careers website.\nAB12C\n" +
                "This email was generated because of a form submitted on the Careers website.",
        )
        assertEquals("AB12C", (distant as OtpParseResult.Success).code)

        val shortUseCode = OtpParser.parse("Use code 482913 to log in")
        assertEquals("482913", (shortUseCode as OtpParseResult.Success).code)
    }

    @Test
    fun rejectsOrdinalDatesAndUnitTokensNearOtpLabels() {
        assertEquals(
            OtpParseResult.None,
            OtpParser.parse("Redeem your code before October 31st TRYPROMO250"),
        )
        val withDuration = OtpParser.parse("Your OTP expires in 10mins: 482913")
        assertEquals("482913", (withDuration as OtpParseResult.Success).code)
    }
}
