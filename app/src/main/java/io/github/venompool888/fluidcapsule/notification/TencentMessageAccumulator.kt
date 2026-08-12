package io.github.venompool888.fluidcapsule.notification

/**
 * Rebuilds the message sequence from Tencent's repeated summary-notification updates.
 * Tencent does not publish MessagingStyle history, so only updates observed while the
 * listener is connected can be retained.
 */
internal class TencentMessageAccumulator(
    private val maxMessages: Int = 8,
    private val staleAfterMillis: Long = 30 * 60_000L,
) {
    data class Presentation(
        val conversationTitle: String,
        val messages: List<String>,
    )

    private data class Snapshot(
        val conversationTitle: String,
        val latestMessage: String,
        val reportedCount: Int?,
    )

    private data class State(
        var reportedCount: Int?,
        val messages: MutableList<String>,
        var lastSeenMillis: Long,
    )

    private val states = mutableMapOf<String, State>()

    fun update(
        packageName: String,
        notificationKey: String,
        title: String,
        primaryText: String,
        nowMillis: Long,
    ): Presentation? {
        val snapshot = parse(packageName, title, primaryText) ?: return null
        states.entries.removeAll { nowMillis - it.value.lastSeenMillis > staleAfterMillis }
        val stateKey = "$notificationKey\u0000${snapshot.conversationTitle}"
        val previous = states[stateKey]
        val shouldReset = previous == null ||
            snapshot.reportedCount == 1 ||
            (snapshot.reportedCount != null &&
                previous.reportedCount != null &&
                snapshot.reportedCount < previous.reportedCount!!)
        val state = if (shouldReset) {
            State(
                reportedCount = snapshot.reportedCount,
                messages = mutableListOf(snapshot.latestMessage),
                lastSeenMillis = nowMillis,
            ).also { states[stateKey] = it }
        } else {
            previous!!
        }

        if (!shouldReset) {
            val countAdvanced = snapshot.reportedCount != null &&
                (state.reportedCount == null || snapshot.reportedCount > state.reportedCount!!)
            val uncountedMessageChanged = snapshot.reportedCount == null &&
                state.messages.lastOrNull() != snapshot.latestMessage
            if (countAdvanced || uncountedMessageChanged) {
                state.messages += snapshot.latestMessage
                while (state.messages.size > maxMessages) state.messages.removeAt(0)
            } else if (snapshot.reportedCount == state.reportedCount &&
                state.messages.lastOrNull() != snapshot.latestMessage
            ) {
                state.messages[state.messages.lastIndex] = snapshot.latestMessage
            }
            state.reportedCount = snapshot.reportedCount ?: state.reportedCount
            state.lastSeenMillis = nowMillis
        }

        return Presentation(snapshot.conversationTitle, state.messages.toList())
    }

    fun remove(notificationKey: String) {
        val prefix = "$notificationKey\u0000"
        states.keys.removeAll { it.startsWith(prefix) }
    }

    private fun parse(packageName: String, title: String, primaryText: String): Snapshot? {
        val cleanTitle = title.trim()
        val cleanText = primaryText.trim()
        if (cleanTitle.isEmpty() || cleanText.isEmpty()) return null
        return when (packageName) {
            WECHAT_PACKAGE -> {
                val summary = WECHAT_SUMMARY.matchEntire(cleanText)
                val count = summary?.groupValues?.get(1)?.toIntOrNull()
                val summarizedBody = summary?.groupValues?.get(2)?.trim().orEmpty()
                val latest = (summarizedBody.ifEmpty { cleanText })
                    .removePrefix("$cleanTitle:")
                    .removePrefix("$cleanTitle：")
                    .trim()
                latest.takeIf(String::isNotEmpty)?.let {
                    Snapshot(cleanTitle, it, count)
                }
            }
            QQ_PACKAGE -> {
                val countMatch = QQ_COUNT.find(cleanTitle)
                val conversationTitle = cleanTitle.replace(QQ_COUNT, "").trim()
                Snapshot(
                    conversationTitle = conversationTitle.ifEmpty { cleanTitle },
                    latestMessage = cleanText,
                    reportedCount = countMatch?.groupValues?.get(1)?.toIntOrNull(),
                )
            }
            else -> null
        }
    }

    companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
        const val QQ_PACKAGE = "com.tencent.mobileqq"

        private val WECHAT_SUMMARY = Regex("^\\[(\\d+)条](.*)$")
        private val QQ_COUNT = Regex("[\\(（](\\d+)条新消息[\\)）]\\s*$")
    }
}
