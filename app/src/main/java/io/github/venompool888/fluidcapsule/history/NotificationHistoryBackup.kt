package io.github.venompool888.fluidcapsule.history

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.database.CursorWindow
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.Writer
import java.nio.charset.CodingErrorAction

data class NotificationHistoryBackupEntry(
    val eventIdentity: String,
    val fingerprint: String,
    val sourcePackage: String,
    val sourceLabel: String,
    val title: String,
    val primaryText: String,
    val combinedText: String,
    val postedAtMillis: Long,
    val capturedAtMillis: Long,
    val decision: String,
    val decisionDetail: String,
)

data class NotificationHistoryBackupMetadata(
    val appVersion: String,
    val exportedAtMillis: Long,
    val count: Long,
)

object NotificationHistoryBackupCodec {
    private const val FORMAT = "fluidcapsule.notification-history"
    private val textFields = setOf(
        "eventIdentity", "fingerprint", "sourcePackage", "sourceLabel", "title",
        "primaryText", "combinedText", "decision", "decisionDetail",
    )
    private val timeFields = setOf("postedAtMillis", "capturedAtMillis")

    fun utf8Reader(input: InputStream): Reader = InputStreamReader(
        input,
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT),
    ).buffered()

    fun write(
        output: Writer,
        appVersion: String,
        exportedAtMillis: Long,
        entries: Sequence<NotificationHistoryBackupEntry>,
    ): Long {
        var count = 0L
        JsonWriter(output).use { json ->
            json.setIndent("  ")
            json.beginObject()
            json.name("format").value(FORMAT)
            json.name("schemaVersion").value(1)
            json.name("appVersion").value(appVersion)
            json.name("exportedAtMillis").value(exportedAtMillis)
            json.name("entries").beginArray()
            entries.forEach { entry ->
                json.beginObject()
                json.name("eventIdentity").value(entry.eventIdentity)
                json.name("fingerprint").value(entry.fingerprint)
                json.name("sourcePackage").value(entry.sourcePackage)
                json.name("sourceLabel").value(entry.sourceLabel)
                json.name("title").value(entry.title)
                json.name("primaryText").value(entry.primaryText)
                json.name("combinedText").value(entry.combinedText)
                json.name("postedAtMillis").value(entry.postedAtMillis)
                json.name("capturedAtMillis").value(entry.capturedAtMillis)
                json.name("decision").value(entry.decision)
                json.name("decisionDetail").value(entry.decisionDetail)
                json.endObject()
                count++
            }
            json.endArray().endObject()
        }
        return count
    }

    fun read(
        input: Reader,
        onEntry: (NotificationHistoryBackupEntry) -> Unit = {},
    ): NotificationHistoryBackupMetadata = CursorWindow("history-backup-validation").use { window ->
        JsonReader(StrictJsonInput(input)).use { json ->
        json.isLenient = false
        require(json.peek() == JsonToken.BEGIN_OBJECT) { "备份顶层必须为对象" }
        val fields = mutableSetOf<String>()
        var appVersion: String? = null
        var exportedAt: Long? = null
        var count = 0L
        json.beginObject()
        while (json.hasNext()) {
            val name = json.nextName()
            require(fields.add(name)) { "备份包含重复字段" }
            when (name) {
                "format" -> require(readText(json) == FORMAT) { "不是通知历史备份" }
                "schemaVersion" -> require(readInteger(json) == 1L) { "不支持的备份版本" }
                "appVersion" -> appVersion = readText(json)
                "exportedAtMillis" -> exportedAt = readInteger(json)
                "entries" -> {
                    require(json.peek() == JsonToken.BEGIN_ARRAY) { "备份记录必须为数组" }
                    json.beginArray()
                    while (json.hasNext()) {
                        onEntry(readEntry(json, window))
                        count++
                    }
                    json.endArray()
                }
                else -> json.skipValue()
            }
        }
        json.endObject()
        require(fields.containsAll(setOf("format", "schemaVersion", "appVersion", "exportedAtMillis", "entries"))) {
            "备份缺少必要字段"
        }
        require(json.peek() == JsonToken.END_DOCUMENT) { "备份尾部包含多余内容" }
        NotificationHistoryBackupMetadata(appVersion!!, exportedAt!!, count)
        }
    }

    private fun readEntry(json: JsonReader, window: CursorWindow): NotificationHistoryBackupEntry {
        require(json.peek() == JsonToken.BEGIN_OBJECT) { "通知记录必须为对象" }
        val seen = mutableSetOf<String>()
        val texts = mutableMapOf<String, String>()
        val times = mutableMapOf<String, Long>()
        window.clear()
        check(window.setNumColumns(textFields.size + timeFields.size) && window.allocRow())
        json.beginObject()
        while (json.hasNext()) {
            val name = json.nextName()
            require(seen.add(name)) { "通知记录包含重复字段" }
            when (name) {
                in textFields -> {
                    val value = readText(json)
                    require(window.putString(value, 0, textFields.indexOf(name))) { "单条通知太大，当前设备无法读取" }
                    texts[name] = value
                }
                in timeFields -> {
                    val value = readInteger(json)
                    require(window.putLong(value, 0, textFields.size + timeFields.indexOf(name))) { "单条通知太大，当前设备无法读取" }
                    times[name] = value
                }
                else -> json.skipValue()
            }
        }
        json.endObject()
        require(seen.containsAll(textFields + timeFields)) { "通知记录缺少必要字段" }
        require(texts.getValue("eventIdentity").isNotBlank()) { "通知记录缺少标识" }
        require(texts.getValue("fingerprint").matches(Regex("[a-f0-9]{64}"))) { "通知记录指纹无效" }
        require(texts.getValue("sourcePackage").isNotBlank()) { "通知记录缺少来源" }
        return NotificationHistoryBackupEntry(
            texts.getValue("eventIdentity"), texts.getValue("fingerprint"),
            texts.getValue("sourcePackage"), texts.getValue("sourceLabel"),
            texts.getValue("title"), texts.getValue("primaryText"), texts.getValue("combinedText"),
            times.getValue("postedAtMillis"), times.getValue("capturedAtMillis"),
            texts.getValue("decision"), texts.getValue("decisionDetail"),
        )
    }

    private fun readText(json: JsonReader): String {
        require(json.peek() == JsonToken.STRING) { "备份文字字段类型错误" }
        return json.nextString()
    }

    private fun readInteger(json: JsonReader): Long {
        require(json.peek() == JsonToken.NUMBER) { "备份数字字段类型错误" }
        val value = json.nextString()
        require(value.matches(Regex("0|[1-9][0-9]*"))) { "备份数字必须为非负整数" }
        return requireNotNull(value.toLongOrNull()) { "备份数字超出范围" }
    }
}

/** Android's JsonReader accepts illegal escapes and raw controls; validate every string, even skipped extensions. */
private class StrictJsonInput(private val input: Reader) : Reader() {
    private var quoted = false
    private var escaped = false
    private var unicodeRemaining = 0
    private var tokenLength = 0

    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        val count = input.read(buffer, offset, length)
        for (i in offset until offset + count.coerceAtLeast(0)) {
            val character = buffer[i]
            if (quoted) {
                require(++tokenLength <= 16 * 1024 * 1024) { "备份文字字段过长" }
                when {
                    unicodeRemaining > 0 -> {
                        require(character in '0'..'9' || character in 'a'..'f' || character in 'A'..'F') { "非法 Unicode 转义" }
                        unicodeRemaining--
                    }
                    escaped -> {
                        escaped = false
                        require(character in "\"\\/bfnrtu") { "非法 JSON 转义" }
                        if (character == 'u') unicodeRemaining = 4
                    }
                    character == '"' -> { quoted = false; tokenLength = 0 }
                    character == '\\' -> escaped = true
                    else -> require(character >= ' ') { "JSON 字符串含未转义控制字符" }
                }
            } else if (character == '"') {
                quoted = true
                tokenLength = 0
            } else if (character in "{}[],: \t\r\n") {
                tokenLength = 0
            } else {
                require(++tokenLength <= 1024) { "备份数字或字面量过长" }
            }
        }
        return count
    }

    override fun close() = input.close()
}
