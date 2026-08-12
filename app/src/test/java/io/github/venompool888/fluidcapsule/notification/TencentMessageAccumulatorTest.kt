package io.github.venompool888.fluidcapsule.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TencentMessageAccumulatorTest {
    @Test
    fun rebuildsWechatMessagesFromSuccessiveSummaryUpdates() {
        val accumulator = TencentMessageAccumulator()

        assertEquals(
            listOf("1"),
            updateWechat(accumulator, "Rin: 1", 1).messages,
        )
        assertEquals(
            listOf("1", "2"),
            updateWechat(accumulator, "[2条]Rin: 2", 2).messages,
        )
        assertEquals(
            listOf("1", "2", "3"),
            updateWechat(accumulator, "[3条]Rin: 3", 3).messages,
        )
        assertEquals(
            listOf("1", "2", "3", "4"),
            updateWechat(accumulator, "[4条]Rin: 4", 4).messages,
        )
    }

    @Test
    fun rebuildsQqMessagesAndRemovesCountFromConversationTitle() {
        val accumulator = TencentMessageAccumulator()
        val first = accumulator.update(
            TencentMessageAccumulator.QQ_PACKAGE,
            "qq-key",
            "漫威 | death",
            "a",
            1,
        )!!
        val third = accumulator.update(
            TencentMessageAccumulator.QQ_PACKAGE,
            "qq-key",
            "漫威 | death(3条新消息)",
            "ber",
            3,
        )!!

        assertEquals("漫威 | death", first.conversationTitle)
        assertEquals("漫威 | death", third.conversationTitle)
        assertEquals(listOf("a", "ber"), third.messages)
    }

    @Test
    fun repeatedMessageIsKeptWhenReportedCountAdvances() {
        val accumulator = TencentMessageAccumulator()
        updateWechat(accumulator, "Rin: hello", 1)
        val second = updateWechat(accumulator, "[2条]Rin: hello", 2)

        assertEquals(listOf("hello", "hello"), second.messages)
    }

    @Test
    fun removalDropsPreviouslyCapturedMessages() {
        val accumulator = TencentMessageAccumulator()
        updateWechat(accumulator, "Rin: 1", 1)
        updateWechat(accumulator, "[2条]Rin: 2", 2)
        accumulator.remove("wechat-key")

        assertEquals(
            listOf("3"),
            updateWechat(accumulator, "[3条]Rin: 3", 3).messages,
        )
    }

    @Test
    fun ignoresOtherApps() {
        val accumulator = TencentMessageAccumulator()
        assertNull(accumulator.update("com.whatsapp", "key", "Elva", "hello", 1))
    }

    private fun updateWechat(
        accumulator: TencentMessageAccumulator,
        text: String,
        now: Long,
    ) = accumulator.update(
        TencentMessageAccumulator.WECHAT_PACKAGE,
        "wechat-key",
        "Rin",
        text,
        now,
    )!!
}
