package io.github.venompool888.fluidcapsule

import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.venompool888.fluidcapsule.history.HistoryTransferAction
import io.github.venompool888.fluidcapsule.history.HistoryTransferState
import io.github.venompool888.fluidcapsule.history.NotificationHistoryBackupCodec
import io.github.venompool888.fluidcapsule.history.NotificationHistoryBackupEntry
import io.github.venompool888.fluidcapsule.history.NotificationHistoryStore
import io.github.venompool888.fluidcapsule.history.NotificationHistoryTransfer
import io.github.venompool888.fluidcapsule.rules.RuleSubscriptionPrefs
import io.github.venompool888.fluidcapsule.settings.HistoryRetentionPolicy
import io.github.venompool888.fluidcapsule.settings.HistoryRetentionUnit
import io.github.venompool888.fluidcapsule.settings.UserSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringWriter
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

@RunWith(AndroidJUnit4::class)
class HistoryBackupUiTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val source = "test.only.backup.ui.${UUID.randomUUID()}"
    private val oldPolicy = UserSettings.notificationHistoryRetentionPolicy(context)
    private val oldSubscription = RuleSubscriptionPrefs(context).subscriptionEnabled
    private val uris = mutableListOf<Uri>()

    @Before
    fun setup() {
        RuleSubscriptionPrefs(context).subscriptionEnabled = false
        UserSettings.setNotificationHistoryRetentionPolicy(context, HistoryRetentionPolicy.DEFAULT)
    }

    @After
    fun cleanup() {
        NotificationHistoryStore.deletePackage(context, source)
        UserSettings.setNotificationHistoryRetentionPolicy(context, oldPolicy)
        RuleSubscriptionPrefs(context).subscriptionEnabled = oldSubscription
        uris.forEach { context.contentResolver.delete(it, null, null) }
    }

    private fun archive(expired: Boolean): Uri {
        val entry = NotificationHistoryBackupEntry(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString().replace("-", "").repeat(2),
            source,
            "合成应用",
            "TEST ONLY",
            "TEST ONLY 正文",
            "TEST ONLY 完整正文",
            100L,
            if (expired) 0L else System.currentTimeMillis(),
            "SKIPPED",
            "合成原因",
        )
        val output = StringWriter()
        NotificationHistoryBackupCodec.write(output, "test", 300L, sequenceOf(entry))
        val uri = Uri.parse("content://io.github.venompool888.fluidcapsule.test.backup/backup-test-${UUID.randomUUID()}.json")
        uris += uri
        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(output.toString()) }
        return uri
    }

    private fun controller(activity: MainActivity): NotificationHistoryTransfer = activity.historyTransfer

    private fun openHistoryTab() {
        composeRule.onNodeWithText("历史").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("history_manage_button").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun startImport(scenario: ActivityScenario<MainActivity>, uri: Uri) {
        openHistoryTab()
        scenario.onActivity { activity ->
            controller(activity).apply {
                assertTrue(beginPicking(HistoryTransferAction.IMPORT))
                acceptDocument(uri)
            }
        }
    }

    private fun awaitNodeText(text: String) {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun await(scenario: ActivityScenario<MainActivity>, predicate: (MainActivity) -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = predicate(it) }
            if (ready) return
            Thread.sleep(30)
        }
        fail("Timed out waiting for history UI")
    }

    @Test
    fun buttonsLaunchJsonDocumentPickersAndCancellationLeavesHistoryUntouched() {
        val intents = mutableListOf<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action in listOf(Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT)) {
                    intents += Intent(intent)
                    return Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
                }
                return null
            }
        }
        instrumentation.addMonitor(monitor)
        val before = NotificationHistoryStore.count(context)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                openHistoryTab()
                composeRule.onNodeWithTag("history_manage_button").performClick()
                awaitNodeText("导出历史")
                composeRule.onNodeWithText("导入历史").performScrollTo().assertIsDisplayed()
                composeRule.onNodeWithText("导出历史").performScrollTo().performClick()
                await(scenario) { controller(it).state == HistoryTransferState.Idle }
                composeRule.onNodeWithText("导入历史").performScrollTo().performClick()
                await(scenario) { controller(it).state == HistoryTransferState.Idle }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
        assertEquals(listOf(Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT), intents.map { it.action })
        assertTrue(intents.all { it.type == "application/json" && Intent.CATEGORY_OPENABLE in it.categories })
        assertTrue(intents[0].getStringExtra(Intent.EXTRA_TITLE)!!.matches(Regex("FluidCapsule-history-[0-9]{8}-[0-9]{6}\\.json")))
        assertEquals(before, NotificationHistoryStore.count(context))
    }

    @Test
    fun expiredImportDialogSurvivesRecreationAndCancelDoesNotChangePolicy() {
        val uri = archive(true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var previous: NotificationHistoryTransfer? = null
            startImport(scenario, uri)
            scenario.onActivity { previous = controller(it) }
            await(scenario) { controller(it).state is HistoryTransferState.Preview }
            awaitNodeText("仅导入期限内记录")
            scenario.recreate()
            composeRule.waitForIdle()
            scenario.onActivity { activity ->
                assertSame(previous, controller(activity))
            }
            composeRule.onNodeWithTag("history_list").assertIsDisplayed()
            composeRule.onNodeWithText("仅导入期限内记录").assertIsDisplayed()
            composeRule.onNodeWithText("改为永久并导入全部").assertIsDisplayed()
            composeRule.onNodeWithText("取消").performClick()
            await(scenario) { controller(it).state == HistoryTransferState.Idle }
            assertTrue(NotificationHistoryStore.forPackage(context, source).isEmpty())
            assertEquals(HistoryRetentionPolicy.DEFAULT, UserSettings.notificationHistoryRetentionPolicy(context))
        }
    }

    @Test
    fun expiryChoicesProduceCorrectResultAndRefreshRetentionLabel() {
        val uri = archive(true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startImport(scenario, uri)
            await(scenario) { controller(it).state is HistoryTransferState.Preview }
            awaitNodeText("仅导入期限内记录")
            composeRule.onNodeWithText("仅导入期限内记录").performClick()
            await(scenario) { controller(it).state is HistoryTransferState.Finished }
            assertTrue(NotificationHistoryStore.forPackage(context, source).isEmpty())
            awaitNodeText("确定")
            composeRule.onNodeWithText("确定").performClick()
            await(scenario) { controller(it).state == HistoryTransferState.Idle }

            startImport(scenario, uri)
            await(scenario) { controller(it).state is HistoryTransferState.Preview }
            awaitNodeText("改为永久并导入全部")
            composeRule.onNodeWithText("改为永久并导入全部").performClick()
            await(scenario) { controller(it).state is HistoryTransferState.Finished }
            assertEquals(1, NotificationHistoryStore.forPackage(context, source).size)
            assertEquals(HistoryRetentionUnit.FOREVER, UserSettings.notificationHistoryRetentionPolicy(context).unit)
            awaitNodeText("确定")
            composeRule.onNodeWithText("确定").performClick()
            await(scenario) { controller(it).state == HistoryTransferState.Idle }

            composeRule.onNodeWithTag("history_manage_button").performClick()
            awaitNodeText("自动保留时间")
            composeRule.onNode(hasText("永久保存") and isSelected()).assertIsDisplayed()
        }
    }

    @Test
    fun currentRecordsHaveSimpleImportConfirmationAndResultSurvivesRecreation() {
        val uri = archive(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startImport(scenario, uri)
            await(scenario) { controller(it).state is HistoryTransferState.Preview }
            awaitNodeText("导入")
            composeRule.onNodeWithText("导入").assertIsDisplayed()
            composeRule.onNodeWithText("改为永久并导入全部").assertDoesNotExist()
            composeRule.onNodeWithText("导入").performClick()
            await(scenario) { controller(it).state is HistoryTransferState.Finished }
            scenario.recreate()
            composeRule.waitForIdle()
            scenario.onActivity { activity ->
                assertTrue((controller(activity).state as HistoryTransferState.Finished).success)
            }
            awaitNodeText("确定")
            composeRule.onNodeWithText("确定").assertIsDisplayed().performClick()
            await(scenario) { controller(it).state == HistoryTransferState.Idle }
        }
    }

    @Test
    fun pendingImportDefersConflictingUiActionsWithoutBlockingMainThread() {
        val uri = archive(false)
        val acquired = CountDownLatch(1)
        val release = CountDownLatch(1)
        val lock = NotificationHistoryStore::class.java
            .getDeclaredField("retentionLock")
            .apply { isAccessible = true }
            .get(null) as ReentrantLock
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startImport(scenario, uri)
            await(scenario) { controller(it).state is HistoryTransferState.Preview }
            awaitNodeText("导入")
            val holder = Thread {
                lock.lock()
                try {
                    acquired.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                } finally {
                    lock.unlock()
                }
            }
            holder.start()
            assertTrue(acquired.await(5, TimeUnit.SECONDS))
            try {
                composeRule.onNodeWithText("导入").performClick()
                await(scenario) { controller(it).state is HistoryTransferState.Working }
                composeRule.onNodeWithTag("history_manage_button").performClick()
                awaitNodeText("清空全部通知历史")
                composeRule.onNodeWithText("7 天").performScrollTo().assertIsNotEnabled()
                composeRule.onNodeWithText("导出历史").performScrollTo().assertIsNotEnabled()
                composeRule.onNodeWithText("导入历史").performScrollTo().assertIsNotEnabled()
                composeRule.onNodeWithText("清空全部通知历史").performScrollTo().assertIsNotEnabled()
                composeRule.onNodeWithText("清空通知历史？").assertDoesNotExist()
            } finally {
                release.countDown()
                holder.join(5000)
            }
            await(scenario) { controller(it).state is HistoryTransferState.Finished }
        }
    }
}
