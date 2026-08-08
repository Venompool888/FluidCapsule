package io.github.venompool888.fluidcapsule.diagnostics

import android.content.Context

object DiagnosticsStore {
    private const val PREFS = "diagnostics"

    fun markListenerConnected(context: Context, connected: Boolean) {
        edit(context, "listener_connected", connected)
        edit(context, "listener_changed_at", System.currentTimeMillis())
    }

    fun markNotificationSeen(context: Context, packageName: String) {
        edit(context, "last_notification_package", packageName)
        edit(context, "last_notification_at", System.currentTimeMillis())
    }

    fun markParse(context: Context, outcome: String) {
        edit(context, "last_parse_outcome", outcome)
        edit(context, "last_parse_at", System.currentTimeMillis())
    }

    fun markPublish(context: Context, publisher: String, detail: String) {
        edit(context, "last_publisher", publisher)
        edit(context, "last_publish_detail", detail)
        edit(context, "last_publish_at", System.currentTimeMillis())
    }

    fun snapshot(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return buildString {
            appendLine("通知监听连接：${prefs.getBoolean("listener_connected", false)}")
            appendLine("最近通知来源：${prefs.getString("last_notification_package", "尚无")}")
            appendLine("最近解析结果：${prefs.getString("last_parse_outcome", "尚无")}")
            appendLine("最近发布器：${prefs.getString("last_publisher", "尚无")}")
            append("发布详情：${prefs.getString("last_publish_detail", "尚无")}")
        }
    }

    private fun edit(context: Context, key: String, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, value).apply()
    }

    private fun edit(context: Context, key: String, value: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(key, value).apply()
    }

    private fun edit(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }
}
