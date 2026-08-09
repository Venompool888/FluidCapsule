package io.github.venompool888.fluidcapsule.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailAppClassifierTest {
    @Test
    fun `recognizes known email packages`() {
        assertTrue(EmailAppClassifier.isEmailApp("com.google.android.gm", emptySet()))
        assertTrue(EmailAppClassifier.isEmailApp("com.microsoft.office.outlook", emptySet()))
    }

    @Test
    fun `recognizes apps that handle mailto links`() {
        assertTrue(
            EmailAppClassifier.isEmailApp(
                "example.custom.mail",
                setOf("example.custom.mail"),
            ),
        )
    }

    @Test
    fun `does not classify unrelated apps as email`() {
        assertFalse(EmailAppClassifier.isEmailApp("example.chat", emptySet()))
    }
}
