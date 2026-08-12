package io.github.venompool888.fluidcapsule.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageDisplayFormatterTest {
    @Test
    fun showsAllMessagesFromOneMessagingStyleNotificationInOrder() {
        assertEquals(
            "Literally us · 🥹 🥹 🥹 🥹 🥹 🥹",
            format(
                messageTexts = listOf(
                    "Literally us",
                    "🥹 🥹 🥹 🥹 🥹 🥹",
                ),
                primaryText = "🥹 🥹 🥹 🥹 🥹 🥹",
            ),
        )
    }

    @Test
    fun keepsRepeatedMessagesBecauseTheyRepresentSeparateIncomingMessages() {
        assertEquals(
            "hello · hello",
            format(messageTexts = listOf("hello", "hello"), primaryText = "hello"),
        )
    }

    @Test
    fun fallsBackToPrimaryTextForNonMessagingNotifications() {
        assertEquals(
            "Download complete",
            format(messageTexts = emptyList(), primaryText = "Download complete"),
        )
    }

    private fun format(messageTexts: List<String>, primaryText: String): String =
        MessageDisplayFormatter.format(
            messageTexts = messageTexts,
            primaryText = primaryText,
            combinedText = "WhatsApp\nElva\n$primaryText",
            appLabel = "WhatsApp",
            displayTitle = "Elva",
            maxChars = 220,
        )
}
