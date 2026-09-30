package io.github.venompool888.fluidcapsule

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.venompool888.fluidcapsule.history.*
import io.github.venompool888.fluidcapsule.notification.NormalizedNotification
import io.github.venompool888.fluidcapsule.settings.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class HistoryBackupStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = "test.only.backup.${UUID.randomUUID()}"
    private val oldPolicy = UserSettings.notificationHistoryRetentionPolicy(context)
    private val files = mutableListOf<File>()

    @After fun cleanup() {
        NotificationHistoryStore.deletePackage(context, source)
        UserSettings.setNotificationHistoryRetentionPolicy(context, oldPolicy)
        files.forEach { it.delete() }
    }

    private fun entry(index: Int, captured: Long = System.currentTimeMillis()) = NotificationHistoryBackupEntry(
        "$source:$index", index.toString(16).padStart(64, '0'), source, "合成应用",
        "TEST ONLY $index", "中文🙂\n\"完整正文\"", "标题\n正文", 100L, captured, "SKIPPED", "合成原因",
    )

    private fun archive(vararg entries: NotificationHistoryBackupEntry): File =
        File.createTempFile("backup-test-", ".json", context.cacheDir).also { file ->
            files += file
            file.bufferedWriter().use { NotificationHistoryBackupCodec.write(it, "test", 300L, entries.asSequence()) }
        }

    private fun notification(index: Int, body: String = "TEST ONLY 原文") = NormalizedNotification(
        packageName = source, notificationKey = "$source:$index", title = "TEST ONLY $index",
        primaryText = body, messageTexts = listOf(body), combinedText = body,
        postedAtMillis = 100L + index, contentIntent = null, smallIcon = null, largeIcon = null,
        senderIcon = null, actions = emptyList(), isGroupSummary = false, isOngoing = false, channelId = "test",
    )

    @Test fun exportIncludesMoreThanDisplayLimitAndRoundTripSkipsExistingRecords() {
        repeat(260) { NotificationHistoryStore.record(context, notification(it)) }
        val output = StringWriter()
        NotificationHistoryStore.exportBackup(context, output, "test", 300L)
        val entries = mutableListOf<NotificationHistoryBackupEntry>()
        NotificationHistoryBackupCodec.read(StringReader(output.toString())) { if (it.sourcePackage == source) entries += it }
        assertEquals(260, entries.size)
        val file = archive(*entries.toTypedArray())
        val firstIds = NotificationHistoryStore.forPackage(context, source, 1000).map { it.id }
        assertEquals(NotificationHistoryImportResult(0L, 260L, 0L),
            NotificationHistoryStore.importBackup(context, file, oldPolicy, false))
        NotificationHistoryStore.deletePackage(context, source)
        assertEquals(NotificationHistoryImportResult(260L, 0L, 0L),
            NotificationHistoryStore.importBackup(context, file, oldPolicy, false))
        assertTrue(NotificationHistoryStore.forPackage(context, source, 1000).none { it.id in firstIds })
        val again = StringWriter()
        NotificationHistoryStore.exportBackup(context, again, "test", 300L)
        val restored = mutableListOf<NotificationHistoryBackupEntry>()
        NotificationHistoryBackupCodec.read(StringReader(again.toString())) { if (it.sourcePackage == source) restored += it }
        assertEquals(entries, restored)
        context.openOrCreateDatabase("notification_history.db", Context.MODE_PRIVATE, null).use { db ->
            db.rawQuery("SELECT active, notification_key FROM notification_history WHERE package_name = ?", arrayOf(source)).use { cursor ->
                while (cursor.moveToNext()) { assertEquals(0, cursor.getInt(0)); assertTrue(cursor.isNull(1)) }
            }
        }
    }

    @Test fun mergesByEitherIdentityOrFingerprintWithoutReplacingExistingText() {
        val original = entry(1)
        NotificationHistoryStore.importBackup(context, archive(original), oldPolicy, false)
        val file = archive(
            original.copy(primaryText = "不应覆盖", fingerprint = "b".repeat(64)),
            entry(2).copy(fingerprint = original.fingerprint), entry(3), entry(3),
        )
        assertEquals(NotificationHistoryImportResult(1L, 3L, 0L),
            NotificationHistoryStore.importBackup(context, file, oldPolicy, false))
        val rows = NotificationHistoryStore.forPackage(context, source)
        assertEquals(2, rows.size)
        assertEquals(original.primaryText, rows.single { it.title == original.title }.primaryText)
    }

    @Test fun expiryChoiceSkipsOldRowsOrKeepsThemWithPermanentRetention() {
        val policy = HistoryRetentionPolicy.DEFAULT
        UserSettings.setNotificationHistoryRetentionPolicy(context, policy)
        val file = archive(entry(1, 0L), entry(2))
        assertEquals(NotificationHistoryImportResult(1L, 0L, 1L),
            NotificationHistoryStore.importBackup(context, file, policy, false))
        assertEquals(policy, UserSettings.notificationHistoryRetentionPolicy(context))
        assertEquals(NotificationHistoryImportResult(1L, 1L, 0L),
            NotificationHistoryStore.importBackup(context, file, policy, true))
        assertEquals(HistoryRetentionUnit.FOREVER, UserSettings.notificationHistoryRetentionPolicy(context).unit)
        assertEquals(0, NotificationHistoryStore.purgeExpiredForCurrentPolicy(context))
        assertEquals(2, NotificationHistoryStore.forPackage(context, source).size)
    }

    @Test fun invalidTailRollsBackEarlierRowsAndRetentionSetting() {
        UserSettings.setNotificationHistoryRetentionPolicy(context, HistoryRetentionPolicy.DEFAULT)
        val file = archive(entry(1, 0L), entry(2))
        file.appendText(" {}")
        assertThrows(Exception::class.java) {
            NotificationHistoryStore.importBackup(context, file, HistoryRetentionPolicy.DEFAULT, true)
        }
        assertTrue(NotificationHistoryStore.forPackage(context, source).isEmpty())
        assertEquals(HistoryRetentionPolicy.DEFAULT, UserSettings.notificationHistoryRetentionPolicy(context))
    }

    @Test fun changedPolicyRequiresFreshConfirmationAndEmptyBackupIsSafe() {
        val file = archive(entry(1))
        val nextPolicy = HistoryRetentionPolicy(20, HistoryRetentionUnit.DAYS)
        UserSettings.setNotificationHistoryRetentionPolicy(context, nextPolicy)
        assertThrows(HistoryRetentionChangedException::class.java) {
            NotificationHistoryStore.importBackup(context, file, oldPolicy, false)
        }
        assertTrue(NotificationHistoryStore.forPackage(context, source).isEmpty())
        assertEquals(NotificationHistoryImportResult(0L, 0L, 0L),
            NotificationHistoryStore.importBackup(context, archive(), nextPolicy, false))
    }

    @Test fun oversizedRecordIsRejectedBeforeItCanBreakHistoryReads() {
        val file = archive(entry(1).copy(combinedText = "x".repeat(3 * 1024 * 1024)))
        assertThrows(Exception::class.java) {
            NotificationHistoryStore.importBackup(context, file, oldPolicy, false)
        }
        assertTrue(NotificationHistoryStore.forPackage(context, source).isEmpty())
        assertEquals(oldPolicy, UserSettings.notificationHistoryRetentionPolicy(context))
    }

    @Test fun failedDurableRetentionWriteRollsBackImportedRows() {
        UserSettings.setNotificationHistoryRetentionPolicy(context, HistoryRetentionPolicy.DEFAULT)
        val failing = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val actual = context.getSharedPreferences(name, mode)
                return object : SharedPreferences by actual {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = actual.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                                editor.putInt(key, value)
                                return this
                            }
                            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                                editor.putString(key, value)
                                return this
                            }
                            override fun commit(): Boolean = false
                        }
                    }
                }
            }
        }
        assertThrows(Exception::class.java) {
            NotificationHistoryStore.importBackup(failing, archive(entry(1, 0L)), HistoryRetentionPolicy.DEFAULT, true)
        }
        assertTrue(NotificationHistoryStore.forPackage(context, source).isEmpty())
        assertEquals(HistoryRetentionPolicy.DEFAULT, UserSettings.notificationHistoryRetentionPolicy(context))
    }

    @Test fun historyReadOnMainThreadDoesNotWaitForExportTransaction() {
        NotificationHistoryStore.record(context, notification(1))
        val paused = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val output = object : Writer() {
            var first = true
            override fun write(chars: CharArray, offset: Int, length: Int) {
                if (first) { first = false; paused.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
            }
            override fun flush() {}
            override fun close() {}
        }
        val export = Thread {
            try { NotificationHistoryStore.exportBackup(context, output, "test", 300L) }
            catch (error: Throwable) { failure.set(error) }
        }
        export.start()
        assertTrue(paused.await(5, TimeUnit.SECONDS))
        val safetyRelease = Thread { Thread.sleep(2000); release.countDown() }.apply { start() }
        val start = System.nanoTime()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { NotificationHistoryStore.count(context) }
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000
        release.countDown()
        export.join(5000); safetyRelease.join(3000)
        assertNull(failure.get())
        assertTrue("History query blocked UI for $elapsedMillis ms", elapsedMillis < 1000)
    }

    @Test fun exportTransactionExcludesConcurrentUpdatesAndInsertions() {
        NotificationHistoryStore.record(context, notification(1))
        val started = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val writer = StringWriter()
        val update = Thread {
            started.countDown()
            try {
                NotificationHistoryStore.record(context, notification(1, "TEST ONLY 新文"))
                NotificationHistoryStore.record(context, notification(2))
            } catch (error: Throwable) { failure.set(error) }
        }
        val coordinating = object : Writer() {
            var first = true
            override fun write(chars: CharArray, offset: Int, length: Int) {
                if (first) { first = false; update.start(); assertTrue(started.await(2, TimeUnit.SECONDS)) }
                writer.write(chars, offset, length)
            }
            override fun flush() = writer.flush()
            override fun close() = writer.close()
        }
        NotificationHistoryStore.exportBackup(context, coordinating, "test", 300L)
        update.join(5000)
        assertFalse(update.isAlive)
        assertNull(failure.get())
        val saved = mutableListOf<NotificationHistoryBackupEntry>()
        NotificationHistoryBackupCodec.read(StringReader(writer.toString())) { if (it.sourcePackage == source) saved += it }
        assertEquals(1, saved.size)
        assertEquals("TEST ONLY 原文", saved.single().primaryText)
        assertEquals(2, NotificationHistoryStore.forPackage(context, source).size)
    }
}
