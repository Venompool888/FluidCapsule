package io.github.venompool888.fluidcapsule.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.venompool888.fluidcapsule.settings.CapsuleDisplayDuration
import io.github.venompool888.fluidcapsule.settings.ThemeMode
import io.github.venompool888.fluidcapsule.ui.components.SectionLabel
import io.github.venompool888.fluidcapsule.ui.components.SettingSwitchRow
import io.github.venompool888.fluidcapsule.ui.components.StatusBadge
import io.github.venompool888.fluidcapsule.ui.components.StatusTone
import io.github.venompool888.fluidcapsule.ui.components.TonalSectionCard
import io.github.venompool888.fluidcapsule.ui.state.SettingsUiState
import io.github.venompool888.fluidcapsule.ui.state.SyncSummaryState
import io.github.venompool888.fluidcapsule.ui.state.SystemHealthChecker
import io.github.venompool888.fluidcapsule.ui.state.SystemHealthState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settingsState: SettingsUiState,
    healthState: SystemHealthState,
    syncSummary: SyncSummaryState,
    rawDiagnosticsText: String,
    bottomContentPadding: Dp,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onCapsuleDurationChange: (Int) -> Unit,
    onToggleShowOtpDirectly: (Boolean) -> Unit,
    onToggleMaskClipboard: (Boolean) -> Unit,
    onToggleShowWhitelistContent: (Boolean) -> Unit,
    onOpenSyncSettings: () -> Unit,
    onToggleKeepAlive: (Boolean) -> Unit,
    onRequestPostNotificationsOrOpenSettings: () -> Unit,
    onOpenPromotionSettings: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var sliderValue by remember(settingsState.capsuleDurationMinutes) {
        mutableFloatStateOf(settingsState.capsuleDurationMinutes.toFloat())
    }
    var showRawDiagnostics by remember { mutableStateOf(false) }

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
                Column {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                    )
                    Text(
                        text = "外观、全局胶囊偏好、云同步与运行诊断",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
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
            // 1. 外观与 Material You
            SectionLabel(
                title = "外观与 Material You",
                subtitle = "选择应用外观，不会修改手机的系统主题",
            )
            TonalSectionCard(containerColor = scheme.surfaceContainerLow) {
                val themeChoices = listOf(
                    ThemeMode.LIGHT to "浅色",
                    ThemeMode.DARK to "深色",
                    ThemeMode.SYSTEM to "跟随系统",
                )
                Text(
                    text = when (settingsState.themeMode) {
                        ThemeMode.DARK -> "当前：始终使用暗色外观"
                        ThemeMode.LIGHT -> "当前：始终使用浅色外观"
                        ThemeMode.SYSTEM -> "当前：跟随系统自动切换"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    themeChoices.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = settingsState.themeMode == mode,
                            onClick = { onSelectThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = themeChoices.size,
                            ),
                            label = { Text(label) },
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = scheme.outlineVariant.copy(alpha = 0.5f),
                )
                SettingSwitchRow(
                    title = "跟随壁纸动态取色",
                    summary = "启用系统 Material You 动态色彩；关闭后使用流体青品牌配色",
                    checked = settingsState.dynamicColorEnabled,
                    onCheckedChange = onToggleDynamicColor,
                )
            }

            // 2. 全局胶囊时长与隐私
            SectionLabel(
                title = "全局胶囊时长与隐私",
                subtitle = "控制胶囊停留窗口以及锁屏和剪贴板中的敏感内容",
            )
            TonalSectionCard(containerColor = scheme.surfaceContainerLow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "单条胶囊显示时长",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "1–30 分钟连续可调；从下一条胶囊开始生效",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    StatusBadge(
                        text = "${sliderValue.roundToInt()} 分钟",
                        tone = StatusTone.PRIMARY,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(
                        enabled = sliderValue.roundToInt() > CapsuleDisplayDuration.minMinutes,
                        onClick = {
                            val next = (sliderValue.roundToInt() - 1)
                                .coerceIn(CapsuleDisplayDuration.minMinutes, CapsuleDisplayDuration.maxMinutes)
                            sliderValue = next.toFloat()
                            onCapsuleDurationChange(next)
                        },
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "减少显示时长")
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            val rounded = sliderValue.roundToInt()
                                .coerceIn(CapsuleDisplayDuration.minMinutes, CapsuleDisplayDuration.maxMinutes)
                            sliderValue = rounded.toFloat()
                            onCapsuleDurationChange(rounded)
                        },
                        valueRange = CapsuleDisplayDuration.minMinutes.toFloat()..CapsuleDisplayDuration.maxMinutes.toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(
                        enabled = sliderValue.roundToInt() < CapsuleDisplayDuration.maxMinutes,
                        onClick = {
                            val next = (sliderValue.roundToInt() + 1)
                                .coerceIn(CapsuleDisplayDuration.minMinutes, CapsuleDisplayDuration.maxMinutes)
                            sliderValue = next.toFloat()
                            onCapsuleDurationChange(next)
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "增加显示时长")
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(1, 5, 10, 15, 30).forEach { preset ->
                        FilterChip(
                            selected = sliderValue.roundToInt() == preset,
                            onClick = {
                                sliderValue = preset.toFloat()
                                onCapsuleDurationChange(preset)
                            },
                            label = { Text("$preset 分钟") },
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = scheme.outlineVariant.copy(alpha = 0.5f),
                )
                SettingSwitchRow(
                    title = "直接显示验证码",
                    summary = "包含锁屏胶囊；关闭后仍可点击复制",
                    checked = settingsState.showOtpDirectly,
                    onCheckedChange = onToggleShowOtpDirectly,
                )
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                SettingSwitchRow(
                    title = "隐藏剪贴板预览",
                    summary = "复制验证码时，用掩码保护系统预览",
                    checked = settingsState.maskOtpClipboardPreview,
                    onCheckedChange = onToggleMaskClipboard,
                )
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                SettingSwitchRow(
                    title = "白名单显示正文",
                    summary = "包含锁屏；适用于已信任的通知来源",
                    checked = settingsState.showWhitelistContent,
                    onCheckedChange = onToggleShowWhitelistContent,
                )
            }

            // 3. 短信云同步
            SectionLabel(
                title = "短信云同步",
                subtitle = "从另一部手机的「胶囊短信」实时接收短信胶囊",
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenSyncSettings,
                shape = RoundedCornerShape(22.dp),
                color = scheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (syncSummary.isLive) scheme.primary else scheme.surfaceContainerHighest,
                        contentColor = if (syncSummary.isLive) scheme.onPrimary else scheme.onSurfaceVariant,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CloudSync,
                                contentDescription = null,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "短信云同步",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            StatusBadge(
                                text = when {
                                    syncSummary.isLive -> "实时在线"
                                    syncSummary.reconnecting -> "重连中"
                                    syncSummary.isConnected -> "连接中"
                                    else -> "未连接"
                                },
                                tone = when {
                                    syncSummary.isLive -> StatusTone.SUCCESS
                                    syncSummary.reconnecting -> StatusTone.WARNING
                                    else -> StatusTone.NEUTRAL
                                },
                                leadingDot = true,
                            )
                        }
                        Text(
                            text = if (syncSummary.isConnected && syncSummary.account.isNotBlank()) {
                                "${syncSummary.account} · ${syncSummary.summaryText}"
                            } else {
                                syncSummary.summaryText
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "进入短信云同步设置",
                        tint = scheme.primary,
                    )
                }
            }

            // 4. 保活守护与运行诊断
            SectionLabel(
                title = "保活守护与运行诊断",
                subtitle = "保活开关、系统通道健康与最近运行记录",
            )
            TonalSectionCard(containerColor = scheme.surfaceContainerLow) {
                SettingSwitchRow(
                    title = "前台保活服务",
                    summary = "开启常驻保活通知以对抗后台清理，并在重启后自动恢复",
                    checked = settingsState.keepAliveEnabled,
                    onCheckedChange = onToggleKeepAlive,
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = scheme.outlineVariant.copy(alpha = 0.5f),
                )

                HealthCheckRow(
                    title = "通知读取",
                    ready = healthState.listenerReady,
                    pending = healthState.notificationListenerEnabled && !healthState.listenerConnected,
                    detail = when {
                        healthState.listenerReady -> "已授权且连接正常"
                        healthState.notificationListenerEnabled -> "已授权，等待系统重绑"
                        else -> "未开启通知使用权"
                    },
                    actionLabel = "去设置",
                    onAction = { SystemHealthChecker.openNotificationListenerSettings(context) },
                )
                HealthCheckRow(
                    title = "通知发布",
                    ready = healthState.postNotificationsGranted,
                    detail = if (healthState.postNotificationsGranted) "已允许发送通知" else "未授予发布权限",
                    actionLabel = if (healthState.postNotificationsGranted) "查看" else "去授权",
                    onAction = onRequestPostNotificationsOrOpenSettings,
                )
                HealthCheckRow(
                    title = "受限敏感通知权限",
                    ready = healthState.sensitiveNotificationsGranted,
                    detail = if (healthState.sensitiveNotificationsGranted) {
                        "已允许读取验证码短信/通知"
                    } else {
                        "未授予受限敏感通知权限"
                    },
                    actionLabel = "去设置",
                    onAction = { SystemHealthChecker.openSensitiveNotificationSettings(context) },
                )
                HealthCheckRow(
                    title = "实时通知提升",
                    ready = healthState.promotedNotificationsAllowed,
                    detail = if (healthState.promotedNotificationsAllowed) {
                        "支持并已开启实时通知提升"
                    } else {
                        "未开启实时通知提升"
                    },
                    actionLabel = "去设置",
                    onAction = onOpenPromotionSettings,
                )
                HealthCheckRow(
                    title = "忽略电池优化",
                    ready = healthState.batteryOptimizationIgnored,
                    detail = if (healthState.batteryOptimizationIgnored) "已忽略电池优化" else "建议设为不受限制",
                    actionLabel = "去申请",
                    onAction = { SystemHealthChecker.openBatteryOptimizationSettings(context) },
                )
                HealthCheckRow(
                    title = "无障碍保活服务",
                    ready = healthState.accessibilityReady,
                    pending = healthState.accessibilityKeepAliveEnabled && !healthState.accessibilityConnected,
                    detail = when {
                        healthState.accessibilityReady -> "无障碍保活运行中"
                        healthState.accessibilityKeepAliveEnabled -> "已在系统开启，等待连接"
                        else -> "未开启（对抗激进杀后台时可启用）"
                    },
                    actionLabel = "去设置",
                    onAction = { SystemHealthChecker.openAccessibilitySettings(context) },
                )

                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onRefreshDiagnostics,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("刷新诊断状态")
                }
            }

            RecentActivityCard(diagnostics = healthState.diagnostics)

            // Expandable Developer Raw Fields
            TonalSectionCard(
                containerColor = scheme.surfaceContainer,
                contentPadding = PaddingValues(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showRawDiagnostics = !showRawDiagnostics }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "开发者原始字段",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Icon(
                        imageVector = if (showRawDiagnostics) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (showRawDiagnostics) "收起原始诊断字段" else "展开原始诊断字段",
                    )
                }
                AnimatedVisibility(visible = showRawDiagnostics) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surfaceContainerLowest,
                    ) {
                        Text(
                            text = rawDiagnosticsText,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthCheckRow(
    title: String,
    ready: Boolean,
    pending: Boolean = false,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusBadge(
                    text = when {
                        ready -> "已开启"
                        pending -> "待连接"
                        else -> "未开启"
                    },
                    tone = when {
                        ready -> StatusTone.SUCCESS
                        pending -> StatusTone.WARNING
                        else -> StatusTone.ERROR
                    },
                )
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        OutlinedButton(
            onClick = onAction,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(actionLabel, style = MaterialTheme.typography.labelMedium)
        }
    }
}
