package io.github.venompool888.fluidcapsule

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.venompool888.fluidcapsule.history.*
import io.github.venompool888.fluidcapsule.settings.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoryTransferTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val source = "test.only.transfer.${UUID.randomUUID()}"
    private val oldPolicy = UserSettings.notificationHistoryRetentionPolicy(context)
    private val wasRecording = UserSettings.notificationHistoryEnabled(context)
    private val controller = NotificationHistoryTransfer(context)
    private val files = mutableListOf<File>()
    private val uris = mutableListOf<Uri>()

    @After fun cleanup() {
        instrumentation.runOnMainSync { controller.close() }
        NotificationHistoryStore.deletePackage(context, source)
        UserSettings.setNotificationHistoryRetentionPolicy(context, oldPolicy)
        UserSettings.setNotificationHistoryEnabled(context, wasRecording)
        files.forEach { it.delete() }
        uris.forEach { context.contentResolver.delete(it, null, null) }
    }

    private fun entry(captured: Long = System.currentTimeMillis()) = NotificationHistoryBackupEntry(
        UUID.randomUUID().toString(), UUID.randomUUID().toString().replace("-", "").repeat(2),
        source, "合成应用", "TEST ONLY", "TEST ONLY 原文", "TEST ONLY 完整正文", 100L, captured, "SKIPPED", "合成原因",
    )

    private fun archive(vararg entries: NotificationHistoryBackupEntry): Pair<File, Uri> {
        val file = File(context.cacheDir, "backup-test-${UUID.randomUUID()}.json")
        files += file
        file.bufferedWriter().use { NotificationHistoryBackupCodec.write(it, "test", 300L, entries.asSequence()) }
        val uri = Uri.parse("content://io.github.venompool888.fluidcapsule.test.backup/${file.name}")
        uris += uri
        context.contentResolver.openOutputStream(uri, "wt")!!.use { output -> file.inputStream().use { it.copyTo(output) } }
        return file to uri
    }

    private fun beginImport(uri: Uri) = instrumentation.runOnMainSync {
        assertTrue(controller.beginPicking(HistoryTransferAction.IMPORT))
        controller.acceptDocument(uri)
    }

    private inline fun <reified T : HistoryTransferState> await(): T {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val state = controller.state
            if (state is T) return state
            Thread.sleep(15)
        }
        throw AssertionError("Expected ${T::class.java.simpleName}; actual ${controller.state}")
    }

    @Test fun cancelPickingOrPreviewNeverChangesDataOrSettings() {
        val before = NotificationHistoryStore.count(context)
        instrumentation.runOnMainSync {
            assertTrue(controller.beginPicking(HistoryTransferAction.IMPORT))
            assertFalse(controller.beginPicking(HistoryTransferAction.EXPORT))
            controller.acceptDocument(null)
            assertEquals(HistoryTransferState.Idle, controller.state)
        }
        val (_, uri) = archive(entry(0L))
        beginImport(uri)
        val preview = await<HistoryTransferState.Preview>()
        assertTrue(preview.file.exists())
        instrumentation.runOnMainSync { controller.cancelPreview() }
        assertFalse(preview.file.exists())
        assertEquals(before, NotificationHistoryStore.count(context))
        assertEquals(oldPolicy, UserSettings.notificationHistoryRetentionPolicy(context))
    }

    @Test fun validImportRequiresConfirmationAndDoesNotEnableRecording() {
        UserSettings.setNotificationHistoryEnabled(context, false)
        val (_, uri) = archive(entry())
        beginImport(uri)
        val preview = await<HistoryTransferState.Preview>()
        assertEquals(1L, preview.metadata.count)
        assertEquals(0L, preview.expired)
        assertTrue(NotificationHistoryStore.forPackage(context, source).isEmpty())
        instrumentation.runOnMainSync { controller.confirmImport(false) }
        assertTrue(await<HistoryTransferState.Finished>().success)
        assertEquals(1, NotificationHistoryStore.forPackage(context, source).size)
        assertFalse(UserSettings.notificationHistoryEnabled(context))
        assertFalse(preview.file.exists())
    }

    @Test fun changedRetentionShowsUpdatedPreviewBeforeWriting() {
        UserSettings.setNotificationHistoryRetentionPolicy(context, HistoryRetentionPolicy.DEFAULT)
        val (_, uri) = archive(entry(0L))
        beginImport(uri)
        assertEquals(1L, await<HistoryTransferState.Preview>().expired)
        val permanent = HistoryRetentionPolicy(0, HistoryRetentionUnit.FOREVER)
        UserSettings.setNotificationHistoryRetentionPolicy(context, permanent)
        instrumentation.runOnMainSync { controller.confirmImport(false) }
        val revised = await<HistoryTransferState.Preview>()
        assertEquals(permanent, revised.policy)
        assertEquals(0L, revised.expired)
        assertTrue(NotificationHistoryStore.forPackage(context, source).isEmpty())
        instrumentation.runOnMainSync { controller.confirmImport(false) }
        assertTrue(await<HistoryTransferState.Finished>().success)
    }

    @Test fun detachedObserverCanResumePreviewAndResult() {
        val (_, uri) = archive(entry())
        instrumentation.runOnMainSync { controller.attach {}; controller.detach() }
        beginImport(uri)
        val preview = await<HistoryTransferState.Preview>()
        var observed: HistoryTransferState? = null
        instrumentation.runOnMainSync { controller.attach { observed = it } }
        assertEquals(preview, observed)
        instrumentation.runOnMainSync { controller.detach(); controller.confirmImport(false) }
        val result = await<HistoryTransferState.Finished>()
        instrumentation.runOnMainSync { controller.attach { observed = it } }
        assertEquals(result, observed)
    }

    @Test fun corruptFileAndMissingProviderReportFailureWithoutImporting() {
        val (file, uri) = archive(entry())
        file.appendText(" {}")
        context.contentResolver.openOutputStream(uri, "wt")!!.use { output -> file.inputStream().use { it.copyTo(output) } }
        beginImport(uri)
        assertFalse(await<HistoryTransferState.Finished>().success)
        assertTrue(NotificationHistoryStore.forPackage(context, source).isEmpty())
        instrumentation.runOnMainSync { controller.acknowledgeResult() }
        beginImport(Uri.parse("content://missing.test.provider/backup.json"))
        assertFalse(await<HistoryTransferState.Finished>().success)
        assertTrue(NotificationHistoryStore.forPackage(context, source).isEmpty())
    }

    @Test fun exportUsesDocumentProviderAndReportsFailedDestination() {
        val (input, _) = archive(entry())
        NotificationHistoryStore.importBackup(context, input, oldPolicy, false)
        val (output, uri) = archive()
        instrumentation.runOnMainSync {
            assertTrue(controller.beginPicking(HistoryTransferAction.EXPORT))
            controller.acceptDocument(uri)
        }
        assertTrue(await<HistoryTransferState.Finished>().success)
        val exported = mutableListOf<NotificationHistoryBackupEntry>()
        NotificationHistoryBackupCodec.read(NotificationHistoryBackupCodec.utf8Reader(context.contentResolver.openInputStream(uri)!!)) {
            if (it.sourcePackage == source) exported += it
        }
        assertEquals("TEST ONLY 完整正文", exported.single().combinedText)
        instrumentation.runOnMainSync {
            controller.acknowledgeResult()
            assertTrue(controller.beginPicking(HistoryTransferAction.EXPORT))
            controller.acceptDocument(Uri.parse("content://missing.test.provider/backup.json"))
        }
        assertFalse(await<HistoryTransferState.Finished>().success)
    }
}
