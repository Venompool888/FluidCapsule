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
        "验证码|校验码|动态码|一次性密码|短信码|otp|verification\\s*code|verify\\s*code|passcode|security\\s*code",
        RegexOption.IGNORE_CASE,
    )
    private val candidateRegex = Regex("(?<![\\p{L}\\p{N}])([0-9]{4,8})(?![\\p{L}\\p{N}])")
    private val validityRegex = Regex("([0-9]{1,2})\\s*(?:分钟|min(?:ute)?s?)", RegexOption.IGNORE_CASE)
    private val moneyContextRegex = Regex("(?:¥|￥|元|金额|支付|支出|收入)\\s*[0-9,.]*$|^[0-9,.]*\\s*(?:元|rmb|cny)", RegexOption.IGNORE_CASE)
    private val timeOrDateRegex = Regex("(?:^|\\D)(?:20[0-9]{2}[-/.])?[01]?[0-9][-/.:][0-3]?[0-9](?:\\D|$)")

    fun parse(text: String): OtpParseResult {
        val normalized = text.trim()
        if (normalized.isEmpty()) return OtpParseResult.None

        val keywords = keywordRegex.findAll(normalized).toList()
        if (keywords.isEmpty()) return OtpParseResult.None

        val candidates = candidateRegex.findAll(normalized)
            .map { match ->
                val code = match.groupValues[1]
                val closestKeyword = keywords.minBy { keyword -> minDistance(match.range, keyword.range) }
                val distance = minDistance(match.range, closestKeyword.range)
                val followsKeyword = match.range.first > closestKeyword.range.last
                val contextStart = (match.range.first - 12).coerceAtLeast(0)
                val contextEnd = (match.range.last + 13).coerceAtMost(normalized.length)
                val context = normalized.substring(contextStart, contextEnd)
                var score = 25
                score += when {
                    followsKeyword && distance <= 4 -> 65
                    followsKeyword && distance <= 12 -> 55
                    followsKeyword && distance <= 24 -> 40
                    !followsKeyword && distance <= 4 -> 40
                    !followsKeyword && distance <= 12 -> 30
                    distance <= 48 -> 15
                    else -> 0
                }
                if (code.length in 4..6) score += 10
                if (validityRegex.containsMatchIn(normalized)) score += 5
                if (moneyContextRegex.containsMatchIn(context)) score -= 55
                if (timeOrDateRegex.containsMatchIn(context)) score -= 35
                if (code.toSet().size == 1) score -= 20
                Candidate(code, score.coerceIn(0, 100), match.range)
            }
            .distinctBy { it.code }
            .sortedByDescending { it.score }
            .toList()

        val best = candidates.firstOrNull() ?: return OtpParseResult.None
        if (best.score < 70) return OtpParseResult.None

        val second = candidates.getOrNull(1)
        if (second != null && second.score >= 70 && best.score - second.score < 12) {
            return OtpParseResult.Ambiguous(candidates.count { it.score >= 70 })
        }

        val validity = validityRegex.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
        return OtpParseResult.Success(best.code, best.score, validity)
    }

    private fun minDistance(a: IntRange, b: IntRange): Int = when {
        a.last < b.first -> b.first - a.last
        b.last < a.first -> a.first - b.last
        else -> 0
    }

    private data class Candidate(val code: String, val score: Int, val range: IntRange)
}
