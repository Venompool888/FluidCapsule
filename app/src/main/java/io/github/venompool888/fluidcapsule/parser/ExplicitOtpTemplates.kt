package io.github.venompool888.fluidcapsule.parser

/**
 * Small, high precision templates for SMS formats whose wording binds a value
 * to an authentication action. A null result means that the normal parser may
 * try the message; this helper never treats a bare occurrence of "code" as an
 * OTP.
 */
object ExplicitOtpTemplates {
    private val IGNORE_CASE = RegexOption.IGNORE_CASE
    private const val RAW_CODE =
        "(?:\\d{3}[-\\s]\\d{3}|(?<![A-Za-z0-9-])[A-Za-z0-9]{3}-[A-Za-z0-9]{3}(?![A-Za-z0-9-])|\\d{4,8}|[A-Za-z0-9]{4,10})"

    // Keep G outside the returned group: Google messages commonly use G-123456
    // as a transport prefix, while the OTP is the numeric part.
    private const val CODE =
        "(?<![A-Za-z0-9])(?:(?:G-)(?<gcode>\\d{4,8})|(?<code>$RAW_CODE))(?![A-Za-z0-9])"

    private const val KNOWN_BRANDS =
        "(?:Uber|Boost(?:\\s+Mobile)?|Telstra|WeChat|Weixin|WhatsApp|Telegram|" +
            "Revolut|PAYTR|MEXC|Woolworths|Flybuys|Monese|Gojek|CBA|NetBank|" +
            "ING|Samsung|CoinJar|Shop|Octopus|Wise|Life360|Facebook|Instagram|" +
            "Telekom|DoorDash|Microsoft|CoinFlip|Westpac|Swyftx|Google|Apple|" +
            "Afterpay|Slack|Meta|PayPal)"

    private val templates = listOf(
        // Explicit labels used by the high-volume English senders.
        Regex("\\bSMS[-\\s]*code\\b\\s*(?:is|:|：|-)?\\s*$CODE", IGNORE_CASE),
        Regex("\\benter\\s+$CODE\\s+SMS\\s+code\\b", IGNORE_CASE),
        Regex("$CODE\\s+is\\s+your\\s+verification\\s*,\\s*expires\\s+in\\b", IGNORE_CASE),
        Regex("\\byour\\s+code\\s+for\\s+authorization[^\\n]{0,100}?[:：]\\s*$CODE", IGNORE_CASE),
        Regex("\\bfinish\\s+creating\\s+your\\s+PayID[^\\n]{0,40}?enter\\s+the\\s+code\\s+$CODE", IGNORE_CASE),
        Regex("\\byour\\s+code\\s+to\\s+transfer\\s+to\\s+Telstra\\s+is\\s+$CODE", IGNORE_CASE),
        Regex("\\bNetCode\\b[^\\n]{0,100}?(?:\\bis(?:\\s*[:：=])?|[:：=])\\s*$CODE", IGNORE_CASE),
        Regex("\\bTelegram\\s+code\\b\\s*(?:is|:|：|-)?\\s*$CODE", IGNORE_CASE),
        Regex("\\bPAYTR\\s+code\\b\\s*(?:is|:|：|-)?\\s*$CODE", IGNORE_CASE),
        Regex("(?:\\[MEXC\\]|\\bMEXC\\b)[^\\n]{0,24}?\\bcode\\b\\s*(?:is|:|：|-)?\\s*$CODE", IGNORE_CASE),
        Regex("\\b(?:your\\s+)?WhatsApp\\s+code\\b\\s*(?:is|:|：|-)?\\s*$CODE", IGNORE_CASE),
        Regex("\\b(?:CoinFlip)\\s+code\\b\\s*(?:is|:|：|-)?\\s*$CODE", IGNORE_CASE),

        // WeChat puts the code in parentheses, often next to a URL/hash.
        Regex("\\b(?:use\\s+the\\s+code|(?:WeChat|Weixin)[^\\n]{0,80}?code)\\s*\\(\\s*$CODE\\s*\\)", IGNORE_CASE),
        Regex("\\(\\s*$CODE\\s*\\)\\s+code\\s+to\\s+be\\s+used\\s+once\\b", IGNORE_CASE),
        Regex("\\b(?:WeChat|Weixin)[^\\n]{0,80}?\\b(?:linking|verifying)\\s+mobile\\s+number\\s*\\(\\s*$CODE\\s*\\)", IGNORE_CASE),
        Regex("\\b(?:WeChat|Weixin)[^\\n]{0,80}?\\b(?:code|verification)\\b[^\\n]{0,80}?(?:\\bis(?:\\s*[:：])?|[:：])\\s*$CODE", IGNORE_CASE),

        // Revolut's 3-3 code is intentionally normalized below.
        Regex("\\b(?:authori[sz]e\\s+a\\s+login|Revolut)[^\\n]{0,80}?\\buse\\s+code\\b\\s*(?:is|:|：|-)?\\s*$CODE", IGNORE_CASE),

        // German Telekom formats use a compound word and the verb "lautet".
        Regex("\\bBestätigungscode\\b[^\\n]{0,100}?\\b(?:lautet|ist)\\s*$CODE", IGNORE_CASE),
        Regex("\\bSMS[-\\s]*Code\\b[^\\n]{0,140}?\\b(?:lautet|ist)\\s*$CODE", IGNORE_CASE),

        // OTP is explicit and is only accepted when followed by a bound value.
        Regex("\\bOTP\\b\\s*(?:is|:|：|-)?\\s*$CODE", IGNORE_CASE),

        // Labels with an authentication meaning. The bounded suffix supports
        // templates such as "verification code for registration: 123456".
        Regex(
            "\\b(?:verification|confirmation|security|login|single[-\\s]*use|one[-\\s]*time|" +
                "authentication|authorization|activation|transfer|access|secret|recovery)" +
                "\\s+(?:code|pin)\\b\\s*(?:is|:|：)?\\s*$CODE",
            IGNORE_CASE,
        ),
        Regex(
            "\\b(?:verification|confirmation|security|authentication|authorization|activation|" +
                "transfer|access|secret|recovery)\\s+(?:code|pin)\\b[^\\n]{0,100}?" +
                "(?:is|:|：)\\s*$CODE",
            IGNORE_CASE,
        ),

        // Known brands allow the code label to be separated from the brand by
        // normal explanatory text, but still require an explicit bound value.
        Regex(
            "\\b$KNOWN_BRANDS\\b[\\s\\S]{0,240}?\\bcode\\b\\s*(?:(?:is|:|：)\\s*)?$CODE",
            IGNORE_CASE,
        ),
        Regex(
            "\\b$KNOWN_BRANDS\\b[\\s\\S]{0,240}?\\bcode\\b[^\\n]{0,80}?" +
                "(?:\\bis(?:\\s*[:：])?|[:：])\\s*$CODE",
            IGNORE_CASE,
        ),
        Regex(
            "\\b(?:your|this|the)\\s+(?:one[-\\s]*time\\s+)?code\\b\\s*" +
                "(?:is\\s*)?[:：]\\s*$CODE[\\s\\S]{0,120}?\\b$KNOWN_BRANDS\\b",
            IGNORE_CASE,
        ),

        // Code-before-brand messages, for example "123456 is your Flybuys
        // verification code" and "123456 is your Instagram code".
        Regex(
            "$CODE\\s+is\\s+your\\s+(?:[A-Za-z0-9&.'’/-]+\\s+){0,5}" +
                "(?:one[-\\s]*time\\s+|single[-\\s]*use\\s+)?" +
                "(?:login|verification|confirmation|security|authentication|authorization|account|" +
                "sign[-\\s]*in)\\s+(?:code|number)\\b",
            IGNORE_CASE,
        ),
        Regex(
            "$CODE\\s+(?:is\\s+)?(?:your\\s+)?$KNOWN_BRANDS(?:\\s+account)?\\s+code\\b",
            IGNORE_CASE,
        ),

        // A few action-shaped templates have an explicit verb but no brand
        // immediately beside the label.
        Regex(
            "\\b(?:enter|input|type|use)\\s+(?:the\\s+)?(?:verification|security|login|one[-\\s]*time)" +
                "\\s+code\\b\\s*(?:is|:|：)?\\s*$CODE",
            IGNORE_CASE,
        ),
        Regex(
            "\\b(?:confirm|approve|complete)\\s+(?:your\\s+)?(?:payment|transaction|transfer)" +
                "[^\\n]{0,100}?\\b(?:using|with|enter)\\s+(?:the\\s+)?code\\b" +
                "\\s*(?:is|:|：)?\\s*$CODE",
            IGNORE_CASE,
        ),
        Regex(
            "\\b(?:authenticator|mobile\\s+authenticator)\\b[^\\n]{0,100}?" +
                "\\buse\\s+code\\b\\s*(?:is|:|：|-)?\\s*$CODE",
            IGNORE_CASE,
        ),
        Regex(
            "\\b(?:approve|confirm|verify)\\b[^\\n]{0,120}?\\bwith\\s+code\\b" +
                "\\s*(?:is|:|：)?\\s*$CODE",
            IGNORE_CASE,
        ),
        Regex(
            "\\byour\\s+(?:[A-Za-z0-9&.'’/-]+\\s+){0,3}code\\s+to\\s+" +
                "(?:link|verify|confirm|log\\s*in|sign\\s*in|connect|register|reset|authori[sz]e|authenticate|complete|access)" +
                "[^\\n]{0,80}?(?:\\bis(?:\\s*[:：])?|[:：])\\s*$CODE",
            IGNORE_CASE,
        ),
        Regex(
            "\\b(?:verification|confirmation|security|login|sign[-\\s]*in|one[-\\s]*time|single[-\\s]*use)" +
                "\\s+code\\b[\\s\\S]{0,160}?\\bhere\\s+is\\s+(?:your|the)\\s+(?:one[-\\s]*time\\s+|verification\\s+)?code\\b" +
                "[^\\n]{0,120}?[.!?:：\\n]\\s*$CODE",
            IGNORE_CASE,
        ),
        Regex(
            "\\b(?:use|enter)\\s+(?:the\\s+|this\\s+)?code\\s*(?:[:：]\\s*)?$CODE\\s+to\\s+" +
                "(?:log\\s*in|sign\\s*in|verify|confirm|continue)\\b",
            IGNORE_CASE,
        ),

        // Octopus prefixes the numeric value with a fixed product marker;
        // return the numeric value itself.
        Regex("\\benter\\s+LDs-\\s*$CODE", IGNORE_CASE),
    )

    private val validityRegex = Regex("\\b(\\d{1,2})\\s*(?:minutes?|mins?)\\b", IGNORE_CASE)
    private val numericSegmentRegex = Regex("\\d{3}[-\\s]\\d{3}")
    private val urlRegex = Regex("(?:https?://|www\\.)\\S+", IGNORE_CASE)
    private val nonAuthLabelRegex = Regex(
        "(?:(?:customer\\s+access|account|order|biller|promo|promotional|discount|coupon|" +
            "voucher|gift|referral|pickup|pick-up|postal|zip|booking|reference|tracking)\\s+(?:code|number)|" +
            "redeem\\s+(?:your\\s+|this\\s+|the\\s+)?code)" +
            "\\s*(?:is\\s*)?[:：=-]?\\s*$",
        IGNORE_CASE,
    )
    private val nonOtpAlphanumericRegex = Regex(
        "^(?:[0-9]{1,2}(?:st|nd|rd|th)|[0-9]{1,4}(?:am|pm|hr|hrs|min|mins|sec|secs|day|days|wk|wks|mo|mos|yr|yrs|mb|gb|tb|kb|kg|km|cm|mm|px|ms|hz|fps))$",
    )

    fun parse(text: String): OtpParseResult? {
        if (text.isBlank()) return null

        val candidates = templates
            .asSequence()
            .flatMap { template ->
                template.findAll(text).asSequence().mapNotNull { candidateFrom(it, text) }
            }
            .distinctBy { it.code }
            .toList()

        if (candidates.isEmpty()) return null
        if (candidates.size > 1) return OtpParseResult.Ambiguous(candidates.size)

        return OtpParseResult.Success(
            code = candidates.single().code,
            confidence = 100,
            validForMinutes = validityRegex.find(text)?.groupValues?.get(1)?.toIntOrNull(),
        )
    }

    private data class Candidate(val code: String)

    private fun candidateFrom(match: MatchResult, text: String): Candidate? {
        val group = match.groups["gcode"] ?: match.groups["code"] ?: return null
        if (urlRegex.findAll(text).any { group.range.first in it.range }) return null
        val preceding = text.substring((group.range.first - 80).coerceAtLeast(0), group.range.first)
        if (nonAuthLabelRegex.containsMatchIn(preceding)) return null
        val raw = group.value
        if (raw.contains('-') && !numericSegmentRegex.matches(raw)) {
            if (raw.any(Char::isLowerCase) || !raw.any(Char::isDigit) || !raw.any(Char::isLetter)) return null
        }
        val normalized = if (numericSegmentRegex.matches(raw)) {
            raw.filterNot { it == '-' || it.isWhitespace() }
        } else {
            raw
        }

        // The alphanumeric branch is useful for providers that mix letters
        // and digits, but never accept a plain word or a numeric phone/order
        // value outside the OTP length range.
        if (nonOtpAlphanumericRegex.matches(normalized)) return null
        if (normalized.length !in 4..10) return null
        if (normalized.all(Char::isLetter)) return null
        if (normalized.all(Char::isDigit) && normalized.length !in 4..8) return null
        return Candidate(normalized)
    }

}
