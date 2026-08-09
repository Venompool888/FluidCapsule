package io.github.venompool888.fluidcapsule.integration

object SpeedtestTextParser {
    fun parse(rawTexts: List<String>): SpeedtestSnapshot? {
        val texts = rawTexts.map(String::trim).filter(String::isNotEmpty).distinct()
        if (texts.isEmpty()) return null
        val joined = texts.joinToString(" | ")
        val ping = findMetric(texts, joined, listOf("ping", "延迟", "idle ping"))
        val download = findMetric(texts, joined, listOf("download", "下载"))
        val upload = findMetric(texts, joined, listOf("upload", "上传"))
        if (ping == null && download == null && upload == null) return null
        return SpeedtestSnapshot(ping, download, upload)
    }

    private fun findMetric(texts: List<String>, joined: String, labels: List<String>): Double? {
        labels.forEach { label ->
            val inline = Regex("(?i)${Regex.escape(label)}[^0-9]{0,24}([0-9]+(?:\\.[0-9]+)?)")
                .find(joined)
                ?.groupValues
                ?.get(1)
                ?.toDoubleOrNull()
            if (inline != null) return inline

            val index = texts.indexOfFirst { it.equals(label, true) || it.contains(label, true) }
            if (index >= 0) {
                texts.drop(index + 1).take(3).forEach { candidate ->
                    NUMBER.find(candidate)?.value?.toDoubleOrNull()?.let { return it }
                }
            }
        }
        return null
    }

    private val NUMBER = Regex("[0-9]+(?:\\.[0-9]+)?")
}

data class SpeedtestSnapshot(
    val pingMs: Double?,
    val downloadMbps: Double?,
    val uploadMbps: Double?,
) {
    val progress: Int
        get() = when {
            uploadMbps != null -> 100
            downloadMbps != null -> 66
            pingMs != null -> 33
            else -> 0
        }

    val shortText: String
        get() = when {
            uploadMbps != null -> "↑ ${format(uploadMbps)}"
            downloadMbps != null -> "↓ ${format(downloadMbps)}"
            pingMs != null -> "${format(pingMs)} ms"
            else -> "测速中"
        }

    fun body(): String = buildList {
        pingMs?.let { add("Ping ${format(it)} ms") }
        downloadMbps?.let { add("下载 ${format(it)} Mbps") }
        uploadMbps?.let { add("上传 ${format(it)} Mbps") }
    }.joinToString(" · ")

    private fun format(value: Double): String = if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        "%.2f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
    }
}
