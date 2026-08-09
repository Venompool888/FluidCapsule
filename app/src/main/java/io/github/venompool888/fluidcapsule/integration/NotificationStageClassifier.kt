package io.github.venompool888.fluidcapsule.integration

object NotificationStageClassifier {
    const val LOCALSEND_PACKAGE = "org.localsend.localsend_app"
    const val MEITUAN_PACKAGE = "com.sankuai.meituan"

    fun classify(packageName: String, text: String): NotificationStage? = when (packageName) {
        LOCALSEND_PACKAGE -> classifyLocalSend(text)
        MEITUAN_PACKAGE -> classifyMeituan(text)
        else -> null
    }

    private fun classifyLocalSend(text: String): NotificationStage? {
        val normalized = text.lowercase()
        val percent = PERCENT.find(normalized)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100)
        return when {
            containsAny(normalized, "failed", "error", "cancelled", "canceled", "失败", "错误", "已取消") ->
                NotificationStage("传输失败", percent, true)
            containsAny(normalized, "completed", "complete", "finished", "received", "成功", "已完成", "接收完成") ->
                NotificationStage("传输完成", 100, true)
            percent != null -> NotificationStage("传输中 $percent%", percent, false)
            containsAny(normalized, "incoming", "receive request", "accept", "传入", "接收请求", "是否接收") ->
                NotificationStage("接收请求", 0, false)
            containsAny(normalized, "sending", "receiving", "transferring", "发送中", "接收中", "传输中") ->
                NotificationStage("传输中", null, false, indeterminate = true)
            else -> null
        }
    }

    private fun classifyMeituan(text: String): NotificationStage? {
        val normalized = text.lowercase()
        val orderStage = when {
            containsAny(normalized, "已送达", "订单完成", "已完成") -> NotificationStage("已送达", 100, true)
            containsAny(normalized, "配送中", "正在配送", "骑手已取", "送餐中") -> NotificationStage("配送中", 72, false)
            containsAny(normalized, "备餐", "制作中", "正在制作", "商家出餐") -> NotificationStage("商家制作中", 42, false)
            containsAny(normalized, "商家已接单", "已接单") -> NotificationStage("商家已接单", 18, false)
            else -> null
        }
        if (orderStage != null) return orderStage
        if (containsAny(
                normalized,
                "优惠", "红包", "领券", "限时", "特价", "秒杀", "满减", "推荐", "活动", "会员", "签到", "抽奖", "福利", "免单",
            )
        ) {
            return NotificationStage("营销通知", null, true, suppress = true)
        }
        return null
    }

    private fun containsAny(text: String, vararg needles: String) = needles.any(text::contains)

    private val PERCENT = Regex("(\\d{1,3})\\s*%")
}

data class NotificationStage(
    val label: String,
    val progress: Int?,
    val terminal: Boolean,
    val indeterminate: Boolean = false,
    val suppress: Boolean = false,
)
