package io.github.venompool888.fluidcapsule.parser

import io.github.venompool888.fluidcapsule.rules.RulePack
import java.net.URI

data class VerificationLinkRequest(val url: String?)

/** Recognizes an unfinished verification action, then binds at most one nearby web URL. */
object VerificationLinkParser {
    private val requestPatterns = listOf(
        Regex(
            "\\b(?:verify|confirm|activate)\\s+(?:your\\s+|the\\s+)?" +
                "(?:email(?:\\s+address)?|account|identity|phone(?:\\s+number)?|" +
                "mobile(?:\\s+number)?|work\\s+rights|right\\s+to\\s+work|registration)\\b",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            "\\b(?:click|tap|follow|open|visit)\\b[^.!?\\n]{0,80}?\\b(?:link|button)\\b" +
                "[^.!?\\n]{0,40}?\\b(?:verify|confirm|activate)\\b",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            "(?:请|請)?(?:点击|點擊|打开|打開|访问|訪問)[^。！？\\n]{0,30}?" +
                "(?:链接|連結|网址|網址)[^。！？\\n]{0,30}?(?:验证|驗證|确认|確認|激活|啟用)",
        ),
        Regex(
            "(?:验证|驗證|确认|確認|激活|啟用)(?:您的|你的)?" +
                "(?:邮箱|郵箱|電子郵件|账户|帳戶|账号|賬號|身份|手机号|手機號)",
        ),
        Regex(
            "\\b(?:click|tap|follow|open|use)\\b[^.!?\\n]{0,80}?\\b(?:link|button)\\b" +
                "(?:\\s+(?:below|above|here))?\\s+(?:to|in\\s+order\\s+to)\\s+" +
                "(?:(?:finish|complete)\\s+)?(?:sign(?:ing)?|log(?:ging)?)\\s+in\\b",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            "\\b(?:magic|one[-\\s]*time|secure)\\s+(?:sign[-\\s]*in|log[-\\s]*in|login)\\s+link\\b",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            "\\b(?:click|tap|follow|open|use)\\b[^.!?\\n]{0,80}?\\b(?:link|button)\\b" +
                "(?:\\s+(?:below|above|here))?\\s+(?:to|in\\s+order\\s+to)\\s+" +
                "reset\\s+(?:your\\s+)?(?:[A-Za-z0-9]+\\s+){0,2}password\\b",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            "\\breset\\s+(?:your\\s+)?(?:[A-Za-z0-9]+\\s+){0,2}password\\b" +
                "[^.!?\\n]{0,40}?\\b(?:via|using|with)\\s+(?:the\\s+|this\\s+)?link\\b",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            "(?:请|請)?(?:点击|點擊|打开|打開|通过|通過)[^。！？\\n]{0,30}?" +
                "(?:链接|連結|按钮|按鈕)[^。！？\\n]{0,30}?" +
                "(?:(?:完成)?(?:登录|登入)|(?:重置|重設)(?:您的|你的)?(?:密码|密碼))",
        ),
    )
    private val urlPattern = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
    private val unrelatedBridge = Regex(
        "\\b(?:privacy|policy|terms|unsubscribe|support|contact|track|guide|help|details|learn|read)\\b|" +
            "隐私|隱私|退订|退訂|帮助|幫助|详情|詳情|指南|追踪|追蹤",
        RegexOption.IGNORE_CASE,
    )
    private val sentenceBreak = Regex("[.!?。！？]")
    private val nonRequestLead = Regex(
        "(?:\\b(?:how\\s+to|learn\\s+to|guide\\s+to|steps\\s+to|" +
            "do\\s+not(?:\\s+need\\s+to)?|don't(?:\\s+need\\s+to)?|" +
            "no\\s+(?:longer\\s+)?need\\s+to|never|" +
            "(?:need|use)\\s+(?:your\\s+|a\\s+)?(?:second\\s+factor|authenticator|passkey|security\\s+key|device|fingerprint|face|biometrics?)\\s+to|" +
            "(?:bring|show|present|provide)\\s+(?:a\\s+|your\\s+)?(?:government[-\\s]+issued\\s+)?(?:photo\\s+)?(?:id|document|passport|licen[cs]e)\\s+to|" +
            "if\\s+(?:this\\s+was(?:n't|\\s+not)\\s+you|you\\s+did(?:n't|\\s+not)\\s+request\\s+this)|" +
            "already|previously)\\b[,:]?|如何|怎样|怎樣|教程|指南|无需|無需|不必|不用|" +
            "请勿|請勿|不要|已完成|已经|已經|已)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String, rules: RulePack = RulePack.EMPTY): VerificationLinkRequest? {
        if (text.isBlank()) return null
        if (rules.linkExclusions.any { text.contains(it.phrase, ignoreCase = true) }) return null
        val requests = (requestPatterns.flatMap { it.findAll(text).map { match -> match.range }.toList() } +
            rules.verificationRequests.flatMap { rule -> literalRanges(text, rule.phrase) })
            .filter { range ->
                val lead = text.substring((range.first - 80).coerceAtLeast(0), range.first)
                !nonRequestLead.containsMatchIn(lead)
            }
        if (requests.isEmpty()) return null

        val matchingUrls = urlPattern.findAll(text)
            .mapNotNull { match ->
                val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', '。', '，', '；', '：', '！', '？')
                val uri = runCatching { URI(url) }.getOrNull() ?: return@mapNotNull null
                if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() ||
                    uri.userInfo != null
                ) return@mapNotNull null
                if (requests.none { request -> isBoundToRequest(text, request, match.range) }) {
                    return@mapNotNull null
                }
                url
            }
            .distinct()
            .toList()

        return VerificationLinkRequest(matchingUrls.singleOrNull())
    }

    private fun isBoundToRequest(text: String, request: IntRange, url: IntRange): Boolean {
        val gap = when {
            url.first > request.last -> text.substring(request.last + 1, url.first)
            request.first > url.last -> text.substring(url.last + 1, request.first)
            else -> ""
        }
        val proseBetween = urlPattern.replace(gap, "")
        return gap.length <= 180 &&
            !sentenceBreak.containsMatchIn(proseBetween) &&
            !unrelatedBridge.containsMatchIn(proseBetween)
    }

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
}
