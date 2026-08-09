package io.github.venompool888.fluidcapsule.parser

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
        "验证码|验证密码|校验码|动态(?:验证)?码|动态密码|一次性密码|短信码|交易码|" +
            "otp|verification\\s*code|verify\\s*code|passcode|security\\s*code|" +
            "login\\s*code|authentication\\s*code|confirmation\\s*code|" +
            "one[-\\s]*time\\s*(?:password|code)|your\\s+(?:login\\s+)?code|code\\s+is",
        RegexOption.IGNORE_CASE,
    )
    private val candidateRegex = Regex(
        "(?<![\\p{L}\\p{N}])([0-9]{4,8}|[A-Za-z0-9]{4,10})(?![\\p{L}\\p{N}])",
    )
    private val validityRegex = Regex("([0-9]{1,2})\\s*(?:分钟|min(?:ute)?s?)", RegexOption.IGNORE_CASE)
    private val moneyContextRegex = Regex(
        "(?:¥|￥|元|金额|支付|支出|收入|余额|账单|price|amount|paid|payment)\\s*[0-9,.]*$|" +
            "^[0-9,.]*\\s*(?:元|rmb|cny|aud|usd)",
        RegexOption.IGNORE_CASE,
    )
    private val timeOrDateRegex = Regex("(?:^|\\D)(?:20[0-9]{2}[-/.])?[01]?[0-9][-/.:][0-3]?[0-9](?:\\D|$)")
    private val contactContextRegex = Regex(
        "拨打|致电|联系客服|客服(?:电话|热线)?|热线|发送至|回复至|call|contact|hotline|customer\\s*service",
        RegexOption.IGNORE_CASE,
    )
    private val referenceContextRegex = Regex(
        "订单|订购|套餐|编号|流水号|参考号|快递|物流|运单|航班|预约|" +
            "order|reference|tracking|booking|flight|subscription|plan",
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

    fun parse(text: String): OtpParseResult {
        val normalized = text.trim()
        if (normalized.isEmpty()) return OtpParseResult.None

        val keywords = keywordRegex.findAll(normalized).toList()
        if (keywords.isEmpty()) return OtpParseResult.None

        val candidates = candidateRegex.findAll(normalized)
            .filter { match ->
                val code = match.groupValues[1]
                code.all(Char::isDigit) ||
                    (code.any(Char::isDigit) && code.any(Char::isLetter))
            }
            .map { match ->
                val code = match.groupValues[1]
                val closestKeyword = keywords.minBy { keyword -> minDistance(match.range, keyword.range) }
                val distance = minDistance(match.range, closestKeyword.range)
                val followsKeyword = match.range.first > closestKeyword.range.last
                val contextStart = (match.range.first - 24).coerceAtLeast(0)
                val contextEnd = (match.range.last + 25).coerceAtMost(normalized.length)
                val context = normalized.substring(contextStart, contextEnd)
                val localContextStart = (match.range.first - 10).coerceAtLeast(0)
                val localContextEnd = (match.range.last + 11).coerceAtMost(normalized.length)
                val localContext = normalized.substring(localContextStart, localContextEnd)
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
                if (validityRegex.containsMatchIn(context)) score += 5
                if (secrecyRegex.containsMatchIn(context)) score += 8
                if (authContextRegex.containsMatchIn(context)) score += 5
                if (moneyContextRegex.containsMatchIn(localContext)) score -= 70
                if (timeOrDateRegex.findAll(normalized).any { match.range.overlaps(it.range) }) score -= 50
                if (contactContextRegex.containsMatchIn(context)) score -= 90
                if (referenceContextRegex.containsMatchIn(context)) score -= 60
                if (urlRegex.findAll(normalized).any { match.range.overlaps(it.range) }) score -= 100
                if (code.toSet().size == 1) score -= 20
                Candidate(code, score.coerceIn(0, 100), match.range)
            }
            .distinctBy { it.code }
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

    private data class Candidate(val code: String, val score: Int, val range: IntRange)
}
