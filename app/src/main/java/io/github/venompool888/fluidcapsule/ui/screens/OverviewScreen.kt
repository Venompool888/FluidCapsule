package io.github.venompool888.fluidcapsule.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.venompool888.fluidcapsule.R
import io.github.venompool888.fluidcapsule.diagnostics.DiagnosticsSnapshot
import io.github.venompool888.fluidcapsule.ui.components.RelativeTimeFormatter
import io.github.venompool888.fluidcapsule.ui.components.SectionLabel
import io.github.venompool888.fluidcapsule.ui.components.StatusBadge
import io.github.venompool888.fluidcapsule.ui.components.StatusTone
import io.github.venompool888.fluidcapsule.ui.components.TonalSectionCard
import io.github.venompool888.fluidcapsule.ui.state.AppsUiState
import io.github.venompool888.fluidcapsule.ui.state.HistoryUiState
import io.github.venompool888.fluidcapsule.ui.state.MainTab
import io.github.venompool888.fluidcapsule.ui.state.RuleSubscriptionUiState
import io.github.venompool888.fluidcapsule.ui.state.SandboxPreset
import io.github.venompool888.fluidcapsule.ui.state.SandboxPreviewState
import io.github.venompool888.fluidcapsule.ui.state.SettingsUiState
import io.github.venompool888.fluidcapsule.ui.state.SyncSummaryState
import io.github.venompool888.fluidcapsule.ui.state.SystemHealthChecker
import io.github.venompool888.fluidcapsule.ui.state.SystemHealthState
import io.github.venompool888.fluidcapsule.ui.theme.FluidTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OverviewScreen(
    healthState: SystemHealthState,
    settingsState: SettingsUiState,
    appsState: AppsUiState,
    historyState: HistoryUiState,
    ruleState: RuleSubscriptionUiState,
    syncSummary: SyncSummaryState,
    sandboxState: SandboxPreviewState,
    bottomContentPadding: Dp,
    onNavigateTab: (MainTab) -> Unit,
    onOpenSyncSettings: () -> Unit,
    onRequestPostNotificationsOrOpenSettings: () -> Unit,
    onOpenPromotionSettings: () -> Unit,
    onSelectSandboxPreset: (SandboxPreset) -> Unit,
    onToggleSandboxExpanded: () -> Unit,
    onCopySandboxOtp: (String) -> Unit,
    onPublishTestOtp: () -> Unit,
    onPublishTestChat: () -> Unit,
    onPublishTestProgress: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scheme = MaterialTheme.colorScheme
    val semantic = FluidTheme.semanticColors

    var readinessExpanded by remember(healthState.isAllCoreReady) {
        mutableStateOf(!healthState.isAllCoreReady)
    }
    LaunchedEffect(healthState.isAllCoreReady) {
        readinessExpanded = !healthState.isAllCoreReady
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        TopAppBar(
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = scheme.surface,
                scrolledContainerColor = scheme.surfaceContainer,
            ),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = scheme.primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_capsule),
                                contentDescription = "流体胶囊图标",
                                tint = scheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "流体胶囊",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                        )
                        StatusBadge(
                            text = if (healthState.isAllCoreReady) {
                                "监听已连接 · 系统就绪"
                            } else {
                                "尚有核心权限待授权 (${healthState.coreReadyCount}/${SystemHealthState.CORE_CHECK_COUNT})"
                            },
                            tone = if (healthState.isAllCoreReady) StatusTone.SUCCESS else StatusTone.WARNING,
                            leadingDot = true,
                        )
                    }
                }
            },
            actions = {
                Surface(
                    onClick = { onNavigateTab(MainTab.SETTINGS) },
                    shape = CircleShape,
                    color = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "健康诊断",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = bottomContentPadding + 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Collapsible System Readiness Card
            val readinessContainer = if (healthState.isAllCoreReady) {
                semantic.successContainer
            } else {
                scheme.surfaceContainerLow
            }
            val readinessContent = if (healthState.isAllCoreReady) {
                semantic.onSuccessContainer
            } else {
                scheme.onSurface
            }
            TonalSectionCard(
                containerColor = readinessContainer,
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { readinessExpanded = !readinessExpanded }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = if (healthState.isAllCoreReady) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.WarningAmber
                            },
                            contentDescription = null,
                            tint = if (healthState.isAllCoreReady) semantic.success else semantic.warning,
                        )
                        Column {
                            Text(
                                text = if (healthState.isAllCoreReady) {
                                    "系统已就绪 · ${healthState.coreReadyCount}/${SystemHealthState.CORE_CHECK_COUNT} 通道正常"
                                } else {
                                    "系统准备度（${healthState.coreReadyCount}/${SystemHealthState.CORE_CHECK_COUNT} 项就绪）"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = readinessContent,
                            )
                            Text(
                                text = if (healthState.isAllCoreReady) {
                                    "通知读取、发布、敏感权限、实时提升与电池优化均正常"
                                } else {
                                    "按下方提示补齐授权后，验证码与白名单通知会自动弹出胶囊"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = readinessContent.copy(alpha = 0.8f),
                            )
                        }
                    }
                    Icon(
                        imageVector = if (readinessExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (readinessExpanded) "收起系统准备度" else "展开系统准备度",
                        tint = readinessContent,
                    )
                }

                AnimatedVisibility(
                    visible = readinessExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                        ReadinessCheckRow(
                            title = "1. 通知读取权限",
                            subtitle = when {
                                healthState.listenerReady -> "已授权且监听服务在线"
                                healthState.notificationListenerEnabled -> "已在系统开启，等待服务重新绑定"
                                else -> "用于读取短信验证码与白名单应用通知"
                            },
                            ready = healthState.listenerReady,
                            pendingInSystem = healthState.notificationListenerEnabled && !healthState.listenerConnected,
                            actionLabel = if (healthState.listenerReady) "已就绪" else "去开启",
                            onAction = { SystemHealthChecker.openNotificationListenerSettings(context) },
                        )
                        ReadinessCheckRow(
                            title = "2. 通知发布权限",
                            subtitle = if (healthState.postNotificationsGranted) "已允许发送状态栏胶囊通知" else "必须开启才能弹出顶部实时胶囊",
                            ready = healthState.postNotificationsGranted,
                            actionLabel = if (healthState.postNotificationsGranted) "已授权 ✓" else "去授权",
                            onAction = onRequestPostNotificationsOrOpenSettings,
                        )
                        ReadinessCheckRow(
                            title = "3. 受限敏感通知权限",
                            subtitle = if (healthState.sensitiveNotificationsGranted) "已允许读取短信与敏感验证码" else "Android 15+ 默认拦截验证码读取，需在应用详情授权",
                            ready = healthState.sensitiveNotificationsGranted,
                            actionLabel = if (healthState.sensitiveNotificationsGranted) "已授权" else "去授权",
                            onAction = { SystemHealthChecker.openSensitiveNotificationSettings(context) },
                        )
                        ReadinessCheckRow(
                            title = "4. 实时通知提升（Live Updates）",
                            subtitle = if (healthState.promotedNotificationsAllowed) "已允许展示状态栏胶囊与锁屏实时卡片" else "开启后可在状态栏高亮显示胶囊芯片",
                            ready = healthState.promotedNotificationsAllowed,
                            actionLabel = if (healthState.promotedNotificationsAllowed) "已允许" else "去设置",
                            onAction = onOpenPromotionSettings,
                        )
                        ReadinessCheckRow(
                            title = "5. 忽略电池优化",
                            subtitle = if (healthState.batteryOptimizationIgnored) "已在后台白名单中" else "建议设为不受限制，防止后台被系统休眠",
                            ready = healthState.batteryOptimizationIgnored,
                            actionLabel = if (healthState.batteryOptimizationIgnored) "不受限" else "去申请",
                            onAction = { SystemHealthChecker.openBatteryOptimizationSettings(context) },
                        )
                    }
                }
            }

            // 2. Live Interactive Capsule Sandbox Preview Card
            SectionLabel(
                title = "实时胶囊沙盒预览",
                subtitle = "直观查看当前隐私与时长设定下的状态栏胶囊效果，并可一键发真实测试通知",
            )
            TonalSectionCard(
                containerColor = scheme.surfaceContainer,
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SandboxPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = sandboxState.preset == preset,
                            onClick = { onSelectSandboxPreset(preset) },
                            label = { Text(preset.label) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Simulated Pixel/ColorOS Status Bar Pill + Expandable Live Update Card
                LiveCapsulePreviewSurface(
                    sandboxState = sandboxState,
                    settingsState = settingsState,
                    onToggleExpanded = onToggleSandboxExpanded,
                    onCopyOtp = { code -> onCopySandboxOtp(code) },
                )

                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onPublishTestOtp,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text("测试验证码")
                    }
                    FilledTonalButton(
                        onClick = onPublishTestChat,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text("测试微信回复")
                    }
                    FilledTonalButton(
                        onClick = onPublishTestProgress,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text("测试美团进度")
                    }
                }
            }

            // 3. 2x2 Quick Overview Tiles
            SectionLabel(
                title = "核心能力概览",
                subtitle = "点击卡片直达对应管理页面",
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickOverviewTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Apps,
                        title = "通知来源",
                        value = "${appsState.selectedCount} 个已启用",
                        supporting = "管理白名单与单应用规则",
                        tone = StatusTone.PRIMARY,
                        onClick = { onNavigateTab(MainTab.APPS_AND_RULES) },
                    )
                    QuickOverviewTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.CloudSync,
                        title = "短信云同步",
                        value = when {
                            syncSummary.isLive -> "实时已连接"
                            syncSummary.reconnecting -> "重连中"
                            syncSummary.isConnected -> "连接中"
                            else -> "未连接"
                        },
                        supporting = if (syncSummary.isConnected && syncSummary.account.isNotBlank()) {
                            syncSummary.account
                        } else {
                            "接收其他设备转发短信"
                        },
                        tone = when {
                            syncSummary.isLive -> StatusTone.SUCCESS
                            syncSummary.reconnecting -> StatusTone.WARNING
                            else -> StatusTone.NEUTRAL
                        },
                        onClick = onOpenSyncSettings,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickOverviewTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.History,
                        title = "本地历史",
                        value = "${historyState.totalCount} 条记录",
                        supporting = if (historyState.recordingEnabled) {
                            "正在记录 · ${historyState.retentionPolicy.chineseLabel()}"
                        } else {
                            "记录已暂停"
                        },
                        tone = if (historyState.recordingEnabled) StatusTone.SECONDARY else StatusTone.NEUTRAL,
                        onClick = { onNavigateTab(MainTab.HISTORY) },
                    )
                    QuickOverviewTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Rounded.LibraryBooks,
                        title = "识别词库",
                        value = "v${ruleState.installedVersion}",
                        supporting = when {
                            ruleState.availableManifest != null -> "发现新版本 v${ruleState.availableManifest.manifest.version}"
                            ruleState.isBuiltInSource -> "内置规则"
                            else -> "官方订阅"
                        },
                        tone = if (ruleState.availableManifest != null) StatusTone.WARNING else StatusTone.TERTIARY,
                        onClick = { onNavigateTab(MainTab.APPS_AND_RULES) },
                    )
                }
            }

            // 4. Human-readable Recent Activity Strip
            SectionLabel(
                title = "最近活动",
                subtitle = "仅记录处理结果摘要，不保存明文验证码或回复内容",
                trailing = {
                    TextButton(onClick = { onNavigateTab(MainTab.SETTINGS) }) {
                        Text("查看完整诊断")
                    }
                },
            )
            RecentActivityCard(diagnostics = healthState.diagnostics)
        }
    }
}

@Composable
private fun ReadinessCheckRow(
    title: String,
    subtitle: String,
    ready: Boolean,
    pendingInSystem: Boolean = false,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                StatusBadge(
                    text = when {
                        ready -> "正常"
                        pendingInSystem -> "待绑定"
                        else -> "未开启"
                    },
                    tone = when {
                        ready -> StatusTone.SUCCESS
                        pendingInSystem -> StatusTone.WARNING
                        else -> StatusTone.ERROR
                    },
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (ready) {
            OutlinedButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            FilledTonalButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveCapsulePreviewSurface(
    sandboxState: SandboxPreviewState,
    settingsState: SettingsUiState,
    onToggleExpanded: () -> Unit,
    onCopyOtp: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isOtp = sandboxState.preset == SandboxPreset.OTP
    val hideOtp = isOtp && !settingsState.showOtpDirectly
    val hideBody = !isOtp && !settingsState.showWhitelistContent

    val pillText = when {
        hideOtp -> "收到验证码"
        hideBody -> sandboxState.title
        else -> sandboxState.shortText
    }
    val cardTitle = when {
        hideOtp -> "收到验证码"
        else -> sandboxState.title
    }
    val cardBody = when {
        hideOtp -> "锁屏与收起态已隐藏验证码 · 解锁或点击复制查看"
        hideBody -> "「白名单显示正文」已关闭，锁屏与收起胶囊隐藏正文"
        else -> sandboxState.body
    }
    val ttlText = if (isOtp) "5 分钟（验证码默认）" else "全局 ${settingsState.capsuleDurationMinutes} 分钟"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Simulated status-bar row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    onClick = onToggleExpanded,
                    shape = CircleShape,
                    color = scheme.primary,
                    contentColor = scheme.onPrimary,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_capsule),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = pillText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = if (sandboxState.expanded) "点击胶囊收起" else "点击胶囊展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = sandboxState.expanded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.primaryContainer.copy(alpha = 0.45f),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = sandboxState.sourceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = ttlText,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = cardTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = cardBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        if (sandboxState.progress != null) {
                            LinearProgressIndicator(
                                progress = { sandboxState.progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            )
                        }
                        FlowRow(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isOtp && sandboxState.otpCode != null) {
                                FilledTonalButton(
                                    onClick = { onCopyOtp(sandboxState.otpCode) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("复制 ${sandboxState.otpCode}")
                                }
                                StatusBadge(
                                    text = if (settingsState.maskOtpClipboardPreview) {
                                        "剪贴板预览已打码"
                                    } else {
                                        "剪贴板明文预览"
                                    },
                                    tone = if (settingsState.maskOtpClipboardPreview) {
                                        StatusTone.SUCCESS
                                    } else {
                                        StatusTone.NEUTRAL
                                    },
                                )
                            } else if (sandboxState.preset == SandboxPreset.CHAT) {
                                listOf("好的没问题", "收到", "打开回复").forEach { reply ->
                                    StatusBadge(text = reply, tone = StatusTone.SECONDARY)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickOverviewTile(
    icon: ImageVector,
    title: String,
    value: String,
    supporting: String,
    tone: StatusTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                StatusBadge(text = value, tone = tone)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RecentActivityCard(diagnostics: DiagnosticsSnapshot) {
    val context = LocalContext.current
    val lastPkgLabel = remember(diagnostics.lastNotificationPackage) {
        val pkg = diagnostics.lastNotificationPackage ?: return@remember "尚无通知记录"
        runCatching {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            "${context.packageManager.getApplicationLabel(info)} ($pkg)"
        }.getOrDefault(pkg)
    }
    TonalSectionCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityRow(
                label = "监听服务",
                value = if (diagnostics.listenerConnected) "已连接" else "未连接",
                timeText = RelativeTimeFormatter.format(diagnostics.listenerChangedAt),
            )
            ActivityRow(
                label = "最近通知来源",
                value = lastPkgLabel,
                timeText = RelativeTimeFormatter.format(diagnostics.lastNotificationAt),
            )
            ActivityRow(
                label = "最近规则解析",
                value = humanParseOutcome(diagnostics.lastParseOutcome),
                timeText = RelativeTimeFormatter.format(diagnostics.lastParseAt),
            )
            ActivityRow(
                label = "最近胶囊发布",
                value = humanPublisherLabel(diagnostics.lastPublisher, diagnostics.lastPublishDetail),
                timeText = RelativeTimeFormatter.format(diagnostics.lastPublishAt),
            )
        }
    }
}

@Composable
private fun ActivityRow(label: String, value: String, timeText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun humanParseOutcome(outcome: String?): String = when {
    outcome.isNullOrBlank() -> "尚无解析记录"
    outcome.startsWith("OTP_SUCCESS") -> "成功提取验证码 ($outcome)"
    outcome.startsWith("OTP_AMBIGUOUS") -> "发现多个候选验证码 ($outcome)"
    outcome == "NO_OTP" -> "未匹配到验证码"
    outcome == "WHITELIST_NOTIFICATION" -> "按白名单正文展示"
    outcome == "SKIPPED_NOT_WHITELISTED" -> "来源不在白名单中"
    outcome == "SKIPPED_EMPTY_OR_GROUP_SUMMARY" -> "跳过空白或群组摘要"
    outcome == "SKIPPED_APP_RULE" -> "命中单应用关键词过滤"
    outcome == "SKIPPED_OTP_ONLY_NON_OTP" -> "仅验证码模式下已跳过非验证码"
    outcome.startsWith("KNOWN_APP_SUPPRESSED_") -> "专属适配器已抑制营销通知"
    outcome.startsWith("KNOWN_APP_") -> "专属适配器已转换实时进度"
    else -> outcome
}

internal fun humanPublisherLabel(publisher: String?, detail: String?): String {
    if (publisher.isNullOrBlank()) return "尚无发布记录"
    val readablePub = when (publisher) {
        "AOSP_LIVE_UPDATE" -> "系统实时胶囊 (Live Update)"
        "NOTIFICATION_FALLBACK" -> "标准通知兜底通道"
        "PROMOTION_SETTINGS" -> "系统实时通知入口检测"
        else -> publisher
    }
    return if (detail.isNullOrBlank()) readablePub else "$readablePub · $detail"
}
