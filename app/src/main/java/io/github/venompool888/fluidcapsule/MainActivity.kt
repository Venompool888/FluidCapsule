package io.github.venompool888.fluidcapsule

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.venompool888.fluidcapsule.history.HistoryTransferAction
import io.github.venompool888.fluidcapsule.history.HistoryTransferState
import io.github.venompool888.fluidcapsule.history.NotificationHistoryTransfer
import io.github.venompool888.fluidcapsule.rules.VerifiedManifest
import io.github.venompool888.fluidcapsule.sync.SyncRealtimeService
import io.github.venompool888.fluidcapsule.sync.SyncSettingsActivity
import io.github.venompool888.fluidcapsule.ui.screens.AppsAndRulesScreen
import io.github.venompool888.fluidcapsule.ui.screens.HistoryScreen
import io.github.venompool888.fluidcapsule.ui.screens.OverviewScreen
import io.github.venompool888.fluidcapsule.ui.screens.SettingsScreen
import io.github.venompool888.fluidcapsule.ui.state.MainTab
import io.github.venompool888.fluidcapsule.ui.state.MainViewModel
import io.github.venompool888.fluidcapsule.ui.state.PromotionSettingsLaunchResult
import io.github.venompool888.fluidcapsule.ui.state.SystemHealthChecker
import io.github.venompool888.fluidcapsule.ui.theme.FluidCapsuleTheme
import io.github.venompool888.fluidcapsule.ui.theme.configureEdgeToEdgeForTheme
import io.github.venompool888.fluidcapsule.ui.theme.isDarkThemeForContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    internal val viewModel: MainViewModel by viewModels()
    internal val historyTransfer: NotificationHistoryTransfer
        get() = viewModel.historyTransfer

    private var pendingSnackbarMessage: ((String) -> Unit)? = null
    private var pendingEnableKeepAliveAfterPermission = false

    private val exportDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if ((viewModel.historyTransfer.state as? HistoryTransferState.Picking)?.action == HistoryTransferAction.EXPORT) {
            viewModel.historyTransfer.acceptDocument(result.data?.data.takeIf { result.resultCode == RESULT_OK })
        }
    }

    private val importDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if ((viewModel.historyTransfer.state as? HistoryTransferState.Picking)?.action == HistoryTransferAction.IMPORT) {
            viewModel.historyTransfer.acceptDocument(result.data?.data.takeIf { result.resultCode == RESULT_OK })
        }
    }

    private val postNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val shouldStartKeepAlive = pendingEnableKeepAliveAfterPermission
        pendingEnableKeepAliveAfterPermission = false
        viewModel.refreshHealth()
        if (granted) {
            if (shouldStartKeepAlive) {
                viewModel.setKeepAliveEnabled(true)
                showSnackbar("已授予通知权限并开启前台保活服务")
            } else {
                showSnackbar("已授予通知发布权限")
            }
        } else {
            showSnackbar("未授予通知发布权限，部分系统需在通知设置中开启")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "流体胶囊"
        configureEdgeToEdgeForTheme(this, isDarkThemeForContext(this))
        viewModel.restoreSavedState(savedInstanceState)

        setContent {
            val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
            FluidCapsuleTheme(
                themeMode = settingsState.themeMode,
                dynamicColor = settingsState.dynamicColorEnabled,
            ) {
                MainShell()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshOnResume()
        // A force-stop kills the realtime loop and nothing restarts it until boot; opening the
        // app (allowed to start a foreground service) brings it back.
        SyncRealtimeService.update(this)
        // The loop needs a moment to (re)connect after resume; show the settled state.
        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) viewModel.refreshSyncSummary()
        }, 2_000L)
    }

    @Composable
    private fun MainShell() {
        val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
        val healthState by viewModel.healthState.collectAsStateWithLifecycle()
        val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
        val appsState by viewModel.appsCatalog.state.collectAsStateWithLifecycle()
        val ruleState by viewModel.ruleState.collectAsStateWithLifecycle()
        val syncSummary by viewModel.syncSummary.collectAsStateWithLifecycle()
        val sandboxState by viewModel.sandboxState.collectAsStateWithLifecycle()
        val historyState by viewModel.historyState.collectAsStateWithLifecycle()
        val transferState by viewModel.historyTransferState.collectAsStateWithLifecycle()
        val rawDiagnostics by viewModel.rawDiagnosticsText.collectAsStateWithLifecycle()

        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()
        val showMessage: (String) -> Unit = remember(snackbarHostState, coroutineScope) {
            { message ->
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
        pendingSnackbarMessage = showMessage

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        val icon = when (tab) {
                            MainTab.OVERVIEW -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                            MainTab.APPS_AND_RULES -> if (selected) Icons.Filled.Apps else Icons.Outlined.Apps
                            MainTab.HISTORY -> if (selected) Icons.Filled.History else Icons.Outlined.History
                            MainTab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.selectTab(tab) },
                            icon = { Icon(imageVector = icon, contentDescription = null) },
                            label = { Text(tab.label, maxLines = 1) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            val bottomPadding = innerPadding.calculateBottomPadding()
            when (currentTab) {
                MainTab.OVERVIEW -> OverviewScreen(
                    healthState = healthState,
                    settingsState = settingsState,
                    appsState = appsState,
                    historyState = historyState,
                    ruleState = ruleState,
                    syncSummary = syncSummary,
                    sandboxState = sandboxState,
                    bottomContentPadding = bottomPadding,
                    onNavigateTab = viewModel::selectTab,
                    onOpenSyncSettings = {
                        startActivity(Intent(this@MainActivity, SyncSettingsActivity::class.java))
                    },
                    onRequestPostNotificationsOrOpenSettings = ::requestNotificationPermissionOrOpenSettings,
                    onOpenPromotionSettings = { openPromotionSettingsSafely(showMessage) },
                    onSelectSandboxPreset = viewModel::selectSandboxPreset,
                    onToggleSandboxExpanded = viewModel::toggleSandboxExpanded,
                    onCopySandboxOtp = { code ->
                        showMessage(viewModel.copyTextToClipboard("验证码", code, sensitive = true))
                    },
                    onPublishTestOtp = { showMessage(viewModel.publishTestOtp()) },
                    onPublishTestChat = { showMessage(viewModel.publishTestChat()) },
                    onPublishTestProgress = { showMessage(viewModel.publishTestProgress()) },
                )
                MainTab.APPS_AND_RULES -> AppsAndRulesScreen(
                    appsState = appsState,
                    ruleState = ruleState,
                    bottomContentPadding = bottomPadding,
                    onSearchQueryChange = viewModel.appsCatalog::setSearchQuery,
                    onFilterChange = viewModel.appsCatalog::setFilter,
                    onToggleApp = viewModel.appsCatalog::setAppEnabled,
                    onRefreshAndSort = viewModel.appsCatalog::refreshAndSortApps,
                    onToggleRuleSubscription = viewModel::setRuleSubscriptionEnabled,
                    onCheckRuleUpdates = { viewModel.checkRuleUpdates(force = true) },
                    onInstallAvailableRule = {
                        ruleState.availableManifest?.let(viewModel::installRuleUpdate)
                    },
                    onRestoreBuiltInRules = viewModel::restoreBuiltInRules,
                )
                MainTab.HISTORY -> HistoryScreen(
                    historyState = historyState,
                    transferState = transferState,
                    bottomContentPadding = bottomPadding,
                    onSearchQueryChange = viewModel::setHistorySearchQuery,
                    onDecisionFilterChange = viewModel::setHistoryDecisionFilter,
                    onSortModeChange = viewModel::setHistorySortMode,
                    onTogglePrivacyBlur = viewModel::togglePrivacyBlur,
                    onToggleAppGroup = viewModel::toggleAppGroupExpanded,
                    onTogglePackageRecordHistory = { pkg, enabled ->
                        showMessage(viewModel.setPackageRecordHistory(pkg, enabled))
                    },
                    onCopyOtp = { code ->
                        showMessage(viewModel.copyTextToClipboard("验证码", code, sensitive = true))
                    },
                    onCopyBody = { body ->
                        showMessage(viewModel.copyTextToClipboard("通知正文", body, sensitive = false))
                    },
                    onDeleteEntry = { id -> viewModel.deleteHistoryEntry(id, showMessage) },
                    onDeletePackage = { pkg -> viewModel.deleteHistoryPackage(pkg, showMessage) },
                    onClearAllHistory = { viewModel.clearAllHistory(showMessage) },
                    onToggleRecording = { enabled ->
                        showMessage(viewModel.setNotificationHistoryEnabled(enabled))
                    },
                    onChangeRetentionPolicy = { policy ->
                        viewModel.setRetentionPolicy(policy, showMessage)
                    },
                    onExportHistory = { pickHistoryDocument(HistoryTransferAction.EXPORT, showMessage) },
                    onImportHistory = { pickHistoryDocument(HistoryTransferAction.IMPORT, showMessage) },
                    onBlockedActionMessage = showMessage,
                )
                MainTab.SETTINGS -> SettingsScreen(
                    settingsState = settingsState,
                    healthState = healthState,
                    syncSummary = syncSummary,
                    rawDiagnosticsText = rawDiagnostics,
                    bottomContentPadding = bottomPadding,
                    onSelectThemeMode = viewModel::setThemeMode,
                    onToggleDynamicColor = viewModel::setDynamicColorEnabled,
                    onCapsuleDurationChange = { minutes ->
                        viewModel.setCapsuleDurationMinutes(minutes)
                        showMessage("胶囊将显示 $minutes 分钟")
                    },
                    onToggleShowOtpDirectly = { checked ->
                        viewModel.setShowOtpDirectly(checked)
                        showMessage(if (checked) "将直接显示验证码" else "将隐藏验证码，点击仍可复制")
                    },
                    onToggleMaskClipboard = { checked ->
                        viewModel.setMaskOtpClipboardPreview(checked)
                        showMessage(if (checked) "复制提示将打码" else "复制提示将直接显示验证码")
                    },
                    onToggleShowWhitelistContent = { checked ->
                        viewModel.setShowWhitelistContent(checked)
                        showMessage(if (checked) "白名单通知将直接显示正文" else "锁屏将隐藏白名单通知正文")
                    },
                    onOpenSyncSettings = {
                        startActivity(Intent(this@MainActivity, SyncSettingsActivity::class.java))
                    },
                    onToggleKeepAlive = { enabled ->
                        handleKeepAliveToggle(enabled, showMessage)
                    },
                    onRequestPostNotificationsOrOpenSettings = ::requestNotificationPermissionOrOpenSettings,
                    onOpenPromotionSettings = { openPromotionSettingsSafely(showMessage) },
                    onRefreshDiagnostics = {
                        viewModel.refreshHealth()
                        viewModel.refreshSyncSummary()
                        showMessage("诊断状态已刷新")
                    },
                )
            }
        }

        HistoryTransferDialogs(
            transferState = transferState,
            onConfirmImport = { includeExpired -> viewModel.historyTransfer.confirmImport(includeExpired) },
            onCancelPreview = { viewModel.historyTransfer.cancelPreview() },
            onAcknowledgeResult = { viewModel.historyTransfer.acknowledgeResult() },
        )

        ruleState.pendingUpdateDialog?.let { manifest ->
            RuleUpdatePromptDialog(
                manifest = manifest,
                onConfirmInstall = viewModel::installPendingRuleUpdate,
                onDismiss = viewModel::dismissRuleUpdateDialog,
            )
        }
    }

    private fun showSnackbar(message: String) {
        pendingSnackbarMessage?.invoke(message)
    }

    private fun pickHistoryDocument(
        action: HistoryTransferAction,
        showMessage: (String) -> Unit,
    ) {
        if (!viewModel.historyTransfer.beginPicking(action)) return
        val exporting = action == HistoryTransferAction.EXPORT
        val intent = Intent(
            if (exporting) Intent.ACTION_CREATE_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT,
        ).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            if (exporting) {
                val date = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                putExtra(Intent.EXTRA_TITLE, "FluidCapsule-history-$date.json")
            }
        }
        try {
            if (exporting) {
                exportDocumentLauncher.launch(intent)
            } else {
                importDocumentLauncher.launch(intent)
            }
        } catch (_: ActivityNotFoundException) {
            viewModel.historyTransfer.acceptDocument(null)
            showMessage("没有可用的文件选择器")
        } catch (_: SecurityException) {
            viewModel.historyTransfer.acceptDocument(null)
            showMessage("无法打开文件选择器")
        }
    }

    private fun requestNotificationPermissionOrOpenSettings() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.areNotificationsEnabled()) {
            SystemHealthChecker.openAppNotificationSettings(this)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            postNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SystemHealthChecker.openAppNotificationSettings(this)
        }
    }

    private fun handleKeepAliveToggle(enabled: Boolean, showMessage: (String) -> Unit) {
        if (enabled) {
            val manager = getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingEnableKeepAliveAfterPermission = true
                    postNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    showMessage("请先授予通知发布权限以开启前台保活")
                } else {
                    SystemHealthChecker.openAppNotificationSettings(this)
                    showMessage("请先在系统通知设置中允许通知后开启前台保活")
                }
                return
            }
        }
        pendingEnableKeepAliveAfterPermission = false
        viewModel.setKeepAliveEnabled(enabled)
        showMessage(if (enabled) "已开启前台保活服务" else "已关闭前台保活服务")
    }

    private fun openPromotionSettingsSafely(showMessage: (String) -> Unit) {
        when (SystemHealthChecker.openPromotionSettingsSafely(this)) {
            PromotionSettingsLaunchResult.OpenedPromotionSettings -> Unit
            PromotionSettingsLaunchResult.FallbackToAppNotificationSettings -> {
                showMessage("当前系统未提供实时通知提升入口，已打开普通通知设置")
            }
            PromotionSettingsLaunchResult.Unavailable -> {
                showMessage("系统没有可用的通知设置入口")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryTransferDialogs(
    transferState: HistoryTransferState,
    onConfirmImport: (Boolean) -> Unit,
    onCancelPreview: () -> Unit,
    onAcknowledgeResult: () -> Unit,
) {
    when (transferState) {
        is HistoryTransferState.Preview -> {
            val expired = transferState.expired > 0
            val message = buildString {
                append("备份共 ${transferState.metadata.count} 条记录。\n")
                append("当前保留期限：${transferState.policy.chineseLabel()}。\n")
                append("超过当前期限：${transferState.expired} 条。\n")
                append("导入会保留本机历史，并跳过重复记录。")
                if (expired) {
                    append("\n选择导入全部会将本机历史保留期限改为永久。")
                }
            }
            if (expired) {
                AlertDialog(
                    onDismissRequest = onCancelPreview,
                    title = { Text("导入通知历史") },
                    text = { Text(message) },
                    confirmButton = {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextButton(onClick = onCancelPreview) {
                                Text("取消")
                            }
                            TextButton(onClick = { onConfirmImport(false) }) {
                                Text("仅导入期限内记录")
                            }
                            TextButton(onClick = { onConfirmImport(true) }) {
                                Text("改为永久并导入全部")
                            }
                        }
                    },
                )
            } else {
                AlertDialog(
                    onDismissRequest = onCancelPreview,
                    title = { Text("导入通知历史") },
                    text = { Text(message) },
                    confirmButton = {
                        TextButton(onClick = { onConfirmImport(false) }) {
                            Text("导入")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onCancelPreview) {
                            Text("取消")
                        }
                    },
                )
            }
        }
        is HistoryTransferState.Finished -> {
            AlertDialog(
                onDismissRequest = onAcknowledgeResult,
                title = { Text(if (transferState.success) "操作完成" else "操作失败") },
                text = { Text(transferState.message) },
                confirmButton = {
                    TextButton(onClick = onAcknowledgeResult) {
                        Text("确定")
                    }
                },
            )
        }
        else -> Unit
    }
}

@Composable
private fun RuleUpdatePromptDialog(
    manifest: VerifiedManifest,
    onConfirmInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val version = manifest.manifest.version
    val notes = manifest.manifest.notes.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("有新的规则更新") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("官方规则版本 $version 已可用。是否立即更新？")
                if (notes.isNotEmpty()) {
                    Text(
                        text = "更新说明：\n$notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmInstall) {
                Text("立即更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        },
    )
}
