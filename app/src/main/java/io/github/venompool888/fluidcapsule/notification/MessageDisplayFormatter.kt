package io.github.venompool888.fluidcapsule.notification

/** Builds the visible body without changing the latest-message-only OTP input. */
internal object MessageDisplayFormatter {
    fun format(
        messageTexts: List<String>,
        primaryText: String,
        combinedText: String,
        appLabel: String,
        displayTitle: String,
        maxChars: Int,
    ): String {
        val visibleMessages = (messageTexts.ifEmpty { listOf(primaryText) })
            .map(String::trim)
            .filter { text ->
                text.isNotEmpty() &&
                    !text.equals(appLabel, ignoreCase = true) &&
                    !text.equals(displayTitle, ignoreCase = true)
            }

        val body = visibleMessages.joinToString(" · ").ifEmpty {
            combinedText.lineSequence()
                .map(String::trim)
                .firstOrNull { text ->
                    text.isNotEmpty() &&
                        !text.equals(appLabel, ignoreCase = true) &&
                        !text.equals(displayTitle, ignoreCase = true)
                }
                ?: "有新通知"
        }
        return body.take(maxChars.coerceAtLeast(0))
    }
}
