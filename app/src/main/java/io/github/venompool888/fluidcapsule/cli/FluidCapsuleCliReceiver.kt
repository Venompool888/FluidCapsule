package io.github.venompool888.fluidcapsule.cli

import android.app.Activity
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import io.github.venompool888.fluidcapsule.keepalive.KeepAliveService
import io.github.venompool888.fluidcapsule.notification.CapsuleNotificationListenerService
import io.github.venompool888.fluidcapsule.settings.NotificationWhitelist
import io.github.venompool888.fluidcapsule.settings.AppRule
import io.github.venompool888.fluidcapsule.settings.AppRuleStore
import io.github.venompool888.fluidcapsule.settings.UserSettings
import io.github.venompool888.fluidcapsule.history.NotificationHistoryStore
import org.json.JSONArray
import org.json.JSONObject

class FluidCapsuleCliReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra(EXTRA_COMMAND)?.trim().orEmpty()
        runCatching { execute(context, command, intent) }
            .onSuccess { respond(Activity.RESULT_OK, it) }
            .onFailure { error ->
                respond(
                    Activity.RESULT_CANCELED,
                    JSONObject()
                        .put("ok", false)
                        .put("command", command)
                        .put("error", error.message ?: error.javaClass.simpleName),
                )
            }
    }

    private fun execute(context: Context, command: String, intent: Intent): JSONObject = when (command) {
        "status" -> status(context)
        "whitelist-list" -> result(command).put(
            "packages",
            JSONArray(NotificationWhitelist.packages(context).sorted()),
        )
        "whitelist-add" -> updateWhitelist(context, command, requiredPackage(intent), true)
        "whitelist-remove" -> updateWhitelist(context, command, requiredPackage(intent), false)
        "whitelist-set-otp-only" -> {
            val packageName = requiredPackage(intent)
            val value = requiredBoolean(intent)
            NotificationWhitelist.setOtpOnly(context, packageName, value)
            result(command).put("package", packageName).put("value", value)
        }
        "whitelist-clear" -> {
            NotificationWhitelist.clear(context)
            result(command).put("packages", JSONArray())
        }
        "set-show-otp-directly" -> setBoolean(context, command, intent) {
            UserSettings.setShowOtpDirectly(context, it)
        }
        "set-mask-clipboard" -> setBoolean(context, command, intent) {
            UserSettings.setMaskOtpClipboardPreview(context, it)
        }
        "set-show-whitelist-content" -> setBoolean(context, command, intent) {
            UserSettings.setShowWhitelistContent(context, it)
        }
        "set-keep-alive" -> setKeepAlive(context, command, requiredBoolean(intent))
        "set-display-duration" -> setInteger(context, command, intent, 1, 30) {
            UserSettings.setCapsuleDisplayDurationMinutes(context, it)
        }
        "set-history-enabled" -> setBoolean(context, command, intent) {
            UserSettings.setNotificationHistoryEnabled(context, it)
        }
        "set-history-sort" -> {
            val value = requiredValue(intent).also {
                require(it == "time" || it == "app_count") { "value must be time|app_count" }
            }
            UserSettings.setNotificationHistorySortMode(context, value)
            result(command).put("value", value)
        }
        "set-history-retention" -> setInteger(context, command, intent, 1, 30) {
            UserSettings.setNotificationHistoryRetentionDays(context, it)
            NotificationHistoryStore.purgeOlderThanDays(context, it)
        }
        "app-rule-get" -> result(command).put("rule", ruleJson(context, requiredPackage(intent)))
        "app-rule-set" -> updateAppRule(context, intent)
        "app-rule-reset" -> {
            val packageName = requiredPackage(intent)
            AppRuleStore.reset(context, packageName)
            NotificationWhitelist.setOtpOnly(context, packageName, false)
            result(command).put("package", packageName).put("rule", ruleJson(context, packageName))
        }
        "history-count" -> result(command).put("count", NotificationHistoryStore.count(context))
        "history-clear" -> result(command).put("deleted", NotificationHistoryStore.clear(context))
        "history-delete-package" -> result(command).put(
            "deleted",
            NotificationHistoryStore.deletePackage(context, requiredPackage(intent)),
        )
        "history-purge" -> {
            val days = requiredInteger(intent, 1, 30)
            result(command).put("days", days).put("deleted", NotificationHistoryStore.purgeOlderThanDays(context, days))
        }
        else -> throw IllegalArgumentException(
            "unknown command; see docs/CLI.md",
        )
    }

    private fun status(context: Context): JSONObject {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val listenerEnabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        )?.split(':')
            ?.mapNotNull(ComponentName::unflattenFromString)
            ?.any {
                it.packageName == context.packageName &&
                    it.className == CapsuleNotificationListenerService::class.java.name
            }
            ?: false
        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }

        return result("status")
            .put("package", context.packageName)
            .put("version", packageInfo.versionName)
            .put("versionCode", if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode)
            .put("notificationPostingEnabled", notificationManager.areNotificationsEnabled())
            .put("notificationListenerEnabled", listenerEnabled)
            .put(
                "sensitiveNotificationsAllowed",
                appOpsManager.unsafeCheckOpNoThrow(
                    OPSTR_RECEIVE_SENSITIVE_NOTIFICATIONS,
                    Process.myUid(),
                    context.packageName,
                ) == AppOpsManager.MODE_ALLOWED,
            )
            .put(
                "promotedNotificationsAllowed",
                if (Build.VERSION.SDK_INT >= 36) notificationManager.canPostPromotedNotifications() else JSONObject.NULL,
            )
            .put("ignoringBatteryOptimizations", powerManager.isIgnoringBatteryOptimizations(context.packageName))
            .put("keepAliveRequested", UserSettings.keepAliveEnabled(context))
            .put("showOtpDirectly", UserSettings.showOtpDirectly(context))
            .put("maskClipboardPreview", UserSettings.maskOtpClipboardPreview(context))
            .put("showWhitelistContent", UserSettings.showWhitelistContent(context))
            .put("displayDurationMinutes", UserSettings.capsuleDisplayDurationMinutes(context))
            .put("historyEnabled", UserSettings.notificationHistoryEnabled(context))
            .put("historySort", UserSettings.notificationHistorySortMode(context))
            .put("historyRetentionDays", UserSettings.notificationHistoryRetentionDays(context))
            .put("historyCount", NotificationHistoryStore.count(context))
            .put("whitelist", JSONArray(NotificationWhitelist.packages(context).sorted()))
            .put("otpOnlyPackages", JSONArray(NotificationWhitelist.otpOnlyPackages(context).sorted()))
            .put("appRulePackages", JSONArray(AppRuleStore.packages(context).sorted()))
    }

    private fun updateWhitelist(
        context: Context,
        command: String,
        packageName: String,
        enabled: Boolean,
    ): JSONObject {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
        }
        NotificationWhitelist.setEnabled(context, packageName, enabled)
        return result(command)
            .put("package", packageName)
            .put("enabled", enabled)
            .put("packages", JSONArray(NotificationWhitelist.packages(context).sorted()))
    }

    private fun setBoolean(
        context: Context,
        command: String,
        intent: Intent,
        setter: (Boolean) -> Unit,
    ): JSONObject {
        val value = requiredBoolean(intent)
        setter(value)
        return result(command).put("value", value)
    }

    private fun setInteger(
        context: Context,
        command: String,
        intent: Intent,
        min: Int,
        max: Int,
        setter: (Int) -> Unit,
    ): JSONObject {
        val value = requiredInteger(intent, min, max)
        setter(value)
        return result(command).put("value", value)
    }

    private fun updateAppRule(context: Context, intent: Intent): JSONObject {
        val packageName = requiredPackage(intent)
        val key = intent.getStringExtra(EXTRA_KEY)?.trim()?.lowercase()
            ?: throw IllegalArgumentException("missing --es key ttl|priority|content|max-body|include|exclude|record-history|otp-only")
        val value = requiredValue(intent)
        val current = AppRuleStore.get(context, packageName)
        val updated = when (key) {
            "ttl" -> current.copy(ttlMinutes = value.toIntOrNull()?.takeIf { it in 0..30 }
                ?: throw IllegalArgumentException("ttl must be 0..30; 0 inherits global"))
            "priority" -> current.copy(priority = when (value.lowercase()) {
                "low", "-1" -> -1
                "normal", "0" -> 0
                "high", "1" -> 1
                else -> throw IllegalArgumentException("priority must be low|normal|high")
            })
            "content" -> current.copy(contentMode = value.lowercase().also {
                require(it in setOf(AppRule.CONTENT_INHERIT, AppRule.CONTENT_SHOW, AppRule.CONTENT_HIDE)) {
                    "content must be inherit|show|hide"
                }
            })
            "max-body" -> current.copy(maxBodyChars = value.toIntOrNull()?.takeIf { it in 20..500 }
                ?: throw IllegalArgumentException("max-body must be 20..500"))
            "include" -> current.copy(includeKeywords = value)
            "exclude" -> current.copy(excludeKeywords = value)
            "record-history" -> current.copy(recordHistory = parseBoolean(value))
            "otp-only" -> {
                NotificationWhitelist.setOtpOnly(context, packageName, parseBoolean(value))
                current
            }
            else -> throw IllegalArgumentException("unknown app rule key: $key")
        }
        AppRuleStore.set(context, packageName, updated)
        return result("app-rule-set").put("package", packageName).put("rule", ruleJson(context, packageName))
    }

    private fun ruleJson(context: Context, packageName: String): JSONObject {
        val rule = AppRuleStore.get(context, packageName)
        return JSONObject()
            .put("package", packageName)
            .put("ttlMinutes", rule.ttlMinutes)
            .put("priority", rule.priority)
            .put("contentMode", rule.contentMode)
            .put("maxBodyChars", rule.maxBodyChars)
            .put("includeKeywords", rule.includeKeywords)
            .put("excludeKeywords", rule.excludeKeywords)
            .put("recordHistory", rule.recordHistory)
            .put("otpOnly", NotificationWhitelist.isOtpOnly(context, packageName))
    }

    private fun setKeepAlive(context: Context, command: String, enabled: Boolean): JSONObject {
        UserSettings.setKeepAliveEnabled(context, enabled)
        val serviceIntent = Intent(context, KeepAliveService::class.java)
        if (enabled) {
            context.startForegroundService(serviceIntent)
        } else {
            context.stopService(serviceIntent)
        }
        return result(command).put("value", enabled)
    }

    private fun requiredPackage(intent: Intent): String =
        intent.getStringExtra(EXTRA_PACKAGE)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("missing --es package <package.name>")

    private fun requiredBoolean(intent: Intent): Boolean = when (
        intent.getStringExtra(EXTRA_VALUE)?.trim()?.lowercase()
    ) {
        "true", "1", "on", "yes" -> true
        "false", "0", "off", "no" -> false
        else -> throw IllegalArgumentException("missing or invalid --es value true|false")
    }

    private fun requiredValue(intent: Intent): String =
        intent.getStringExtra(EXTRA_VALUE)
            ?: throw IllegalArgumentException("missing --es value VALUE")

    private fun requiredInteger(intent: Intent, min: Int, max: Int): Int =
        requiredValue(intent).toIntOrNull()?.takeIf { it in min..max }
            ?: throw IllegalArgumentException("value must be an integer in $min..$max")

    private fun parseBoolean(value: String): Boolean = when (value.trim().lowercase()) {
        "true", "1", "on", "yes" -> true
        "false", "0", "off", "no" -> false
        else -> throw IllegalArgumentException("value must be true|false")
    }

    private fun result(command: String) = JSONObject().put("ok", true).put("command", command)

    private fun respond(code: Int, body: JSONObject) {
        resultCode = code
        resultData = body.toString()
    }

    companion object {
        private const val EXTRA_COMMAND = "command"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_VALUE = "value"
        private const val EXTRA_KEY = "key"
        private const val OPSTR_RECEIVE_SENSITIVE_NOTIFICATIONS =
            "android:receive_sensitive_notifications"
    }
}
