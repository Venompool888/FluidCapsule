package io.github.venompool888.fluidcapsule.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationStageClassifierTest {
    @Test fun localSendProgressIsRecognized() {
        val result = NotificationStageClassifier.classify(
            NotificationStageClassifier.LOCALSEND_PACKAGE,
            "Receiving holiday.zip 47%",
        )!!
        assertEquals("传输中 47%", result.label)
        assertEquals(47, result.progress)
        assertFalse(result.terminal)
    }

    @Test fun meituanOrderAndMarketingAreSeparated() {
        val order = NotificationStageClassifier.classify(
            NotificationStageClassifier.MEITUAN_PACKAGE,
            "您的订单骑手已取餐，正在配送中",
        )!!
        assertEquals("配送中", order.label)
        assertFalse(order.suppress)

        val marketing = NotificationStageClassifier.classify(
            NotificationStageClassifier.MEITUAN_PACKAGE,
            "限时红包，点击领券",
        )!!
        assertTrue(marketing.suppress)
    }
}
