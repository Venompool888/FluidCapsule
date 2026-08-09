package io.github.venompool888.fluidcapsule.history

import java.security.MessageDigest

object NotificationHistoryFingerprint {
    fun create(
        notificationKey: String,
        postedAtMillis: Long,
        title: String,
        combinedText: String,
    ): String {
        val source = listOf(
            notificationKey,
            postedAtMillis.toString(),
            title,
            combinedText,
        ).joinToString("\u0000")
        return digest(source)
    }

    private fun digest(source: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
