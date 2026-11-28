package io.github.venompool888.fluidcapsule.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.venompool888.fluidcapsule.R
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
import io.github.venompool888.fluidcapsule.ui.theme.FluidTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settingsState: SettingsUiState,
    healthState: SystemHealthState,
    syncSummary: SyncSummaryState,
    rawDiagnosticsText: String,
    appVersionName: String,
    focusPermissionsRequestId: Int,
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
    onOpenGitHubRepository: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var sliderValue by remember(settingsState.capsuleDurationMinutes) {
        mutableFloatStateOf(settingsState.capsuleDurationMinutes.toFloat())
    }
    var showRawDiagnostics by remember { mutableStateOf(false) }
    var healthHubOffsetY by remember { mutableIntStateOf(0) }
    var handledFocusRequestId by remember { mutableIntStateOf(0) }

    LaunchedEffect(focusPermissionsRequestId, healthHubOffsetY) {
        if (focusPermissionsRequestId > handledFocusRequestId && healthHubOffsetY > 0) {
            handledFocusRequestId = focusPermissionsRequestId
            scrollState.animateScrollTo(healthHubOffsetY)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = statusBarTop + 12.dp,
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

        // 3. 短信云同步 (streamlined 1-row card)
        SectionLabel(
            title = "短信云同步",
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenSyncSettings,
            shape = RoundedCornerShape(22.dp),
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
                    color = if (syncSummary.isLive) scheme.primary else scheme.surfaceContainerHighest,
                    contentColor = if (syncSummary.isLive) scheme.onPrimary else scheme.onSurfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "短信云同步",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    val syncLine = when {
                        syncSummary.isLive && syncSummary.account.isNotBlank() ->
                            "实时在线 · ${syncSummary.account}"
                        syncSummary.isLive -> "实时在线"
                        syncSummary.reconnecting && syncSummary.account.isNotBlank() ->
                            "重连中 · ${syncSummary.account}"
                        syncSummary.reconnecting -> "重连中 · ${syncSummary.lastError}"
                        syncSummary.isConnected && syncSummary.account.isNotBlank() ->
                            "连接中 · ${syncSummary.account}"
                        syncSummary.isConnected -> "连接中"
                        else -> "未连接 · 从另一部手机接收短信胶囊"
                    }
                    Text(
                        text = syncLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "进入短信云同步设置",
                    tint = scheme.onSurfaceVariant,
                )
            }
        }

        // 4. 权限与后台保活 (single authoritative Permissions & Keep-Alive Hub)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    healthHubOffsetY = coordinates.positionInParent().y.roundToInt()
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(
                title = "权限与后台保活",
                subtitle = "系统通道授权与后台保活设置",
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        StatusBadge(
                            text = "${healthState.coreReadyCount}/${SystemHealthState.CORE_CHECK_COUNT} 核心就绪",
                            tone = if (healthState.isAllCoreReady) StatusTone.SUCCESS else StatusTone.WARNING,
                        )
                        IconButton(onClick = onRefreshDiagnostics) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新诊断状态",
                                tint = scheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
            TonalSectionCard(
                containerColor = scheme.surfaceContainerLow,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) {
                SettingSwitchRow(
                    title = "前台保活服务",
                    summary = "常驻保活通知对抗后台清理",
                    checked = settingsState.keepAliveEnabled,
                    onCheckedChange = onToggleKeepAlive,
                )

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
                HealthCheckRow(
                    title = "通知读取",
                    ready = healthState.listenerReady,
                    pending = healthState.notificationListenerEnabled && !healthState.listenerConnected,
                    detail = when {
                        healthState.listenerReady -> "已授权且监听服务在线"
                        healthState.notificationListenerEnabled -> "已授权，等待系统重绑"
                        else -> "读取短信验证码与白名单通知"
                    },
                    actionLabel = if (healthState.listenerReady) "已开启" else "去开启",
                    onAction = { SystemHealthChecker.openNotificationListenerSettings(context) },
                )

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
                HealthCheckRow(
                    title = "通知发布",
                    ready = healthState.postNotificationsGranted,
                    detail = if (healthState.postNotificationsGranted) "已允许发送状态栏胶囊" else "未授予通知发布权限",
                    actionLabel = if (healthState.postNotificationsGranted) "已开启" else "去授权",
                    onAction = onRequestPostNotificationsOrOpenSettings,
                )

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
                HealthCheckRow(
                    title = "受限敏感通知权限",
                    ready = healthState.sensitiveNotificationsGranted,
                    detail = if (healthState.sensitiveNotificationsGranted) {
                        "已允许读取敏感验证码"
                    } else {
                        "Android 15+ 默认拦截，需在应用详情授权"
                    },
                    actionLabel = if (healthState.sensitiveNotificationsGranted) "已开启" else "去授权",
                    onAction = { SystemHealthChecker.openSensitiveNotificationSettings(context) },
                )

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
                HealthCheckRow(
                    title = "实时通知提升",
                    ready = healthState.promotedNotificationsAllowed,
                    detail = if (healthState.promotedNotificationsAllowed) {
                        "已允许展示状态栏胶囊芯片"
                    } else {
                        "开启后可高亮展示状态栏胶囊"
                    },
                    actionLabel = if (healthState.promotedNotificationsAllowed) "已允许" else "去设置",
                    onAction = onOpenPromotionSettings,
                )

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
                HealthCheckRow(
                    title = "忽略电池优化",
                    ready = healthState.batteryOptimizationIgnored,
                    detail = if (healthState.batteryOptimizationIgnored) "已在后台白名单中" else "建议设为不受限制",
                    actionLabel = if (healthState.batteryOptimizationIgnored) "不受限" else "去申请",
                    onAction = { SystemHealthChecker.openBatteryOptimizationSettings(context) },
                )

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
                HealthCheckRow(
                    title = "无障碍保活服务",
                    ready = healthState.accessibilityReady,
                    pending = healthState.accessibilityKeepAliveEnabled && !healthState.accessibilityConnected,
                    optional = true,
                    detail = when {
                        healthState.accessibilityReady -> "无障碍保活运行中"
                        healthState.accessibilityKeepAliveEnabled -> "已在系统开启，等待连接"
                        else -> "可选，对抗激进杀后台"
                    },
                    actionLabel = if (healthState.accessibilityReady) "已开启" else "去设置",
                    onAction = { SystemHealthChecker.openAccessibilitySettings(context) },
                )
            }
        }

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

        // GitHub project repository entry
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenGitHubRepository,
            shape = RoundedCornerShape(22.dp),
            color = scheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_github),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GitHub 项目仓库",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Venompool888/FluidCapsule · v$appVersionName · 开源说明与新版下载",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "打开 GitHub 项目仓库",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun HealthCheckRow(
    title: String,
    ready: Boolean,
    pending: Boolean = false,
    optional: Boolean = false,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val semantic = FluidTheme.semanticColors
    val dotColor = when {
        ready -> semantic.success
        pending -> semantic.warning
        optional -> scheme.onSurfaceVariant
        else -> scheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onAction)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (ready) scheme.onSurfaceVariant else scheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
