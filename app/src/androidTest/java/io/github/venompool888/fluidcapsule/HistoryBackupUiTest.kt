package io.github.venompool888.fluidcapsule

import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.venompool888.fluidcapsule.history.*
import io.github.venompool888.fluidcapsule.rules.RuleSubscriptionPrefs
import io.github.venompool888.fluidcapsule.settings.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringWriter
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

@RunWith(AndroidJUnit4::class)
class HistoryBackupUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val source = "test.only.backup.ui.${UUID.randomUUID()}"
    private val oldPolicy = UserSettings.notificationHistoryRetentionPolicy(context)
    private val oldSubscription = RuleSubscriptionPrefs(context).subscriptionEnabled
    private val uris = mutableListOf<Uri>()

    @Before fun setup() {
        RuleSubscriptionPrefs(context).subscriptionEnabled = false
        UserSettings.setNotificationHistoryRetentionPolicy(context, HistoryRetentionPolicy.DEFAULT)
    }

    @After fun cleanup() {
        NotificationHistoryStore.deletePackage(context, source)
        UserSettings.setNotificationHistoryRetentionPolicy(context, oldPolicy)
        RuleSubscriptionPrefs(context).subscriptionEnabled = oldSubscription
        uris.forEach { context.contentResolver.delete(it, null, null) }
    }

    private fun archive(expired: Boolean): Uri {
        val entry = NotificationHistoryBackupEntry(
            UUID.randomUUID().toString(), UUID.randomUUID().toString().replace("-", "").repeat(2),
            source, "合成应用", "TEST ONLY", "TEST ONLY 正文", "TEST ONLY 完整正文", 100L,
            if (expired) 0L else System.currentTimeMillis(), "SKIPPED", "合成原因",
        )
        val output = StringWriter()
        NotificationHistoryBackupCodec.write(output, "test", 300L, sequenceOf(entry))
        val uri = Uri.parse("content://io.github.venompool888.fluidcapsule.test.backup/backup-test-${UUID.randomUUID()}.json")
        uris += uri
        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(output.toString()) }
        return uri
    }

    private fun controller(activity: MainActivity): NotificationHistoryTransfer {
        assertEquals("导入历史", (field(activity, "historyImportButton") as Button).text.toString())
        return field(activity, "historyTransfer") as NotificationHistoryTransfer
    }

    private fun startImport(activity: MainActivity, uri: Uri) {
        val tab = field(activity, "historyTab")!!
        (tab.javaClass.getDeclaredField("root").apply { isAccessible = true }.get(tab) as View).performClick()
        controller(activity).apply { assertTrue(beginPicking(HistoryTransferAction.IMPORT)); acceptDocument(uri) }
    }

    private fun dialog(activity: MainActivity) = field(activity, "historyTransferDialog") as AlertDialog?
    private fun field(activity: MainActivity, name: String): Any? =
        MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun find(view: View, predicate: (View) -> Boolean): View? {
        if (predicate(view)) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), predicate)?.let { return it }
        return null
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

    @Test fun buttonsLaunchJsonDocumentPickersAndCancellationLeavesHistoryUntouched() {
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
                scenario.onActivity { activity ->
                    val tab = field(activity, "historyTab")!!
                    (tab.javaClass.getDeclaredField("root").apply { isAccessible = true }.get(tab) as View).performClick()
                }
                await(scenario) { activity ->
                    find(field(activity, "historyPage") as View) { it is Button && it.text == "导出历史" } != null
                }
                scenario.onActivity { activity ->
                    (field(activity, "historyExportButton") as Button).performClick()
                }
                await(scenario) { controller(it).state == HistoryTransferState.Idle }
                scenario.onActivity { activity ->
                    (field(activity, "historyImportButton") as Button).performClick()
                }
                await(scenario) { controller(it).state == HistoryTransferState.Idle }
            }
        } finally { instrumentation.removeMonitor(monitor) }
        assertEquals(listOf(Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT), intents.map { it.action })
        assertTrue(intents.all { it.type == "application/json" && Intent.CATEGORY_OPENABLE in it.categories })
        assertTrue(intents[0].getStringExtra(Intent.EXTRA_TITLE)!!.matches(Regex("FluidCapsule-history-[0-9]{8}-[0-9]{6}\\.json")))
        assertEquals(before, NotificationHistoryStore.count(context))
    }

    @Test fun expiredImportDialogSurvivesRecreationAndCancelDoesNotChangePolicy() {
        val uri = archive(true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var previous: NotificationHistoryTransfer? = null
            scenario.onActivity { startImport(it, uri); previous = controller(it) }
            await(scenario) { controller(it).state is HistoryTransferState.Preview && dialog(it)?.isShowing == true }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertSame(previous, controller(activity))
                assertEquals(View.VISIBLE, (field(activity, "historyPage") as View).visibility)
                val dialog = dialog(activity)!!
                assertEquals("仅导入期限内记录", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
                assertEquals("改为永久并导入全部", dialog.getButton(AlertDialog.BUTTON_NEUTRAL).text.toString())
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            assertTrue(NotificationHistoryStore.forPackage(context, source).isEmpty())
            assertEquals(HistoryRetentionPolicy.DEFAULT, UserSettings.notificationHistoryRetentionPolicy(context))
        }
    }

    @Test fun expiryChoicesProduceCorrectResultAndRefreshRetentionLabel() {
        val uri = archive(true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { startImport(it, uri) }
            await(scenario) { controller(it).state is HistoryTransferState.Preview }
            scenario.onActivity { dialog(it)!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
            await(scenario) { controller(it).state is HistoryTransferState.Finished }
            assertTrue(NotificationHistoryStore.forPackage(context, source).isEmpty())
            scenario.onActivity { dialog(it)!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
            await(scenario) { controller(it).state == HistoryTransferState.Idle }
            scenario.onActivity { startImport(it, uri) }
            await(scenario) { controller(it).state is HistoryTransferState.Preview }
            scenario.onActivity { dialog(it)!!.getButton(AlertDialog.BUTTON_NEUTRAL).performClick() }
            await(scenario) { controller(it).state is HistoryTransferState.Finished }
            assertEquals(1, NotificationHistoryStore.forPackage(context, source).size)
            assertEquals(HistoryRetentionUnit.FOREVER, UserSettings.notificationHistoryRetentionPolicy(context).unit)
            scenario.onActivity { activity ->
                assertTrue((field(activity, "historyRetentionValueView") as TextView).text.toString().contains("永久"))
            }
        }
    }

    @Test fun currentRecordsHaveSimpleImportConfirmationAndResultSurvivesRecreation() {
        val uri = archive(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { startImport(it, uri) }
            await(scenario) { controller(it).state is HistoryTransferState.Preview }
            scenario.onActivity { activity ->
                val dialog = dialog(activity)!!
                assertEquals("导入", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
                assertEquals(View.GONE, dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            await(scenario) { controller(it).state is HistoryTransferState.Finished }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue((controller(activity).state as HistoryTransferState.Finished).success)
                assertTrue(dialog(activity)!!.isShowing)
                dialog(activity)!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            await(scenario) { controller(it).state == HistoryTransferState.Idle }
        }
    }

    @Test fun pendingImportDefersConflictingUiActionsWithoutBlockingMainThread() {
        val uri = archive(false)
        val acquired = CountDownLatch(1)
        val release = CountDownLatch(1)
        val lock = NotificationHistoryStore::class.java.getDeclaredField("retentionLock").apply { isAccessible = true }.get(null) as ReentrantLock
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { startImport(it, uri) }
            await(scenario) { controller(it).state is HistoryTransferState.Preview }
            val holder = Thread {
                lock.lock()
                try { acquired.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
                finally { lock.unlock() }
            }
            holder.start()
            assertTrue(acquired.await(5, TimeUnit.SECONDS))
            try {
                scenario.onActivity { dialog(it)!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
                await(scenario) { controller(it).state is HistoryTransferState.Working }
                scenario.onActivity { activity ->
                    val retention = field(activity, "historyRetentionValueView") as TextView
                    assertFalse("Retention must not wait on an import lock from UI", retention.isEnabled)
                    assertFalse((field(activity, "historyClearButton") as Button).isEnabled)
                    retention.performClick()
                    assertNull(dialog(activity))
                }
            } finally { release.countDown(); holder.join(5000) }
            await(scenario) { controller(it).state is HistoryTransferState.Finished }
        }
    }
}
