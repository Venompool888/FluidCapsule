package io.github.venompool888.fluidcapsule.ui.state

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.os.SystemClock
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.venompool888.fluidcapsule.core.CapsuleAction
import io.github.venompool888.fluidcapsule.core.CapsuleEvent
import io.github.venompool888.fluidcapsule.core.CapsuleKind
import io.github.venompool888.fluidcapsule.core.CapsulePrivacy
import io.github.venompool888.fluidcapsule.diagnostics.DiagnosticsStore
import io.github.venompool888.fluidcapsule.history.HistoryTransferAction
import io.github.venompool888.fluidcapsule.history.HistoryTransferState
import io.github.venompool888.fluidcapsule.history.NotificationHistoryAppGroup
import io.github.venompool888.fluidcapsule.history.NotificationHistoryEntry
import io.github.venompool888.fluidcapsule.history.NotificationHistoryStore
import io.github.venompool888.fluidcapsule.history.NotificationHistoryTransfer
import io.github.venompool888.fluidcapsule.integration.NotificationStageClassifier
import io.github.venompool888.fluidcapsule.integration.SpeedtestTextParser
import io.github.venompool888.fluidcapsule.keepalive.KeepAliveService
import io.github.venompool888.fluidcapsule.notification.TencentMessageAccumulator
import io.github.venompool888.fluidcapsule.parser.OtpParseResult
import io.github.venompool888.fluidcapsule.parser.OtpParser
import io.github.venompool888.fluidcapsule.publisher.PublisherRouter
import io.github.venompool888.fluidcapsule.rules.RuleCheckResult
import io.github.venompool888.fluidcapsule.rules.RuleInstallResult
import io.github.venompool888.fluidcapsule.rules.RuleRuntime
import io.github.venompool888.fluidcapsule.rules.RuleSubscriptionPrefs
import io.github.venompool888.fluidcapsule.rules.RuleUpdateGateway
import io.github.venompool888.fluidcapsule.rules.RuleUpdateGatewayProvider
import io.github.venompool888.fluidcapsule.rules.VerifiedManifest
import io.github.venompool888.fluidcapsule.settings.AppRule
import io.github.venompool888.fluidcapsule.settings.AppRuleStore
import io.github.venompool888.fluidcapsule.settings.ApplicationListCache
import io.github.venompool888.fluidcapsule.settings.CachedApplicationEntry
import io.github.venompool888.fluidcapsule.settings.EmailAppClassifier
import io.github.venompool888.fluidcapsule.settings.HistoryRetentionPolicy
import io.github.venompool888.fluidcapsule.settings.NotificationWhitelist
import io.github.venompool888.fluidcapsule.settings.ThemeMode
import io.github.venompool888.fluidcapsule.settings.UserSettings
import io.github.venompool888.fluidcapsule.sync.SyncConfig
import io.github.venompool888.fluidcapsule.sync.SyncRealtimeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class MainTab(val label: String) {
    OVERVIEW("概览"),
    APPS_AND_RULES("应用与规则"),
    HISTORY("历史"),
    SETTINGS("设置"),
}

enum class AppFilter(val label: String) {
    ALL("全部"),
    ENABLED("已启用"),
    CUSTOM_RULE("已设专属规则"),
    PROGRESS_OR_REPLY("进度与回复"),
    EMAIL("邮箱"),
}

enum class HistorySortMode(val storageValue: String, val label: String) {
    TIME("time", "按时间"),
    APP_COUNT("app_count", "按软件"),
    ;

    companion object {
        fun fromStorageValue(value: String): HistorySortMode =
            entries.firstOrNull { it.storageValue == value } ?: TIME
    }
}

enum class HistoryDecisionFilter(val label: String, val dbDecision: String?) {
    ALL("全部", null),
    PUBLISHED("已弹出胶囊", "PUBLISHED"),
    HAS_OTP("含验证码", null),
    FILTERED("已过滤", "FILTERED"),
    SKIPPED("仅记录", "SKIPPED"),
}

data class AppEntryUi(
    val packageName: String,
    val label: String,
    val isEmailApp: Boolean,
    val isDefaultSms: Boolean,
    val isEnabled: Boolean,
    val otpOnly: Boolean,
    val rule: AppRule,
    val capabilityTags: List<String>,
    val supportsProgressOrReply: Boolean,
) {
    val hasCustomRule: Boolean
        get() = otpOnly || rule != AppRule()
}

data class CloudSmsRuleSummary(
    val packageName: String,
    val rule: AppRule,
    val otpOnly: Boolean,
)

data class AppsUiState(
    val entries: List<AppEntryUi> = emptyList(),
    val searchQuery: String = "",
    val filter: AppFilter = AppFilter.ALL,
    val selectedCount: Int = 0,
    val defaultSmsPackage: String? = null,
    val defaultSmsLabel: String? = null,
    val cloudSmsRule: CloudSmsRuleSummary? = null,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val refreshWarning: String? = null,
) {
    val visibleEntries: List<AppEntryUi>
        get() {
            val query = searchQuery.trim().lowercase()
            return entries.filter { entry ->
                val matchesFilter = when (filter) {
                    AppFilter.ALL -> true
                    AppFilter.ENABLED -> entry.isEnabled
                    AppFilter.CUSTOM_RULE -> entry.hasCustomRule
                    AppFilter.PROGRESS_OR_REPLY -> entry.supportsProgressOrReply
                    AppFilter.EMAIL -> entry.isEmailApp
                }
                val matchesQuery = query.isEmpty() ||
                    entry.label.lowercase().contains(query) ||
                    entry.packageName.lowercase().contains(query)
                matchesFilter && matchesQuery
            }
        }
}

data class RuleSubscriptionUiState(
    val subscriptionEnabled: Boolean = true,
    val installedVersion: Int = 1,
    val isBuiltInSource: Boolean = true,
    val statusText: String = "上次检查：尚未检查",
    val checkInFlight: Boolean = false,
    val installInFlight: Boolean = false,
    val restoreInFlight: Boolean = false,
    val availableManifest: VerifiedManifest? = null,
    val pendingUpdateDialog: VerifiedManifest? = null,
) {
    val isBusy: Boolean
        get() = checkInFlight || installInFlight || restoreInFlight
}

data class SyncSummaryState(
    val isConnected: Boolean = false,
    val isLive: Boolean = false,
    val reconnecting: Boolean = false,
    val account: String = "",
    val server: String = "",
    val lastError: String = "",
    val summaryText: String = "未连接 · 接收胶囊短信从其他手机推送的短信",
)

data class HistoryUiState(
    val recordingEnabled: Boolean = false,
    val retentionPolicy: HistoryRetentionPolicy = HistoryRetentionPolicy.DEFAULT,
    val sortMode: HistorySortMode = HistorySortMode.TIME,
    val searchQuery: String = "",
    val decisionFilter: HistoryDecisionFilter = HistoryDecisionFilter.ALL,
    val privacyBlurEnabled: Boolean = false,
    val totalCount: Long = 0L,
    val timelineEntries: List<NotificationHistoryEntry> = emptyList(),
    val appGroups: List<NotificationHistoryAppGroup> = emptyList(),
    val expandedPackages: Set<String> = emptySet(),
    val expandedGroupEntries: Map<String, List<NotificationHistoryEntry>> = emptyMap(),
    val entryOtps: Map<Long, String> = emptyMap(),
    val packageRecordHistory: Map<String, Boolean> = emptyMap(),
    val isLoading: Boolean = false,
)

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val capsuleDurationMinutes: Int = 5,
    val showOtpDirectly: Boolean = true,
    val maskOtpClipboardPreview: Boolean = false,
    val showWhitelistContent: Boolean = true,
    val keepAliveEnabled: Boolean = false,
)

class AppsCatalogController(
    private val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    private val _state = MutableStateFlow(AppsUiState())
    val state: StateFlow<AppsUiState> = _state.asStateFlow()

    private var cachedOrder: List<CachedApplicationEntry> = emptyList()
    private var scanInFlight = false
    private val createdAtMs = SystemClock.elapsedRealtime()

    init {
        loadInitialFromCache()
    }

    fun loadInitialFromCache() {
        val cached = ApplicationListCache.load(context)
        if (cached.isNotEmpty()) {
            cachedOrder = sortEntriesInitial(cached, NotificationWhitelist.packages(context))
            rebuildEntries(isInitialLoading = false)
        }
        refreshAppsFromPackageManager(reSortAfterScan = cached.isEmpty())
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setFilter(filter: AppFilter) {
        _state.update { it.copy(filter = filter) }
    }

    /** Toggles whitelist membership in place so the list never jumps out from under the user's finger. */
    fun setAppEnabled(packageName: String, enabled: Boolean) {
        NotificationWhitelist.setEnabled(context, packageName, enabled)
        val updatedCount = NotificationWhitelist.packages(context).size
        _state.update { current ->
            current.copy(
                selectedCount = updatedCount,
                entries = current.entries.map { entry ->
                    if (entry.packageName == packageName) entry.copy(isEnabled = enabled) else entry
                },
            )
        }
    }

    /** Re-reads whitelist/rules on resume without disrupting current scroll order. */
    fun refreshRulesInPlace() {
        if (cachedOrder.isEmpty()) {
            val cached = ApplicationListCache.load(context)
            if (cached.isNotEmpty()) {
                cachedOrder = sortEntriesInitial(cached, NotificationWhitelist.packages(context))
            }
        }
        if (cachedOrder.isNotEmpty()) {
            rebuildEntries(isInitialLoading = false)
        } else {
            refreshCloudSmsOnly()
        }
    }

    /** Explicitly scans installed applications and sorts enabled apps to the top. */
    fun refreshAndSortApps() {
        refreshAppsFromPackageManager(reSortAfterScan = true)
    }

    private fun refreshAppsFromPackageManager(reSortAfterScan: Boolean) {
        if (scanInFlight) return
        scanInFlight = true
        _state.update { it.copy(isRefreshing = true, refreshWarning = null) }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { scanInstalledApplications(context) }
            }
            val elapsed = SystemClock.elapsedRealtime() - createdAtMs
            val remaining = (MIN_REFRESH_INDICATOR_MS - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) delay(remaining)
            scanInFlight = false
            result.onSuccess { scanned ->
                ApplicationListCache.save(context, scanned)
                val selected = NotificationWhitelist.packages(context)
                cachedOrder = if (reSortAfterScan || cachedOrder.isEmpty()) {
                    sortEntriesInitial(scanned, selected)
                } else {
                    val existingRank = cachedOrder.withIndex().associate { it.value.packageName to it.index }
                    scanned.sortedWith(
                        compareBy<CachedApplicationEntry> { existingRank[it.packageName] ?: Int.MAX_VALUE }
                            .thenByDescending { it.packageName in selected }
                            .thenBy { it.label.lowercase() },
                    )
                }
                rebuildEntries(isInitialLoading = false, clearWarning = true)
            }.onFailure {
                _state.update { current ->
                    current.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        refreshWarning = if (current.entries.isEmpty()) {
                            "获取应用列表失败，请重新打开"
                        } else {
                            "获取失败，正在显示上次的应用列表"
                        },
                    )
                }
            }
        }
    }

    private fun refreshCloudSmsOnly() {
        val pkg = context.packageName
        val defaultSmsPkg = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        val defaultSmsLabel = defaultSmsPkg?.let { resolveAppLabel(context, it) }
        _state.update {
            it.copy(
                selectedCount = NotificationWhitelist.packages(context).size,
                defaultSmsPackage = defaultSmsPkg,
                defaultSmsLabel = defaultSmsLabel,
                cloudSmsRule = CloudSmsRuleSummary(
                    packageName = pkg,
                    rule = AppRuleStore.get(context, pkg),
                    otpOnly = NotificationWhitelist.isOtpOnly(context, pkg),
                ),
            )
        }
    }

    private fun rebuildEntries(isInitialLoading: Boolean, clearWarning: Boolean = false) {
        val selected = NotificationWhitelist.packages(context)
        val otpOnlyPackages = NotificationWhitelist.otpOnlyPackages(context)
        val defaultSmsPkg = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        val defaultSmsLabel = defaultSmsPkg?.let { resolveAppLabel(context, it) }
        val builtEntries = cachedOrder.map { cached ->
            val rule = AppRuleStore.get(context, cached.packageName)
            val isDefaultSms = defaultSmsPkg != null && cached.packageName == defaultSmsPkg
            val capabilities = buildCapabilities(cached.packageName, cached.isEmailApp, isDefaultSms)
            AppEntryUi(
                packageName = cached.packageName,
                label = cached.label,
                isEmailApp = cached.isEmailApp,
                isDefaultSms = isDefaultSms,
                isEnabled = cached.packageName in selected,
                otpOnly = cached.packageName in otpOnlyPackages,
                rule = rule,
                capabilityTags = capabilities,
                supportsProgressOrReply = cached.packageName in PROGRESS_OR_REPLY_PACKAGES,
            )
        }
        val selfPkg = context.packageName
        _state.update { current ->
            current.copy(
                entries = builtEntries,
                selectedCount = selected.size,
                defaultSmsPackage = defaultSmsPkg,
                defaultSmsLabel = defaultSmsLabel,
                cloudSmsRule = CloudSmsRuleSummary(
                    packageName = selfPkg,
                    rule = AppRuleStore.get(context, selfPkg),
                    otpOnly = NotificationWhitelist.isOtpOnly(context, selfPkg),
                ),
                isInitialLoading = isInitialLoading,
                isRefreshing = scanInFlight,
                refreshWarning = if (clearWarning) null else current.refreshWarning,
            )
        }
    }

    private fun sortEntriesInitial(
        entries: List<CachedApplicationEntry>,
        selected: Set<String>,
    ): List<CachedApplicationEntry> = entries.sortedWith(
        compareByDescending<CachedApplicationEntry> { it.packageName in selected }
            .thenBy { it.label.lowercase() },
    )

    companion object {
        private const val MIN_REFRESH_INDICATOR_MS = 600L
        private val PROGRESS_OR_REPLY_PACKAGES = setOf(
            NotificationStageClassifier.MEITUAN_PACKAGE,
            NotificationStageClassifier.LOCALSEND_PACKAGE,
            SpeedtestTextParser.SPEEDTEST_PACKAGE,
            TencentMessageAccumulator.WECHAT_PACKAGE,
            TencentMessageAccumulator.QQ_PACKAGE,
        )

        private fun buildCapabilities(
            packageName: String,
            isEmailApp: Boolean,
            isDefaultSms: Boolean,
        ): List<String> = buildList {
            if (isDefaultSms) add("默认短信")
            if (isEmailApp) add("邮箱")
            when (packageName) {
                NotificationStageClassifier.MEITUAN_PACKAGE,
                NotificationStageClassifier.LOCALSEND_PACKAGE,
                -> add("专属进度适配")
                SpeedtestTextParser.SPEEDTEST_PACKAGE -> add("单行测速适配")
                TencentMessageAccumulator.WECHAT_PACKAGE,
                TencentMessageAccumulator.QQ_PACKAGE,
                -> add("多条消息重建与快捷回复")
            }
        }

        private fun resolveAppLabel(context: Context, packageName: String): String = runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)

        private fun scanInstalledApplications(context: Context): List<CachedApplicationEntry> {
            val pm = context.packageManager
            val selected = NotificationWhitelist.packages(context)
            val defaultSms = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
            val mailtoIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:test@example.com"))
            val mailtoHandlers = if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(mailtoIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mailtoIntent, 0)
            }.asSequence().map { it.activityInfo.packageName }.toHashSet()

            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val launchablePackages = if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, 0)
            }.asSequence().map { it.activityInfo.packageName }.toHashSet()

            val installed = if (Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            return installed.asSequence()
                .filter { it.packageName != context.packageName }
                .filter { app ->
                    app.packageName in launchablePackages ||
                        app.packageName == defaultSms ||
                        app.packageName in selected ||
                        app.flags and ApplicationInfo.FLAG_SYSTEM == 0
                }
                .map { app ->
                    CachedApplicationEntry(
                        packageName = app.packageName,
                        label = pm.getApplicationLabel(app).toString(),
                        isEmailApp = EmailAppClassifier.isEmailApp(app.packageName, mailtoHandlers),
                    )
                }
                .distinctBy { it.packageName }
                .toList()
        }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application get() = getApplication()
    private val rulesExecutor = Executors.newSingleThreadExecutor()
    /** History deletes wait their turn here instead of racing the listener's writes on the main thread. */
    private val historyMutationExecutor = Executors.newSingleThreadExecutor()
    private val rulePrefs = RuleSubscriptionPrefs(app)
    private val ruleGateway: RuleUpdateGateway = RuleUpdateGatewayProvider.create(app)
    private var promptedRuleVersion = 0
    private var savedStateRestored = false
    private val historyQuerySeq = AtomicLong(0L)
    private val permissionsFocusSeq = AtomicInteger(0)

    val historyTransfer = NotificationHistoryTransfer(app)
    val appsCatalog = AppsCatalogController(app, viewModelScope)

    private val _currentTab = MutableStateFlow(MainTab.OVERVIEW)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    private val _settingsPermissionsFocusRequest = MutableStateFlow(0)
    val settingsPermissionsFocusRequest: StateFlow<Int> = _settingsPermissionsFocusRequest.asStateFlow()

    private val _historyTransferState = MutableStateFlow<HistoryTransferState>(HistoryTransferState.Idle)
    val historyTransferState: StateFlow<HistoryTransferState> = _historyTransferState.asStateFlow()

    private val _healthState = MutableStateFlow(SystemHealthChecker.check(app))
    val healthState: StateFlow<SystemHealthState> = _healthState.asStateFlow()

    private val _rawDiagnosticsText = MutableStateFlow(DiagnosticsStore.snapshot(app))
    val rawDiagnosticsText: StateFlow<String> = _rawDiagnosticsText.asStateFlow()

    private val _settingsState = MutableStateFlow(readSettingsState())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    private val _syncSummary = MutableStateFlow(readSyncSummary())
    val syncSummary: StateFlow<SyncSummaryState> = _syncSummary.asStateFlow()

    private val _ruleState = MutableStateFlow(
        RuleSubscriptionUiState(
            subscriptionEnabled = rulePrefs.subscriptionEnabled,
            installedVersion = RuleRuntime.current(app).version,
            isBuiltInSource = RuleRuntime.current(app).version <= 1,
        ),
    )
    val ruleState: StateFlow<RuleSubscriptionUiState> = _ruleState.asStateFlow()

    private val _historyState = MutableStateFlow(
        HistoryUiState(
            recordingEnabled = UserSettings.notificationHistoryEnabled(app),
            retentionPolicy = UserSettings.notificationHistoryRetentionPolicy(app),
            sortMode = HistorySortMode.fromStorageValue(UserSettings.notificationHistorySortMode(app)),
        ),
    )
    val historyState: StateFlow<HistoryUiState> = _historyState.asStateFlow()

    init {
        historyTransfer.attach { state ->
            _historyTransferState.value = state
            if (state is HistoryTransferState.Finished) {
                _historyState.update {
                    it.copy(retentionPolicy = UserSettings.notificationHistoryRetentionPolicy(app))
                }
                if (state.success) {
                    refreshHistory()
                }
            }
        }
        refreshHistory()
    }

    override fun onCleared() {
        historyTransfer.detach()
        historyTransfer.close()
        rulesExecutor.shutdownNow()
        historyMutationExecutor.shutdown()
        super.onCleared()
    }

    fun selectTab(tab: MainTab) {
        _settingsPermissionsFocusRequest.value = 0
        if (_currentTab.value == tab) return
        _currentTab.value = tab
        when (tab) {
            MainTab.OVERVIEW -> {
                refreshHealth()
                refreshSyncSummary()
                refreshHistoryCountOnly()
            }
            MainTab.APPS_AND_RULES -> {
                appsCatalog.refreshRulesInPlace()
            }
            MainTab.HISTORY -> refreshHistory()
            MainTab.SETTINGS -> {
                refreshHealth()
                refreshSyncSummary()
            }
        }
    }

    fun navigateToSettingsPermissions() {
        selectTab(MainTab.SETTINGS)
        _settingsPermissionsFocusRequest.value = permissionsFocusSeq.incrementAndGet()
    }

    fun restoreSavedState(savedInstanceState: Bundle?) {
        if (savedStateRestored || savedInstanceState == null) return
        savedStateRestored = true
        savedInstanceState.getString(KEY_CURRENT_PAGE)?.let { savedTab ->
            MainTab.entries.firstOrNull { it.name == savedTab }?.let { _currentTab.value = it }
        }
        if (historyTransfer.state == HistoryTransferState.Idle) {
            val picker = savedInstanceState.getString(KEY_HISTORY_PICKER)
            if (picker != null) {
                HistoryTransferAction.entries.firstOrNull { it.name == picker }
                    ?.let(historyTransfer::beginPicking)
            } else if (savedInstanceState.getBoolean(KEY_HISTORY_BUSY, false)) {
                historyTransfer.interrupted()
            }
        }
    }

    fun saveState(outState: Bundle) {
        outState.putString(KEY_CURRENT_PAGE, _currentTab.value.name)
        outState.putString(
            KEY_HISTORY_PICKER,
            (historyTransfer.state as? HistoryTransferState.Picking)?.action?.name,
        )
        outState.putBoolean(KEY_HISTORY_BUSY, historyTransfer.state != HistoryTransferState.Idle)
    }

    fun refreshOnResume() {
        _settingsState.value = readSettingsState()
        refreshHealth()
        refreshSyncSummary()
        appsCatalog.refreshRulesInPlace()
        refreshHistory()
        val currentVersion = RuleRuntime.current(app).version
        _ruleState.update {
            it.copy(
                subscriptionEnabled = rulePrefs.subscriptionEnabled,
                installedVersion = currentVersion,
            )
        }
        if (rulePrefs.subscriptionEnabled) {
            checkRuleUpdates(force = false)
        }
    }

    fun refreshHealth() {
        _healthState.value = SystemHealthChecker.check(app)
        _rawDiagnosticsText.value = DiagnosticsStore.snapshot(app)
        _settingsState.value = readSettingsState()
    }

    fun refreshSyncSummary() {
        _syncSummary.value = readSyncSummary()
    }

    fun setThemeMode(mode: ThemeMode) {
        if (UserSettings.themeMode(app) == mode) return
        UserSettings.setThemeMode(app, mode)
        _settingsState.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        UserSettings.setDynamicColorEnabled(app, enabled)
        _settingsState.update { it.copy(dynamicColorEnabled = enabled) }
    }

    fun setCapsuleDurationMinutes(minutes: Int) {
        UserSettings.setCapsuleDisplayDurationMinutes(app, minutes)
        _settingsState.update {
            it.copy(capsuleDurationMinutes = UserSettings.capsuleDisplayDurationMinutes(app))
        }
    }

    fun setShowOtpDirectly(enabled: Boolean) {
        UserSettings.setShowOtpDirectly(app, enabled)
        _settingsState.update { it.copy(showOtpDirectly = enabled) }
    }

    fun setMaskOtpClipboardPreview(enabled: Boolean) {
        UserSettings.setMaskOtpClipboardPreview(app, enabled)
        _settingsState.update { it.copy(maskOtpClipboardPreview = enabled) }
    }

    fun setShowWhitelistContent(enabled: Boolean) {
        UserSettings.setShowWhitelistContent(app, enabled)
        _settingsState.update { it.copy(showWhitelistContent = enabled) }
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        UserSettings.setKeepAliveEnabled(app, enabled)
        val intent = Intent(app, KeepAliveService::class.java)
        if (enabled) {
            app.startForegroundService(intent)
        } else {
            app.stopService(intent)
        }
        _settingsState.update { it.copy(keepAliveEnabled = enabled) }
        refreshHealth()
    }

    fun publishTestOtp(): String {
        val now = System.currentTimeMillis()
        val showDirectly = UserSettings.showOtpDirectly(app)
        PublisherRouter.publish(
            app,
            CapsuleEvent(
                sourcePackage = app.packageName,
                eventId = UUID.randomUUID().toString(),
                kind = CapsuleKind.OTP,
                title = "测试验证码",
                shortText = "482913",
                body = "5 分钟内有效 · 点击复制",
                action = CapsuleAction.CopySensitiveText("482913"),
                privacy = if (showDirectly) CapsulePrivacy.SHOW_FULL else CapsulePrivacy.HIDE_SENSITIVE,
                createdAtMillis = now,
                expiresAtMillis = now + 5 * 60_000L,
                dedupeKey = "test:482913",
            ),
        )
        refreshHealth()
        return "已发送测试验证码（482913），请看状态栏原生样式"
    }

    fun copyTextToClipboard(label: String, value: String, sensitive: Boolean): String {
        val masked = sensitive && UserSettings.maskOtpClipboardPreview(app)
        val clip = ClipData.newPlainText(label, value)
        if (masked && Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        app.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        return when {
            sensitive && masked -> "已复制验证码 $value（已隐藏系统剪贴板预览）"
            sensitive -> "已复制验证码 $value"
            else -> "已复制内容"
        }
    }

    // ---- Rule subscription ----

    fun setRuleSubscriptionEnabled(enabled: Boolean) {
        rulePrefs.subscriptionEnabled = enabled
        _ruleState.update {
            it.copy(
                subscriptionEnabled = enabled,
                statusText = if (enabled) "已开启规则订阅" else "已关闭自动检查；仍可手动检查",
            )
        }
        if (enabled) checkRuleUpdates(force = false)
    }

    fun checkRuleUpdates(force: Boolean) {
        val current = _ruleState.value
        if (current.checkInFlight || (!force && !rulePrefs.subscriptionEnabled)) return
        _ruleState.update { it.copy(checkInFlight = true, statusText = "正在检查规则…") }
        rulesExecutor.execute {
            val result = runCatching { ruleGateway.check(force) }.getOrDefault(RuleCheckResult.Failed)
            viewModelScope.launch(Dispatchers.Main.immediate) {
                when (result) {
                    is RuleCheckResult.Available -> {
                        val version = result.manifest.manifest.version
                        val showDialog = result.shouldPrompt &&
                            rulePrefs.subscriptionEnabled &&
                            !force &&
                            promptedRuleVersion != version &&
                            _ruleState.value.pendingUpdateDialog == null
                        if (showDialog) {
                            promptedRuleVersion = version
                            ruleGateway.dismiss(version)
                        }
                        _ruleState.update { state ->
                            state.copy(
                                checkInFlight = false,
                                availableManifest = result.manifest,
                                statusText = "发现规则版本 $version",
                                pendingUpdateDialog = if (showDialog) result.manifest else state.pendingUpdateDialog,
                            )
                        }
                    }
                    is RuleCheckResult.UpToDate -> {
                        _ruleState.update {
                            it.copy(
                                checkInFlight = false,
                                availableManifest = null,
                                installedVersion = result.version,
                                isBuiltInSource = result.version <= 1 && it.isBuiltInSource,
                                statusText = "规则已是最新版本",
                            )
                        }
                    }
                    is RuleCheckResult.Incompatible -> {
                        _ruleState.update {
                            it.copy(
                                checkInFlight = false,
                                statusText = "规则 ${result.version} 需要更新的应用版本",
                            )
                        }
                    }
                    RuleCheckResult.Offline -> {
                        _ruleState.update { it.copy(checkInFlight = false, statusText = "离线，稍后可重试") }
                    }
                    RuleCheckResult.Failed -> {
                        _ruleState.update { it.copy(checkInFlight = false, statusText = "检查失败，请稍后重试") }
                    }
                    RuleCheckResult.Disabled -> {
                        _ruleState.update { it.copy(checkInFlight = false, statusText = "已关闭自动检查") }
                    }
                }
            }
        }
    }

    fun installRuleUpdate(manifest: VerifiedManifest) {
        if (_ruleState.value.installInFlight) return
        _ruleState.update {
            it.copy(
                installInFlight = true,
                pendingUpdateDialog = null,
                statusText = "正在验证并安装规则…",
            )
        }
        rulesExecutor.execute {
            val result = runCatching { ruleGateway.install(manifest) }.getOrDefault(RuleInstallResult.Failed)
            viewModelScope.launch(Dispatchers.Main.immediate) {
                _ruleState.update { current ->
                    when (result) {
                        is RuleInstallResult.Installed -> current.copy(
                            installInFlight = false,
                            availableManifest = null,
                            installedVersion = result.version,
                            isBuiltInSource = false,
                            statusText = "规则已更新",
                        )
                        RuleInstallResult.Incompatible -> current.copy(
                            installInFlight = false,
                            statusText = "当前应用版本无法安装此规则",
                        )
                        RuleInstallResult.Failed -> current.copy(
                            installInFlight = false,
                            statusText = "更新失败，原有规则仍可使用",
                        )
                    }
                }
            }
        }
    }

    fun installPendingRuleUpdate() {
        val manifest = _ruleState.value.pendingUpdateDialog ?: return
        installRuleUpdate(manifest)
    }

    fun dismissRuleUpdateDialog() {
        _ruleState.update { it.copy(pendingUpdateDialog = null) }
    }

    fun restoreBuiltInRules() {
        if (_ruleState.value.restoreInFlight) return
        _ruleState.update { it.copy(restoreInFlight = true, statusText = "正在恢复内置规则…") }
        rulesExecutor.execute {
            val success = runCatching { ruleGateway.restoreBuiltIn() }.isSuccess
            viewModelScope.launch(Dispatchers.Main.immediate) {
                _ruleState.update { current ->
                    if (success) {
                        current.copy(
                            restoreInFlight = false,
                            availableManifest = null,
                            installedVersion = 1,
                            isBuiltInSource = true,
                            statusText = "已恢复内置规则",
                        )
                    } else {
                        current.copy(
                            restoreInFlight = false,
                            statusText = "恢复失败，请重试",
                        )
                    }
                }
            }
        }
    }

    // ---- History ----

    fun setNotificationHistoryEnabled(enabled: Boolean): String {
        UserSettings.setNotificationHistoryEnabled(app, enabled)
        _historyState.update { it.copy(recordingEnabled = enabled) }
        refreshHistory()
        return if (enabled) "已开始记录新通知" else "已停止记录，原有历史已保留"
    }

    fun setRetentionPolicy(policy: HistoryRetentionPolicy, onResult: (String) -> Unit) {
        mutateHistory(
            work = { NotificationHistoryStore.setRetentionPolicy(app, policy) },
            onSuccess = {
                _historyState.update { it.copy(retentionPolicy = policy) }
                refreshHistory()
                "历史将${if (policy.unit == io.github.venompool888.fluidcapsule.settings.HistoryRetentionUnit.FOREVER) "永久保存" else "保留 ${policy.chineseLabel()}"}"
            },
            onResult = onResult,
        )
    }

    fun setHistorySortMode(mode: HistorySortMode) {
        if (_historyState.value.sortMode == mode) return
        UserSettings.setNotificationHistorySortMode(app, mode.storageValue)
        _historyState.update {
            it.copy(
                sortMode = mode,
                expandedPackages = emptySet(),
                expandedGroupEntries = emptyMap(),
            )
        }
        refreshHistory()
    }

    fun setHistorySearchQuery(query: String) {
        if (_historyState.value.searchQuery == query) return
        _historyState.update { it.copy(searchQuery = query) }
        refreshHistory()
    }

    fun setHistoryDecisionFilter(filter: HistoryDecisionFilter) {
        if (_historyState.value.decisionFilter == filter) return
        _historyState.update { it.copy(decisionFilter = filter) }
        refreshHistory()
    }

    fun togglePrivacyBlur() {
        _historyState.update { it.copy(privacyBlurEnabled = !it.privacyBlurEnabled) }
    }

    fun toggleAppGroupExpanded(sourcePackage: String) {
        val current = _historyState.value
        if (sourcePackage in current.expandedPackages) {
            _historyState.update {
                it.copy(expandedPackages = it.expandedPackages - sourcePackage)
            }
            return
        }
        _historyState.update {
            it.copy(expandedPackages = it.expandedPackages + sourcePackage)
        }
        loadExpandedPackageEntries(sourcePackage)
    }

    fun setPackageRecordHistory(sourcePackage: String, recordHistory: Boolean): String {
        val existing = AppRuleStore.get(app, sourcePackage)
        AppRuleStore.set(app, sourcePackage, existing.copy(recordHistory = recordHistory))
        _historyState.update {
            it.copy(packageRecordHistory = it.packageRecordHistory + (sourcePackage to recordHistory))
        }
        appsCatalog.refreshRulesInPlace()
        return if (recordHistory) "已恢复记录该应用通知" else "已停止记录该应用新通知"
    }

    fun deleteHistoryEntry(id: Long, onResult: (String) -> Unit) {
        mutateHistory(
            work = { NotificationHistoryStore.deleteEntry(app, id) },
            onSuccess = {
                refreshHistory()
                "已删除该条通知记录"
            },
            onResult = onResult,
        )
    }

    fun deleteHistoryPackage(sourcePackage: String, onResult: (String) -> Unit) {
        mutateHistory(
            work = { NotificationHistoryStore.deletePackage(app, sourcePackage) },
            onSuccess = { deleted ->
                _historyState.update {
                    it.copy(
                        expandedPackages = it.expandedPackages - sourcePackage,
                        expandedGroupEntries = it.expandedGroupEntries - sourcePackage,
                    )
                }
                refreshHistory()
                "已删除 $deleted 条历史"
            },
            onResult = onResult,
        )
    }

    fun clearAllHistory(onResult: (String) -> Unit) {
        mutateHistory(
            work = { NotificationHistoryStore.clear(app) },
            onSuccess = { deleted ->
                _historyState.update {
                    it.copy(
                        expandedPackages = emptySet(),
                        expandedGroupEntries = emptyMap(),
                    )
                }
                refreshHistory()
                "已删除 $deleted 条历史"
            },
            onResult = onResult,
        )
    }

    private fun <T> mutateHistory(
        work: () -> T,
        onSuccess: (T) -> String,
        onResult: (String) -> Unit,
    ) {
        if (historyTransfer.state != HistoryTransferState.Idle) {
            onResult("通知历史正在处理，请稍后重试")
            return
        }
        historyMutationExecutor.execute {
            val result = runCatching(work)
            viewModelScope.launch(Dispatchers.Main.immediate) {
                val message = result.fold(
                    onSuccess = onSuccess,
                    onFailure = { it.message ?: "通知历史操作失败，请稍后重试" },
                )
                onResult(message)
            }
        }
    }

    fun isHistoryMutationBlocked(): Boolean = historyTransfer.state != HistoryTransferState.Idle

    fun refreshHistory() {
        val seq = historyQuerySeq.incrementAndGet()
        val snapshot = _historyState.value
        val query = snapshot.searchQuery.trim()
        val filter = snapshot.decisionFilter
        val sortMode = snapshot.sortMode
        val expanded = snapshot.expandedPackages

        _historyState.update {
            it.copy(
                recordingEnabled = UserSettings.notificationHistoryEnabled(app),
                retentionPolicy = UserSettings.notificationHistoryRetentionPolicy(app),
                isLoading = true,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val rulePack = runCatching { RuleRuntime.current(app) }.getOrDefault(
                io.github.venompool888.fluidcapsule.rules.RulePack.EMPTY,
            )
            val total = NotificationHistoryStore.count(app)
            val otpMap = mutableMapOf<Long, String>()

            var timeline: List<NotificationHistoryEntry> = emptyList()
            var groups: List<NotificationHistoryAppGroup> = emptyList()
            val groupEntries = mutableMapOf<String, List<NotificationHistoryEntry>>()
            val recordMap = mutableMapOf<String, Boolean>()

            if (sortMode == HistorySortMode.TIME) {
                val raw = when {
                    query.isEmpty() && filter == HistoryDecisionFilter.ALL ->
                        NotificationHistoryStore.recent(app, HISTORY_DISPLAY_LIMIT)
                    filter == HistoryDecisionFilter.HAS_OTP ->
                        NotificationHistoryStore.search(app, query, null, 500)
                    else ->
                        NotificationHistoryStore.search(app, query, filter.dbDecision, HISTORY_DISPLAY_LIMIT)
                }
                timeline = if (filter == HistoryDecisionFilter.HAS_OTP) {
                    raw.filter { entry ->
                        val code = extractOtpCode(entry, rulePack)
                        if (code != null) {
                            otpMap[entry.id] = code
                            true
                        } else {
                            false
                        }
                    }.take(HISTORY_DISPLAY_LIMIT)
                } else {
                    raw.also { entries ->
                        entries.forEach { entry ->
                            extractOtpCode(entry, rulePack)?.let { otpMap[entry.id] = it }
                        }
                    }
                }
            } else {
                val allGroups = NotificationHistoryStore.appGroups(app)
                val matchingPackages: Set<String>? = if (query.isNotEmpty() || filter != HistoryDecisionFilter.ALL) {
                    val matchedEntries = if (filter == HistoryDecisionFilter.HAS_OTP) {
                        NotificationHistoryStore.search(app, query, null, 500).filter {
                            extractOtpCode(it, rulePack) != null
                        }
                    } else {
                        NotificationHistoryStore.search(app, query, filter.dbDecision, 500)
                    }
                    val fromEntries = matchedEntries.mapTo(mutableSetOf()) { it.sourcePackage }
                    val fromLabels = if (filter == HistoryDecisionFilter.ALL && query.isNotEmpty()) {
                        allGroups.filter {
                            it.sourceLabel.contains(query, ignoreCase = true) ||
                                it.sourcePackage.contains(query, ignoreCase = true)
                        }.mapTo(mutableSetOf()) { it.sourcePackage }
                    } else {
                        emptySet()
                    }
                    fromEntries + fromLabels
                } else {
                    null
                }
                groups = if (matchingPackages == null) {
                    allGroups
                } else {
                    allGroups.filter { it.sourcePackage in matchingPackages }
                }
                groups.forEach { group ->
                    recordMap[group.sourcePackage] = AppRuleStore.get(app, group.sourcePackage).recordHistory
                }
                expanded.forEach { pkg ->
                    val pkgEntries = loadFilteredPackageEntries(pkg, query, filter, rulePack, otpMap)
                    groupEntries[pkg] = pkgEntries
                }
            }

            withContext(Dispatchers.Main.immediate) {
                if (seq != historyQuerySeq.get()) return@withContext
                _historyState.update { current ->
                    current.copy(
                        totalCount = total,
                        timelineEntries = timeline,
                        appGroups = groups,
                        expandedGroupEntries = groupEntries,
                        entryOtps = otpMap,
                        packageRecordHistory = recordMap,
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun refreshHistoryCountOnly() {
        viewModelScope.launch(Dispatchers.IO) {
            val total = NotificationHistoryStore.count(app)
            val enabled = UserSettings.notificationHistoryEnabled(app)
            withContext(Dispatchers.Main.immediate) {
                _historyState.update { it.copy(totalCount = total, recordingEnabled = enabled) }
            }
        }
    }

    private fun loadExpandedPackageEntries(sourcePackage: String) {
        val snapshot = _historyState.value
        val query = snapshot.searchQuery.trim()
        val filter = snapshot.decisionFilter
        viewModelScope.launch(Dispatchers.IO) {
            val rulePack = runCatching { RuleRuntime.current(app) }.getOrDefault(
                io.github.venompool888.fluidcapsule.rules.RulePack.EMPTY,
            )
            val otpMap = mutableMapOf<Long, String>()
            val entries = loadFilteredPackageEntries(sourcePackage, query, filter, rulePack, otpMap)
            val recordHistory = AppRuleStore.get(app, sourcePackage).recordHistory
            withContext(Dispatchers.Main.immediate) {
                _historyState.update { current ->
                    current.copy(
                        expandedGroupEntries = current.expandedGroupEntries + (sourcePackage to entries),
                        entryOtps = current.entryOtps + otpMap,
                        packageRecordHistory = current.packageRecordHistory + (sourcePackage to recordHistory),
                    )
                }
            }
        }
    }

    private fun loadFilteredPackageEntries(
        sourcePackage: String,
        query: String,
        filter: HistoryDecisionFilter,
        rulePack: io.github.venompool888.fluidcapsule.rules.RulePack,
        otpMap: MutableMap<Long, String>,
    ): List<NotificationHistoryEntry> {
        val entries = NotificationHistoryStore.forPackage(app, sourcePackage, HISTORY_DISPLAY_LIMIT)
        return entries.filter { entry ->
            val matchesQuery = query.isEmpty() ||
                entry.title.contains(query, ignoreCase = true) ||
                entry.primaryText.contains(query, ignoreCase = true) ||
                entry.combinedText.contains(query, ignoreCase = true) ||
                entry.sourceLabel.contains(query, ignoreCase = true) ||
                entry.sourcePackage.contains(query, ignoreCase = true)
            val otp = extractOtpCode(entry, rulePack)
            if (otp != null) otpMap[entry.id] = otp
            val matchesFilter = when (filter) {
                HistoryDecisionFilter.ALL -> true
                HistoryDecisionFilter.HAS_OTP -> otp != null
                else -> entry.decision == filter.dbDecision
            }
            matchesQuery && matchesFilter
        }
    }

    private fun extractOtpCode(
        entry: NotificationHistoryEntry,
        rulePack: io.github.venompool888.fluidcapsule.rules.RulePack,
    ): String? {
        val primary = entry.primaryText.ifBlank { entry.combinedText }
        val result = OtpParser.parse(primary, rulePack)
        if (result is OtpParseResult.Success) return result.code
        if (entry.combinedText.isNotBlank() && entry.combinedText != primary) {
            val fallback = OtpParser.parse(entry.combinedText, rulePack)
            if (fallback is OtpParseResult.Success) return fallback.code
        }
        return null
    }

    private fun readSettingsState(): SettingsUiState = SettingsUiState(
        themeMode = UserSettings.themeMode(app),
        dynamicColorEnabled = UserSettings.dynamicColorEnabled(app),
        capsuleDurationMinutes = UserSettings.capsuleDisplayDurationMinutes(app),
        showOtpDirectly = UserSettings.showOtpDirectly(app),
        maskOtpClipboardPreview = UserSettings.maskOtpClipboardPreview(app),
        showWhitelistContent = UserSettings.showWhitelistContent(app),
        keepAliveEnabled = UserSettings.keepAliveEnabled(app),
    )

    private fun readSyncSummary(): SyncSummaryState {
        val config = SyncConfig(app)
        val connected = config.isConnected
        val live = connected && SyncRealtimeService.connected
        val reconnecting = connected && !live && config.lastError.isNotBlank()
        val summary = when {
            !connected -> "未连接 · 接收胶囊短信从其他手机推送的短信"
            live -> "实时接收已连接"
            reconnecting -> "重连中：${config.lastError}"
            else -> "正在连接实时接收…"
        }
        return SyncSummaryState(
            isConnected = connected,
            isLive = live,
            reconnecting = reconnecting,
            account = config.account,
            server = config.server,
            lastError = config.lastError,
            summaryText = summary,
        )
    }

    companion object {
        const val HISTORY_DISPLAY_LIMIT = 250
        private const val KEY_CURRENT_PAGE = "history.currentPage"
        private const val KEY_HISTORY_PICKER = "history.picker"
        private const val KEY_HISTORY_BUSY = "history.busy"
    }
}
