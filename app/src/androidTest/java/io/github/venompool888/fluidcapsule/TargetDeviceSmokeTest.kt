package io.github.venompool888.fluidcapsule

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.venompool888.fluidcapsule.core.CapsuleAction
import io.github.venompool888.fluidcapsule.core.CapsuleEvent
import io.github.venompool888.fluidcapsule.core.CapsuleKind
import io.github.venompool888.fluidcapsule.core.CapsulePrivacy
import io.github.venompool888.fluidcapsule.notification.TencentMessageAccumulator
import io.github.venompool888.fluidcapsule.publisher.CapsuleCoordinator
import io.github.venompool888.fluidcapsule.publisher.NotificationFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)
class TargetDeviceSmokeTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun testRunsOnSupportedDevice() {
        assertEquals(36, Build.VERSION.SDK_INT)
        assertEquals("CPH2797", Build.MODEL)
    }

    @Test
    fun testInstalledBuildIsExpectedVersion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals("1.1.0", packageInfo.versionName)
        assertEquals(35L, packageInfo.longVersionCode)
    }

    @Test
    fun testMainActivityCanLaunch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        instrumentation.waitForIdleSync()
    }

    @Test
    fun testHistoryUsesOneContinuousScrollContainer() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithText("历史").performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithTag("history_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("history_list")
                .assertIsDisplayed()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .assertCountEquals(1)
        }
    }

    @Test
    fun testActionlessCloudEventsGetCloseAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        CapsuleKind.entries.forEach { kind ->
            val eventId = "test-only:actionless:${kind.name}"
            val notification = NotificationFactory.baseBuilder(
                context,
                CapsuleEvent(
                    sourcePackage = "test.actionless.app",
                    sourceLabel = "TEST ONLY",
                    eventId = eventId,
                    kind = kind,
                    title = "TEST ONLY actionless",
                    shortText = "TEST",
                    body = "NO ACTION REQUIRED",
                    action = CapsuleAction.None,
                    privacy = CapsulePrivacy.SHOW_FULL,
                    createdAtMillis = now,
                    expiresAtMillis = now + 60_000L,
                    dedupeKey = eventId,
                ),
            ).build()

            assertEquals(1, notification.actions.size)
            assertEquals("关闭", notification.actions.single().title.toString())
            assertEquals(
                Notification.Action.SEMANTIC_ACTION_DELETE,
                notification.actions.single().semanticAction,
            )
        }
    }

    @Test
    fun testOtpGetsExplicitCopyAndCloseActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        val notification = NotificationFactory.baseBuilder(
            context,
            CapsuleEvent(
                sourcePackage = "test.otp.app",
                sourceLabel = "TEST ONLY",
                eventId = "test-only:otp-actions",
                kind = CapsuleKind.OTP,
                title = "TEST ONLY OTP",
                shortText = "482913",
                body = "点击复制",
                action = CapsuleAction.CopySensitiveText("482913"),
                privacy = CapsulePrivacy.SHOW_FULL,
                createdAtMillis = now,
                expiresAtMillis = now + 60_000L,
                dedupeKey = "test-only:otp-actions",
            ),
        ).build()

        assertEquals(
            listOf("复制验证码", "关闭"),
            notification.actions.map { it.title.toString() },
        )
        assertEquals(R.drawable.ic_content_copy, notification.actions[0].getIcon().resId)
        assertEquals(R.drawable.ic_notification_delete, notification.actions[1].getIcon().resId)
        assertNotNull(notification.deleteIntent)
        assertEquals(
            Notification.Action.SEMANTIC_ACTION_DELETE,
            notification.actions[1].semanticAction,
        )
    }

    @Test
    fun testActionlessQqNotificationGetsOpenReplyAndCloseActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        val originalIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationFactory.baseBuilder(
            context,
            CapsuleEvent(
                sourcePackage = TencentMessageAccumulator.QQ_PACKAGE,
                sourceLabel = "QQ",
                eventId = "test-only:qq-actionless",
                kind = CapsuleKind.NOTIFICATION,
                title = "TEST ONLY QQ",
                shortText = "TEST",
                body = "NO ACTION REQUIRED",
                action = CapsuleAction.OpenOriginal(originalIntent),
                privacy = CapsulePrivacy.SHOW_FULL,
                createdAtMillis = now,
                expiresAtMillis = now + 60_000L,
                dedupeKey = "test-only:qq-actionless",
            ),
        ).build()

        assertEquals(listOf("打开回复", "关闭"), notification.actions.map { it.title.toString() })
        assertEquals(R.drawable.ic_notification_delete, notification.actions[1].getIcon().resId)
    }

    @Test
    fun testSystemNotificationQueuePreemptsAndRestores() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val now = System.currentTimeMillis()
        val firstId = "test-only:ordinary"
        val otpId = "test-only:otp"

        CapsuleCoordinator.submit(
            context,
            CapsuleEvent(
                sourcePackage = context.packageName,
                sourceLabel = "TEST ONLY",
                eventId = firstId,
                kind = CapsuleKind.NOTIFICATION,
                title = "TEST ONLY ordinary",
                shortText = "TEST",
                body = "NO ACTION REQUIRED",
                action = CapsuleAction.None,
                privacy = CapsulePrivacy.SHOW_FULL,
                createdAtMillis = now,
                expiresAtMillis = now + 60_000L,
                dedupeKey = firstId,
            ),
        )
        assertEquals("TEST ONLY ordinary", activeCapsule(manager)?.title())

        CapsuleCoordinator.submit(
            context,
            CapsuleEvent(
                sourcePackage = context.packageName,
                sourceLabel = "TEST ONLY",
                eventId = otpId,
                kind = CapsuleKind.OTP,
                title = "TEST ONLY invalid code",
                shortText = "TEST",
                body = "NO ACTION REQUIRED",
                action = CapsuleAction.None,
                privacy = CapsulePrivacy.HIDE_SENSITIVE,
                createdAtMillis = now + 1,
                expiresAtMillis = now + 60_000L,
                dedupeKey = otpId,
            ),
        )
        assertEquals("TEST ONLY invalid code", activeCapsule(manager)?.title())

        CapsuleCoordinator.consume(context, otpId)
        assertEquals("TEST ONLY ordinary", activeCapsule(manager)?.title())

        CapsuleCoordinator.consume(context, firstId)
        assertNull(activeCapsule(manager))
    }

    private fun activeCapsule(manager: NotificationManager): Notification? =
        manager.activeNotifications
            .firstOrNull { it.id == NotificationFactory.CAPSULE_NOTIFICATION_ID }
            ?.notification

    private fun Notification.title(): String? =
        extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

}
