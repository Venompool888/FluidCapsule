package io.github.venompool888.fluidcapsule.parser

import io.github.venompool888.fluidcapsule.rules.CodePosition
import io.github.venompool888.fluidcapsule.rules.RulePack

sealed interface OtpParseResult {
    data class Success(
        val code: String,
        val confidence: Int,
        val validForMinutes: Int?,
    ) : OtpParseResult

    data class Ambiguous(val candidateCount: Int) : OtpParseResult
    data object None : OtpParseResult
}

object OtpParser {
    private val keywordRegex = Regex(
        "验证码|驗證碼|验证密码|驗證密碼|校验码|校驗碼|登录代码|登入代碼|登入代码|登錄代碼|动态(?:验证)?码|動態(?:驗證)?碼|" +
            "动态密码|動態密碼|一次性密码|一次性密碼|短信码|交易码|" +
            "Apple\\s*(?:账户|帳戶)\\s*(?:代码|代碼)|" +
            "(?:输入|輸入)\\s*(?:代码|代碼)(?=\\s*[0-9]{4,8}\\s*以(?:确认|確認).{0,32}(?:注册|註冊|登录|登入))|" +
            "\\botp\\b|verification\\s*(?:code|number)|verify\\s*code|passcode|" +
            "security\\s*code|secret\\s*code|login\\s*code|sign[-\\s]*in\\s*code|" +
            "authentication\\s*(?:code|number)|confirmation\\s*code|" +
            "(?:access|authorization|activation|unlock|recovery)\\s*code|" +
            "one[-\\s]*time\\s*(?:password|code)|temporary\\s*password|" +
            "[0-9]+[-\\s]*digit\\s*code|\\bpin(?:\\s*code)?\\b|pin\\s*码|" +
            "(?:delivery|verification|security|temporary)\\s*pin|" +
            "one[-\\s]*time\\s*pin|share\\s+(?:your\\s+)?pin|your\\s+pin|pin\\s+is|" +
            "your\\s+(?:login\\s+)?code|code\\s+is",
        RegexOption.IGNORE_CASE,
    )
    private val distantExplicitCodeRegex = Regex(
        "[0-9]+[-\\s]*digit\\s*code",
        RegexOption.IGNORE_CASE,
    )
    private val candidateRegex = Regex(
        "(?<![A-Za-z0-9])((?<![A-Za-z0-9-])[A-Z0-9]{3}-[A-Z0-9]{3}(?![A-Za-z0-9-])|[0-9]{4,8}|[A-Za-z0-9]{4,10})(?![A-Za-z0-9])",
    )
    private val nonOtpAlphanumericRegex = Regex(
        "^(?:[0-9]{1,2}(?:st|nd|rd|th)|[0-9]{1,4}(?:am|pm|hr|hrs|min|mins|sec|secs|day|days|wk|wks|mo|mos|yr|yrs|mb|gb|tb|kb|kg|km|cm|mm|px|ms|hz|fps))$",
    )
    private val validityRegex = Regex("([0-9]{1,2})\\s*(?:分钟|min(?:ute)?s?)", RegexOption.IGNORE_CASE)
    private val moneyContextRegex = Regex(
        "(?:¥|￥|元|金额|支付|支出|收入|余额|账单|price|amount|paid|payment)\\s*[0-9,.]*$|" +
            "^[0-9,.]*\\s*(?:元|rmb|cny|aud|usd)",
        RegexOption.IGNORE_CASE,
    )
    private val moneyTermRegex = Regex(
        "¥|￥|元|金额|支付|支出|收入|余额|账单|\\b(?:price|amount|paid|payment|rmb|cny|aud|usd)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val timeOrDateRegex = Regex("(?:^|\\D)(?:20[0-9]{2}[-/.])?[01]?[0-9][-/.:][0-3]?[0-9](?:\\D|$)")
    private val contactContextRegex = Regex(
        "拨打|致电|联系客服|客服(?:电话|热线)?|热线|详询|咨询|发送至|回复至|call|contact|hotline|customer\\s*service",
        RegexOption.IGNORE_CASE,
    )
    private val referenceContextRegex = Regex(
        "订单|订购|套餐|编号|流水号|参考号|快递|物流|运单|航班|预约|优惠|折扣|兑换|" +
            "order|reference|tracking|booking|flight|subscription|plan|promo|promotional|coupon|discount|voucher|redeem",
        RegexOption.IGNORE_CASE,
    )
    private val secrecyRegex = Regex(
        "请勿(?:泄露|告知|分享|转发)|不要(?:泄露|分享|告诉)|切勿(?:泄露|告知)|" +
            "do\\s*not\\s*share|don't\\s*share|never\\s*share|keep\\s*(?:it\\s*)?secret",
        RegexOption.IGNORE_CASE,
    )
    private val authContextRegex = Regex(
        "登录|登入|注册|重置|认证|验证身份|login|log\\s*in|sign\\s*in|register|reset|authenticate|confirm",
        RegexOption.IGNORE_CASE,
    )
    private val urlRegex = Regex("(?:https?://|www\\.)\\S+", RegexOption.IGNORE_CASE)

    fun parse(text: String, rules: RulePack = RulePack.EMPTY): OtpParseResult {
        if (rules.otpExclusions.any { text.contains(it.phrase, ignoreCase = true) }) return OtpParseResult.None
        val result = parseWithScoring(text, rules)
        // Preserve an existing confident result or ambiguity. Explicit templates
        // fill wording gaps rather than overriding the scored parser's choice.
        return if (result == OtpParseResult.None) {
            ExplicitOtpTemplates.parse(text.trim()) ?: result
        } else {
            result
        }
    }

    private fun parseWithScoring(text: String, rules: RulePack): OtpParseResult {
        val normalized = text.trim()
        if (normalized.isEmpty()) return OtpParseResult.None

        val keywords = keywordRegex.findAll(normalized).map { KeywordHit(it.range, false) }.toList() +
            rules.otpKeywords.flatMap { rule -> literalRanges(normalized, rule.phrase).map { KeywordHit(it, true) } }
        val bindings = rules.otpBindings.flatMap { rule ->
            literalRanges(normalized, rule.phrase).map { rule to it }
        }
        if (keywords.isEmpty() && bindings.isEmpty()) return OtpParseResult.None

        val candidates = candidateRegex.findAll(normalized)
            .filter { match ->
                val code = match.groupValues[1]
                !nonOtpAlphanumericRegex.matches(code) && when {
                    code.contains('-') -> code.any(Char::isDigit) && code.any(Char::isLetter)
                    else -> code.all(Char::isDigit) || (code.any(Char::isDigit) && code.any(Char::isLetter))
                }
            }
            .mapNotNull { match ->
                val code = match.groupValues[1]
                val eligibleBindings = bindings.filter { (rule, range) ->
                    minDistance(match.range, range) <= rule.maxDistance && when (rule.codePosition) {
                        CodePosition.AFTER -> match.range.first > range.last
                        CodePosition.BEFORE -> match.range.last < range.first
                    }
                }.map { KeywordHit(it.second, true) }
                val closestKeyword = (keywords + eligibleBindings)
                    .minByOrNull { keyword -> minDistance(match.range, keyword.range) }
                    ?: return@mapNotNull null
                val distance = minDistance(match.range, closestKeyword.range)
                val followsKeyword = match.range.first > closestKeyword.range.last
                val contextStart = (match.range.first - 24).coerceAtLeast(0)
                val contextEnd = (match.range.last + 25).coerceAtMost(normalized.length)
                val context = normalized.substring(contextStart, contextEnd)
                val localContextStart = (match.range.first - 10).coerceAtLeast(0)
                val localContextEnd = (match.range.last + 11).coerceAtMost(normalized.length)
                val localContext = normalized.substring(localContextStart, localContextEnd)
                if (closestKeyword.remote &&
                    (moneyTermRegex.containsMatchIn(context) ||
                        referenceContextRegex.containsMatchIn(context) ||
                        contactContextRegex.containsMatchIn(localContext) ||
                        timeOrDateRegex.findAll(normalized).any { match.range.overlaps(it.range) } ||
                        urlRegex.findAll(normalized).any { match.range.overlaps(it.range) })
                ) return@mapNotNull null
                var score = 25
                score += when {
                    followsKeyword && distance <= 4 -> 65
                    followsKeyword && distance <= 12 -> 55
                    followsKeyword && distance <= 24 -> 40
                    !followsKeyword && distance <= 4 -> 55
                    !followsKeyword && distance <= 12 -> 45
                    !followsKeyword && distance <= 24 -> 25
                    else -> 0
                }
                score += when {
                    code.length == 6 -> 15
                    code.length in 4..8 -> 8
                    else -> 5
                }
                if (code.any(Char::isLetter) && code.any(Char::isDigit)) score += 5
                val hasLetterHyphenPrefix = match.range.first >= 2 &&
                    normalized[match.range.first - 2].isLetter() &&
                    normalized[match.range.first - 1] == '-'
                if (hasLetterHyphenPrefix && distance <= 24) score += 10
                val explicitCodeDistance = distantExplicitCodeRegex.findAll(normalized)
                    .minOfOrNull { keyword -> minDistance(match.range, keyword.range) }
                if (explicitCodeDistance != null && explicitCodeDistance <= 64 && distance > 24) {
                    score += 35
                }
                if (validityRegex.containsMatchIn(context)) score += 5
                if (secrecyRegex.containsMatchIn(context)) score += 8
                if (authContextRegex.containsMatchIn(context)) score += 5
                if (moneyContextRegex.containsMatchIn(localContext) && !(followsKeyword && distance <= 4)) score -= 70
                if (timeOrDateRegex.findAll(normalized).any { match.range.overlaps(it.range) }) score -= 50
                val nearestContact = contactContextRegex.findAll(normalized)
                    .minByOrNull { contact -> minDistance(match.range, contact.range) }
                val contactDistance = nearestContact?.let { minDistance(match.range, it.range) }
                val contactBeforeCandidate = nearestContact?.range?.last?.let { it < match.range.first } == true
                if (nearestContact != null && contactDistance != null && contactDistance <= 24 &&
                    (contactBeforeCandidate || distance > 12)
                ) score -= 90
                if (referenceContextRegex.containsMatchIn(context)) score -= 60
                if (urlRegex.findAll(normalized).any { match.range.overlaps(it.range) }) score -= 100
                if (code.toSet().size == 1) score -= 20
                Candidate(code, score.coerceIn(0, 100), match.range)
            }
            .groupBy { it.code }
            .values
            .map { matches ->
                // Email previews often repeat a numeric OTP before the actual
                // "one-time passcode" label. Keep its strongest occurrence.
                // Repeated mixed tokens may be product names (for example Life360).
                if (matches.first().code.all(Char::isDigit)) {
                    matches.maxBy { it.score }
                } else {
                    matches.first()
                }
            }
            .sortedByDescending { it.score }
            .toList()

        val best = candidates.firstOrNull() ?: return OtpParseResult.None
        if (best.score < 75) return OtpParseResult.None

        val second = candidates.getOrNull(1)
        if (second != null && second.score >= 75 && best.score - second.score < 15) {
            return OtpParseResult.Ambiguous(candidates.count { it.score >= 75 })
        }

        val validity = validityRegex.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
        return OtpParseResult.Success(best.code, best.score, validity)
    }

    private fun minDistance(a: IntRange, b: IntRange): Int = when {
        a.last < b.first -> b.first - a.last
        b.last < a.first -> a.first - b.last
        else -> 0
    }

    private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last

    private fun literalRanges(text: String, phrase: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var from = 0
        while (from < text.length) {
            val index = text.indexOf(phrase, from, ignoreCase = true)
            if (index < 0) break
            ranges += index until index + phrase.length
            from = index + phrase.length
        }
        return ranges
    }

    private data class KeywordHit(val range: IntRange, val remote: Boolean)
    private data class Candidate(val code: String, val score: Int, val range: IntRange)
}
