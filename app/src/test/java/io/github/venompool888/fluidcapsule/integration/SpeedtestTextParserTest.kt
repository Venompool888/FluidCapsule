package io.github.venompool888.fluidcapsule.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SpeedtestTextParserTest {
    @Test fun parsesEnglishMetricsAcrossNodes() {
        val result = SpeedtestTextParser.parse(
            listOf("PING", "12", "DOWNLOAD Mbps", "318.45", "UPLOAD Mbps", "48.2"),
        )
        assertNotNull(result)
        assertEquals(12.0, result!!.pingMs!!, 0.001)
        assertEquals(318.45, result.downloadMbps!!, 0.001)
        assertEquals(48.2, result.uploadMbps!!, 0.001)
        assertEquals(100, result.progress)
    }

    @Test fun parsesChineseInlineMetrics() {
        val result = SpeedtestTextParser.parse(listOf("延迟 8 ms", "下载 95.6 Mbps"))!!
        assertEquals("↓ 95.6", result.shortText)
        assertEquals(66, result.progress)
    }
}
