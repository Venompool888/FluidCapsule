package io.github.venompool888.fluidcapsule.ui.state

import android.app.Activity
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import io.github.venompool888.fluidcapsule.diagnostics.DiagnosticsSnapshot
import io.github.venompool888.fluidcapsule.diagnostics.DiagnosticsStore
import io.github.venompool888.fluidcapsule.keepalive.KeepAliveAccessibilityService
import io.github.venompool888.fluidcapsule.notification.CapsuleNotificationListenerService
import io.github.venompool888.fluidcapsule.settings.UserSettings

data class SystemHealthState(
    val notificationListenerEnabled: Boolean,
    val listenerConnected: Boolean,
    val postNotificationsGranted: Boolean,
    val sensitiveNotificationsGranted: Boolean,
    val promotedNotificationsAllowed: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val accessibilityKeepAliveEnabled: Boolean,
    val accessibilityConnected: Boolean,
    val keepAliveRequested: Boolean,
    val diagnostics: DiagnosticsSnapshot,
) {
    val listenerReady: Boolean
        get() = notificationListenerEnabled && listenerConnected

    val accessibilityReady: Boolean
        get() = accessibilityKeepAliveEnabled && accessibilityConnected

    val coreReadyCount: Int
        get() = listOf(
            listenerReady,
            postNotificationsGranted,
            sensitiveNotificationsGranted,
            promotedNotificationsAllowed,
            batteryOptimizationIgnored,
        ).count { it }

    val isAllCoreReady: Boolean
        get() = coreReadyCount == CORE_CHECK_COUNT

    companion object {
        const val CORE_CHECK_COUNT = 5
    }
}

sealed interface PromotionSettingsLaunchResult {
    data object OpenedPromotionSettings : PromotionSettingsLaunchResult
    data object FallbackToAppNotificationSettings : PromotionSettingsLaunchResult
    data object Unavailable : PromotionSettingsLaunchResult
}

object SystemHealthChecker {
    private const val OPSTR_RECEIVE_SENSITIVE_NOTIFICATIONS =
        "android:receive_sensitive_notifications"

    fun check(context: Context): SystemHealthState {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val diagnostics = DiagnosticsStore.readSnapshot(context)

        val listenerEnabled = hasEnabledComponent(
            rawSetting = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ),
            packageName = context.packageName,
            className = CapsuleNotificationListenerService::class.java.name,
        )
        val accessibilityEnabled = hasEnabledComponent(
            rawSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ),
            packageName = context.packageName,
            className = KeepAliveAccessibilityService::class.java.name,
        )
        val postGranted = notificationManager?.areNotificationsEnabled() ?: false
        val sensitiveGranted = if (appOpsManager == null) {
            false
        } else {
            runCatching {
                appOpsManager.checkOpNoThrow(
                    OPSTR_RECEIVE_SENSITIVE_NOTIFICATIONS,
                    Process.myUid(),
                    context.packageName,
                ) == AppOpsManager.MODE_ALLOWED
            }.getOrDefault(false)
        }
        val promotedAllowed = if (Build.VERSION.SDK_INT >= 36 && notificationManager != null) {
            runCatching { notificationManager.canPostPromotedNotifications() }.getOrDefault(false)
        } else {
            false
        }
        val batteryIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        return SystemHealthState(
            notificationListenerEnabled = listenerEnabled,
            listenerConnected = diagnostics.listenerConnected,
            postNotificationsGranted = postGranted,
            sensitiveNotificationsGranted = sensitiveGranted,
            promotedNotificationsAllowed = promotedAllowed,
            batteryOptimizationIgnored = batteryIgnored,
            accessibilityKeepAliveEnabled = accessibilityEnabled,
            accessibilityConnected = diagnostics.accessibilityConnected,
            keepAliveRequested = UserSettings.keepAliveEnabled(context),
            diagnostics = diagnostics,
        )
    }

    fun openNotificationListenerSettings(context: Context): Boolean =
        startActivitySafely(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    fun openAppNotificationSettings(context: Context): Boolean =
        startActivitySafely(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )

    fun openSensitiveNotificationSettings(context: Context): Boolean =
        startActivitySafely(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null)),
        ) || openAppNotificationSettings(context)

    fun openPromotionSettingsSafely(context: Context): PromotionSettingsLaunchResult {
        if (Build.VERSION.SDK_INT >= 36) {
            val promotionIntent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
            if (startActivitySafely(context, promotionIntent)) {
                return PromotionSettingsLaunchResult.OpenedPromotionSettings
            }
        }
        DiagnosticsStore.markPublish(
            context,
            "PROMOTION_SETTINGS",
            "unsupported_promotion_settings_fallback_to_app_notifications",
        )
        return if (openAppNotificationSettings(context)) {
            PromotionSettingsLaunchResult.FallbackToAppNotificationSettings
        } else {
            PromotionSettingsLaunchResult.Unavailable
        }
    }

    fun openBatteryOptimizationSettings(context: Context): Boolean =
        startActivitySafely(
            context,
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ),
        ) || startActivitySafely(
            context,
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )

    fun openAccessibilitySettings(context: Context): Boolean =
        startActivitySafely(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun hasEnabledComponent(
        rawSetting: String?,
        packageName: String,
        className: String,
    ): Boolean = rawSetting
        ?.split(':')
        ?.mapNotNull(ComponentName::unflattenFromString)
        ?.any { it.packageName == packageName && it.className == className }
        ?: false

    private fun startActivitySafely(context: Context, intent: Intent): Boolean {
        val launchIntent = if (context is Activity) {
            intent
        } else {
            Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(launchIntent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
