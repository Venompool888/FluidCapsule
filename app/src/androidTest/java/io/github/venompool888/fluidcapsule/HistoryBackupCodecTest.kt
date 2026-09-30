package io.github.venompool888.fluidcapsule

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.venompool888.fluidcapsule.history.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringReader
import java.io.StringWriter

@RunWith(AndroidJUnit4::class)
class HistoryBackupCodecTest {
    private val text = "TEST ONLY · 中文🙂\n引号\"和反斜线\\\n" + "长正文".repeat(30_000)
    private val entry = NotificationHistoryBackupEntry(
        "legacy-event", "a".repeat(64), "test.only.codec", "合成应用", "测试标题",
        text, "标题\n$text", 100L, 200L, "UNKNOWN", "旧版本记录没有处理详情",
    )

    @Test fun roundTripPreservesLongTextAndArchiveFields() {
        val output = StringWriter()
        assertEquals(1L, NotificationHistoryBackupCodec.write(output, "1.2.0", 300L, sequenceOf(entry)))
        val json = JSONObject(output.toString())
        assertEquals("fluidcapsule.notification-history", json.getString("format"))
        assertEquals(1, json.getInt("schemaVersion"))
        val saved = json.getJSONArray("entries").getJSONObject(0)
        assertFalse(saved.has("id"))
        assertFalse(saved.has("active"))
        assertFalse(saved.has("notificationKey"))
        assertEquals(text, saved.getString("primaryText"))
        assertTrue(output.toString().contains("\n  \"format\""))
        val restored = mutableListOf<NotificationHistoryBackupEntry>()
        val metadata = NotificationHistoryBackupCodec.read(StringReader(output.toString()), restored::add)
        assertEquals(listOf(entry), restored)
        assertEquals(NotificationHistoryBackupMetadata("1.2.0", 300L, 1L), metadata)
    }

    @Test fun emptyArchiveIsValid() {
        val output = StringWriter()
        NotificationHistoryBackupCodec.write(output, "1.2.0", 300L, emptySequence())
        assertEquals(0L, NotificationHistoryBackupCodec.read(StringReader(output.toString())).count)
    }

    @Test fun rejectsIncorrectFormatVersionTypesAndTrailingContent() {
        val output = StringWriter()
        NotificationHistoryBackupCodec.write(output, "1.2.0", 300L, sequenceOf(entry))
        val good = output.toString()
        listOf(
            good.replace("fluidcapsule.notification-history", "other.backup"),
            good.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
            good.replace("\"postedAtMillis\": 100", "\"postedAtMillis\": \"100\""),
            good.replace("\"capturedAtMillis\": 200", "\"capturedAtMillis\": 1.5"),
            good.replace("\"title\": \"测试标题\"", "\"title\": null"),
            good.dropLast(4), good + " {}",
            good.replace("\"appVersion\": \"1.2.0\",", ""),
            good.replace("\"schemaVersion\": 1,", "\"schemaVersion\": 1, \"schemaVersion\": 1,"),
        ).forEach { bad ->
            assertThrows(Exception::class.java) { NotificationHistoryBackupCodec.read(StringReader(bad)) }
        }
    }
}
