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
import io.github.venompool888.fluidcapsule.settings.UserSettings
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
        else -> throw IllegalArgumentException(
            "unknown command; use status, whitelist-list/add/remove/clear, " +
                "set-show-otp-directly, set-mask-clipboard, " +
                "set-show-whitelist-content, or set-keep-alive",
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
            .put("whitelist", JSONArray(NotificationWhitelist.packages(context).sorted()))
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

    private fun result(command: String) = JSONObject().put("ok", true).put("command", command)

    private fun respond(code: Int, body: JSONObject) {
        resultCode = code
        resultData = body.toString()
    }

    companion object {
        private const val EXTRA_COMMAND = "command"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_VALUE = "value"
        private const val OPSTR_RECEIVE_SENSITIVE_NOTIFICATIONS =
            "android:receive_sensitive_notifications"
    }
}
