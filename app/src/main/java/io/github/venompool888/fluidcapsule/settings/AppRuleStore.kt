package io.github.venompool888.fluidcapsule.settings

import android.content.Context
import org.json.JSONObject

data class AppRule(
    val ttlMinutes: Int = 0,
    val priority: Int = 0,
    val contentMode: String = CONTENT_INHERIT,
    val maxBodyChars: Int = DEFAULT_MAX_BODY_CHARS,
    val includeKeywords: String = "",
    val excludeKeywords: String = "",
    val recordHistory: Boolean = true,
) {
    fun matches(text: String): RuleMatchResult {
        val normalized = text.lowercase()
        val includes = keywordList(includeKeywords)
        val excludes = keywordList(excludeKeywords)
        val excluded = excludes.firstOrNull { it.lowercase() in normalized }
        if (excluded != null) return RuleMatchResult(false, "命中排除关键词：$excluded")
        if (includes.isNotEmpty() && includes.none { it.lowercase() in normalized }) {
            return RuleMatchResult(false, "未命中任何包含关键词")
        }
        return RuleMatchResult(true, "规则匹配")
    }

    companion object {
        const val CONTENT_INHERIT = "inherit"
        const val CONTENT_SHOW = "show"
        const val CONTENT_HIDE = "hide"
        const val DEFAULT_MAX_BODY_CHARS = 220

        fun keywordList(value: String): List<String> = value
            .split(',', '，', '\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
    }
}

data class RuleMatchResult(val matches: Boolean, val detail: String)

object AppRuleStore {
    private const val PREFS = "app_rules"

    fun get(context: Context, packageName: String): AppRule {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(packageName, null)
            ?: return AppRule()
        return runCatching {
            val json = JSONObject(raw)
            AppRule(
                ttlMinutes = normalizeTtl(json.optInt("ttlMinutes", 0)),
                priority = json.optInt("priority", 0).coerceIn(-1, 1),
                contentMode = normalizeContentMode(json.optString("contentMode", AppRule.CONTENT_INHERIT)),
                maxBodyChars = json.optInt("maxBodyChars", AppRule.DEFAULT_MAX_BODY_CHARS).coerceIn(20, 500),
                includeKeywords = json.optString("includeKeywords", ""),
                excludeKeywords = json.optString("excludeKeywords", ""),
                recordHistory = json.optBoolean("recordHistory", true),
            )
        }.getOrDefault(AppRule())
    }

    fun set(context: Context, packageName: String, rule: AppRule) {
        require(packageName.isNotBlank()) { "package name is required" }
        val normalized = rule.copy(
            ttlMinutes = normalizeTtl(rule.ttlMinutes),
            priority = rule.priority.coerceIn(-1, 1),
            contentMode = normalizeContentMode(rule.contentMode),
            maxBodyChars = rule.maxBodyChars.coerceIn(20, 500),
        )
        val json = JSONObject()
            .put("ttlMinutes", normalized.ttlMinutes)
            .put("priority", normalized.priority)
            .put("contentMode", normalized.contentMode)
            .put("maxBodyChars", normalized.maxBodyChars)
            .put("includeKeywords", normalized.includeKeywords)
            .put("excludeKeywords", normalized.excludeKeywords)
            .put("recordHistory", normalized.recordHistory)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(packageName, json.toString()).apply()
    }

    fun reset(context: Context, packageName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(packageName).apply()
    }

    fun packages(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.keys

    fun effectiveTtlMinutes(context: Context, packageName: String): Int =
        get(context, packageName).ttlMinutes.takeIf { it > 0 }
            ?: UserSettings.capsuleDisplayDurationMinutes(context)

    fun showContent(context: Context, packageName: String): Boolean = when (get(context, packageName).contentMode) {
        AppRule.CONTENT_SHOW -> true
        AppRule.CONTENT_HIDE -> false
        else -> UserSettings.showWhitelistContent(context)
    }

    private fun normalizeTtl(value: Int): Int = if (value == 0) 0 else value.coerceIn(1, 30)

    private fun normalizeContentMode(value: String): String = when (value.lowercase()) {
        AppRule.CONTENT_SHOW, AppRule.CONTENT_HIDE -> value.lowercase()
        else -> AppRule.CONTENT_INHERIT
    }
}
