package io.github.venompool888.fluidcapsule.ui.state

import io.github.venompool888.fluidcapsule.diagnostics.DiagnosticsSnapshot
import io.github.venompool888.fluidcapsule.settings.AppRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateModelsTest {
    @Test
    fun systemHealthStateTracksCoreAndAccessibilityChannelsSeparately() {
        val base = SystemHealthState(
            notificationListenerEnabled = true,
            listenerConnected = false,
            postNotificationsGranted = true,
            sensitiveNotificationsGranted = true,
            promotedNotificationsAllowed = false,
            batteryOptimizationIgnored = true,
            accessibilityKeepAliveEnabled = true,
            accessibilityConnected = true,
            keepAliveRequested = true,
            diagnostics = DiagnosticsSnapshot(
                listenerConnected = false,
                listenerChangedAt = 0L,
                lastNotificationPackage = null,
                lastNotificationAt = 0L,
                lastParseOutcome = null,
                lastParseAt = 0L,
                lastPublisher = null,
                lastPublishDetail = null,
                lastPublishAt = 0L,
                accessibilityConnected = true,
                accessibilityChangedAt = 1_000L,
            ),
        )
        assertFalse(base.listenerReady)
        assertTrue(base.accessibilityReady)
        assertEquals(3, base.coreReadyCount)
        assertFalse(base.isAllCoreReady)

        val ready = base.copy(listenerConnected = true, promotedNotificationsAllowed = true)
        assertTrue(ready.listenerReady)
        assertEquals(SystemHealthState.CORE_CHECK_COUNT, ready.coreReadyCount)
        assertTrue(ready.isAllCoreReady)
    }

    @Test
    fun appsUiStateFiltersByCategoryAndSearchQuery() {
        val wechat = AppEntryUi(
            packageName = "com.tencent.mm",
            label = "微信",
            isEmailApp = false,
            isDefaultSms = false,
            isEnabled = true,
            otpOnly = false,
            rule = AppRule(),
            capabilityTags = listOf("多条消息重建与快捷回复"),
            supportsProgressOrReply = true,
        )
        val gmail = AppEntryUi(
            packageName = "com.google.android.gm",
            label = "Gmail",
            isEmailApp = true,
            isDefaultSms = false,
            isEnabled = false,
            otpOnly = true,
            rule = AppRule(),
            capabilityTags = listOf("邮箱"),
            supportsProgressOrReply = false,
        )
        val bank = AppEntryUi(
            packageName = "com.example.bank",
            label = "掌上银行",
            isEmailApp = false,
            isDefaultSms = false,
            isEnabled = true,
            otpOnly = false,
            rule = AppRule(ttlMinutes = 10),
            capabilityTags = emptyList(),
            supportsProgressOrReply = false,
        )
        val state = AppsUiState(entries = listOf(wechat, gmail, bank))

        assertTrue(gmail.hasCustomRule)
        assertTrue(bank.hasCustomRule)
        assertFalse(wechat.hasCustomRule)

        assertEquals(listOf(wechat, gmail, bank), state.visibleEntries)
        assertEquals(listOf(wechat, bank), state.copy(filter = AppFilter.ENABLED).visibleEntries)
        assertEquals(listOf(gmail, bank), state.copy(filter = AppFilter.CUSTOM_RULE).visibleEntries)
        assertEquals(listOf(wechat), state.copy(filter = AppFilter.PROGRESS_OR_REPLY).visibleEntries)
        assertEquals(listOf(gmail), state.copy(filter = AppFilter.EMAIL).visibleEntries)
        assertEquals(listOf(gmail), state.copy(searchQuery = "android.gm").visibleEntries)
        assertEquals(
            listOf(bank),
            state.copy(filter = AppFilter.ENABLED, searchQuery = "银行").visibleEntries,
        )
    }

    @Test
    fun historySortModeAndRuleSubscriptionBusyWorkCleanly() {
        assertEquals(HistorySortMode.TIME, HistorySortMode.fromStorageValue("time"))
        assertEquals(HistorySortMode.APP_COUNT, HistorySortMode.fromStorageValue("app_count"))
        assertEquals(HistorySortMode.TIME, HistorySortMode.fromStorageValue("unknown"))

        assertFalse(RuleSubscriptionUiState().isBusy)
        assertTrue(RuleSubscriptionUiState(checkInFlight = true).isBusy)
        assertTrue(RuleSubscriptionUiState(installInFlight = true).isBusy)
        assertTrue(RuleSubscriptionUiState(restoreInFlight = true).isBusy)
    }
}
