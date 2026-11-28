package io.github.venompool888.fluidcapsule.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.github.venompool888.fluidcapsule.ui.state.SettingsUiState
import io.github.venompool888.fluidcapsule.ui.state.SyncSummaryState
import io.github.venompool888.fluidcapsule.ui.state.SystemHealthState
import io.github.venompool888.fluidcapsule.ui.theme.FluidTheme

@Composable
fun OverviewScreen(
    healthState: SystemHealthState,
    settingsState: SettingsUiState,
    appsState: AppsUiState,
    historyState: HistoryUiState,
    ruleState: RuleSubscriptionUiState,
    syncSummary: SyncSummaryState,
    bottomContentPadding: Dp,
    onNavigateTab: (MainTab) -> Unit,
    onNavigateToSettingsPermissions: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onPublishTestOtp: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val semantic = FluidTheme.semanticColors
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val coreReady = healthState.isAllCoreReady
    val cloudHealthy = !coreReady &&
        syncSummary.isConnected &&
        healthState.postNotificationsGranted &&
        healthState.promotedNotificationsAllowed &&
        healthState.batteryOptimizationIgnored

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = statusBarTop + 12.dp,
                bottom = bottomContentPadding + 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Scrollable brand header row (non-sticky)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
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
                Text(
                    text = "流体胶囊",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            StatusBadge(
                text = when {
                    coreReady -> "运行正常"
                    cloudHealthy -> "云同步就绪"
                    else -> "待授权 · ${healthState.coreReadyCount}/${SystemHealthState.CORE_CHECK_COUNT}"
                },
                tone = if (coreReady || cloudHealthy) StatusTone.SUCCESS else StatusTone.WARNING,
                leadingDot = true,
            )
        }

        // Compact single-row status/permission banner when any local core check is incomplete
        if (!coreReady) {
            val missingCount = SystemHealthState.CORE_CHECK_COUNT - healthState.coreReadyCount
            val bannerBg = if (cloudHealthy) scheme.surfaceContainerLow else semantic.warningContainer
            val bannerFg = if (cloudHealthy) scheme.onSurface else semantic.onWarningContainer
            val iconTint = if (cloudHealthy) semantic.success else semantic.warning

            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToSettingsPermissions,
                shape = RoundedCornerShape(18.dp),
                color = bannerBg,
                contentColor = bannerFg,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = if (cloudHealthy) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (cloudHealthy) {
                                "云同步接收模式正常"
                            } else {
                                "尚有 $missingCount 项权限待授权"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (cloudHealthy) {
                                "如需监听本机通知，可在设置中补齐权限"
                            } else {
                                "前往「设置」补齐后即可自动弹出胶囊"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = bannerFg.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!cloudHealthy) {
                        Text(
                            text = "去设置",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = bannerFg.copy(alpha = 0.7f),
                    )
                }
            }
        }

        // 2x2 Quick Overview Tiles
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
                    supporting = "白名单与单应用规则",
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
                        "跨设备转发短信"
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
                    value = "${historyState.totalCount} 条",
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

        // Single-row native test OTP sender card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = scheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_capsule),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "发送测试验证码",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    val otpMode = if (settingsState.showOtpDirectly) "直接显示验证码" else "锁屏隐藏验证码"
                    val maskSuffix = if (settingsState.maskOtpClipboardPreview) " · 复制打码" else ""
                    Text(
                        text = "按系统原生样式弹出 · $otpMode$maskSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = onPublishTestOtp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("发送测试")
                }
            }
        }

        // Recent Activity section (single source of truth on Overview)
        SectionLabel(
            title = "最近活动",
            subtitle = "仅记录处理摘要，不保存明文验证码",
            trailing = {
                TextButton(onClick = onNavigateToSettingsPermissions) {
                    Text("完整诊断")
                }
            },
        )
        RecentActivityCard(diagnostics = healthState.diagnostics)
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
    val lastPkgLabel = androidx.compose.runtime.remember(diagnostics.lastNotificationPackage) {
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
