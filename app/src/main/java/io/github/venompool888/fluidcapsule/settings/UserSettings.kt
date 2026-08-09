package io.github.venompool888.fluidcapsule.settings

import android.content.Context

object UserSettings {
    private const val PREFS = "settings"
    private const val KEY_SHOW_OTP_DIRECTLY = "show_otp_directly"
    private const val KEY_MASK_OTP_CLIPBOARD_PREVIEW = "mask_otp_clipboard_preview"
    private const val KEY_SHOW_WHITELIST_CONTENT = "show_whitelist_content"
    private const val KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled"
    private const val KEY_NOTIFICATION_HISTORY_ENABLED = "notification_history_enabled"
    private const val KEY_NOTIFICATION_HISTORY_SORT_MODE = "notification_history_sort_mode"

    fun showOtpDirectly(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_OTP_DIRECTLY, true)

    fun setShowOtpDirectly(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_OTP_DIRECTLY, enabled)
            .apply()
    }

    fun maskOtpClipboardPreview(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MASK_OTP_CLIPBOARD_PREVIEW, false)

    fun setMaskOtpClipboardPreview(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MASK_OTP_CLIPBOARD_PREVIEW, enabled)
            .apply()
    }

    fun showWhitelistContent(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_WHITELIST_CONTENT, true)

    fun setShowWhitelistContent(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_WHITELIST_CONTENT, enabled)
            .apply()
    }

    fun keepAliveEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_ALIVE_ENABLED, false)

    fun setKeepAliveEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_KEEP_ALIVE_ENABLED, enabled)
            .apply()
    }

    fun notificationHistoryEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATION_HISTORY_ENABLED, false)

    fun setNotificationHistoryEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATION_HISTORY_ENABLED, enabled)
            .apply()
    }

    fun notificationHistorySortMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NOTIFICATION_HISTORY_SORT_MODE, "time")
            ?: "time"

    fun setNotificationHistorySortMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NOTIFICATION_HISTORY_SORT_MODE, mode)
            .apply()
    }
}
