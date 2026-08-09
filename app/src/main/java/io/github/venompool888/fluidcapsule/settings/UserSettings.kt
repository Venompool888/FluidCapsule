package io.github.venompool888.fluidcapsule.settings

import android.content.Context

object UserSettings {
    private const val PREFS = "settings"
    private const val KEY_SHOW_OTP_DIRECTLY = "show_otp_directly"
    private const val KEY_MASK_OTP_CLIPBOARD_PREVIEW = "mask_otp_clipboard_preview"
    private const val KEY_SHOW_WHITELIST_CONTENT = "show_whitelist_content"
    private const val KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled"
    private const val KEY_SPEEDTEST_CLOUD_ENABLED = "speedtest_cloud_enabled"

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

    fun speedtestCloudEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SPEEDTEST_CLOUD_ENABLED, false)

    fun setSpeedtestCloudEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SPEEDTEST_CLOUD_ENABLED, enabled)
            .apply()
    }
}
