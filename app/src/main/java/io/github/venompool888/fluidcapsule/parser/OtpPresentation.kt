package io.github.venompool888.fluidcapsule.parser

data class OtpPresentation(
    val title: String,
    val sourceLabel: String,
)

object OtpPresentationFormatter {
    private const val MAX_SENDER_LENGTH = 36

    fun format(appLabel: String, notificationTitle: String): OtpPresentation {
        val normalizedAppLabel = appLabel.trim().ifEmpty { "未知应用" }
        val sender = notificationTitle.trim()
            .takeIf {
                it.isNotEmpty() &&
                    !it.equals(normalizedAppLabel, ignoreCase = true)
            }
            ?.take(MAX_SENDER_LENGTH)
        return OtpPresentation(
            title = sender?.let { "验证码 · $it" } ?: "验证码",
            sourceLabel = normalizedAppLabel,
        )
    }
}
