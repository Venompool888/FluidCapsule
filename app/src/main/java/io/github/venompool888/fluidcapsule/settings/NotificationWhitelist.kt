package io.github.venompool888.fluidcapsule.settings

import android.content.Context

object NotificationWhitelist {
    private const val PREFS = "notification_whitelist"
    private const val KEY_PACKAGES = "packages"
    private const val KEY_OTP_ONLY_PACKAGES = "otp_only_packages"

    fun packages(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PACKAGES, emptySet())
            ?.toSet()
            .orEmpty()

    fun contains(context: Context, packageName: String): Boolean =
        packageName in packages(context)

    fun otpOnlyPackages(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_OTP_ONLY_PACKAGES, emptySet())
            ?.toSet()
            .orEmpty()

    fun isOtpOnly(context: Context, packageName: String): Boolean =
        packageName in otpOnlyPackages(context)

    fun setEnabled(context: Context, packageName: String, enabled: Boolean) {
        val updated = packages(context).toMutableSet()
        if (enabled) updated += packageName else updated -= packageName
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_PACKAGES, updated)
            .apply()
    }

    fun setOtpOnly(context: Context, packageName: String, enabled: Boolean) {
        val updated = otpOnlyPackages(context).toMutableSet()
        if (enabled) updated += packageName else updated -= packageName
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_OTP_ONLY_PACKAGES, updated)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PACKAGES)
            .remove(KEY_OTP_ONLY_PACKAGES)
            .apply()
    }
}
