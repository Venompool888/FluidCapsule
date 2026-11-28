package io.github.venompool888.fluidcapsule.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.venompool888.fluidcapsule.settings.AppRuleActivity
import io.github.venompool888.fluidcapsule.ui.components.AppIconBadge
import io.github.venompool888.fluidcapsule.ui.components.SettingSwitchRow
import io.github.venompool888.fluidcapsule.ui.components.TonalSectionCard
import io.github.venompool888.fluidcapsule.ui.components.summarizeRuleInline
import io.github.venompool888.fluidcapsule.ui.state.AppEntryUi
import io.github.venompool888.fluidcapsule.ui.state.AppFilter
import io.github.venompool888.fluidcapsule.ui.state.AppsUiState
import io.github.venompool888.fluidcapsule.ui.state.RuleSubscriptionUiState

@Composable
fun AppsAndRulesScreen(
    appsState: AppsUiState,
    ruleState: RuleSubscriptionUiState,
    bottomContentPadding: Dp,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onRefreshAndSort: () -> Unit,
    onToggleRuleSubscription: (Boolean) -> Unit,
    onCheckRuleUpdates: () -> Unit,
    onInstallAvailableRule: () -> Unit,
    onRestoreBuiltInRules: () -> Unit,
) {
    var showRuleSheet by remember { mutableStateOf(false) }

    AppsAndRulesContent(
        title = "应用与规则",
        appsState = appsState,
        ruleState = ruleState,
        bottomContentPadding = bottomContentPadding,
        showBackButton = false,
        onNavigateBack = {},
        onOpenRuleSheet = { showRuleSheet = true },
        onSearchQueryChange = onSearchQueryChange,
        onFilterChange = onFilterChange,
        onToggleApp = onToggleApp,
        onRefreshAndSort = onRefreshAndSort,
    )

    if (showRuleSheet) {
        RuleSubscriptionSheet(
            ruleState = ruleState,
            onDismiss = { showRuleSheet = false },
            onToggleSubscription = onToggleRuleSubscription,
            onCheckUpdates = onCheckRuleUpdates,
            onInstallAvailable = onInstallAvailableRule,
            onRestoreBuiltIn = onRestoreBuiltInRules,
        )
    }
}

@Composable
fun AppsAndRulesContent(
    title: String,
    appsState: AppsUiState,
    ruleState: RuleSubscriptionUiState?,
    bottomContentPadding: Dp,
    showBackButton: Boolean,
    onNavigateBack: () -> Unit,
    onOpenRuleSheet: (() -> Unit)?,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onRefreshAndSort: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val visibleEntries = appsState.visibleEntries
    val isUnfiltered = appsState.searchQuery.isBlank() && appsState.filter == AppFilter.ALL
    val showPkg = appsState.searchQuery.contains('.')

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("apps_list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = statusBarTop + 10.dp,
            bottom = bottomContentPadding + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "apps_top_strip") {
            if (showBackButton) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (isUnfiltered) {
                                    "已启用 ${appsState.selectedCount} 个应用"
                                } else {
                                    "显示 ${visibleEntries.size} 个匹配应用"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onRefreshAndSort) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新并重排应用列表",
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = when {
                            appsState.isInitialLoading && appsState.entries.isEmpty() -> "正在加载应用…"
                            isUnfiltered -> "已启用 ${appsState.selectedCount} 个应用"
                            else -> "显示 ${visibleEntries.size} 个匹配应用"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(onClick = onRefreshAndSort) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新并重排应用列表",
                                tint = scheme.onSurfaceVariant,
                            )
                        }
                        if (ruleState != null && onOpenRuleSheet != null) {
                            Surface(
                                onClick = onOpenRuleSheet,
                                shape = CircleShape,
                                color = scheme.secondaryContainer,
                                contentColor = scheme.onSecondaryContainer,
                                modifier = Modifier.testTag("rule_subscription_pill"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.LibraryBooks,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "词库 v${ruleState.installedVersion}",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    if (ruleState.availableManifest != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(scheme.error),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "apps_search_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = appsState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("搜索应用名或包名（输入 . 显包名）") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (appsState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "清除搜索",
                                )
                            }
                        }
                    },
                    shape = CircleShape,
                )

                AnimatedVisibility(visible = appsState.isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (!appsState.refreshWarning.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.errorContainer,
                        contentColor = scheme.onErrorContainer,
                    ) {
                        Text(
                            text = appsState.refreshWarning,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = appsState.filter == filter,
                            onClick = { onFilterChange(filter) },
                            label = { Text(filter.label) },
                        )
                    }
                }
            }
        }

        if (isUnfiltered) {
            item(key = "apps_sms_card") {
                SmsRulesCompactCard(
                    defaultSmsLabel = appsState.defaultSmsLabel,
                    defaultSmsPackage = appsState.defaultSmsPackage,
                    cloudRuleSummary = appsState.cloudSmsRule,
                    onEditCloudSmsRule = { pkg ->
                        context.startActivity(
                            Intent(context, AppRuleActivity::class.java).apply {
                                putExtra(AppRuleActivity.EXTRA_PACKAGE, pkg)
                                putExtra(AppRuleActivity.EXTRA_LABEL, "云同步短信")
                            },
                        )
                    },
                )
            }
        }

        item(key = "apps_list_header") {
            Text(
                text = if (isUnfiltered) {
                    "应用列表 · 启用后可点右侧微调规则"
                } else {
                    "匹配结果 (${visibleEntries.size})"
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        if (visibleEntries.isEmpty()) {
            item(key = "apps_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            appsState.isInitialLoading -> "正在读取应用列表…"
                            appsState.entries.isEmpty() -> "应用列表获取失败"
                            else -> "没有匹配的应用"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(
                items = visibleEntries,
                key = { it.packageName },
            ) { entry ->
                AppWhitelistItemCard(
                    entry = entry,
                    showPackageName = showPkg,
                    onToggleEnabled = { enabled -> onToggleApp(entry.packageName, enabled) },
                    onOpenRule = {
                        context.startActivity(
                            Intent(context, AppRuleActivity::class.java).apply {
                                putExtra(AppRuleActivity.EXTRA_PACKAGE, entry.packageName)
                                putExtra(AppRuleActivity.EXTRA_LABEL, entry.label)
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SmsRulesCompactCard(
    defaultSmsLabel: String?,
    defaultSmsPackage: String?,
    cloudRuleSummary: io.github.venompool888.fluidcapsule.ui.state.CloudSmsRuleSummary?,
    onEditCloudSmsRule: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val smsName = defaultSmsLabel?.takeIf { it.isNotBlank() } ?: defaultSmsPackage ?: "系统默认短信"

    TonalSectionCard(
        containerColor = scheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Sms,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "默认短信 · $smsName",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "自动提取验证码与验证链接",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (cloudRuleSummary != null) {
            val cloudCustomized = cloudRuleSummary.otpOnly ||
                cloudRuleSummary.rule != io.github.venompool888.fluidcapsule.settings.AppRule()
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onEditCloudSmsRule(cloudRuleSummary.packageName) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudSync,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "云同步短信规则",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = summarizeRuleInline(cloudRuleSummary.rule, cloudRuleSummary.otpOnly),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (cloudCustomized) scheme.primary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "编辑云同步短信规则",
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppWhitelistItemCard(
    entry: AppEntryUi,
    showPackageName: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onOpenRule: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val activeOrCustomized = entry.isEnabled || entry.hasCustomRule
    val subtitle = when {
        showPackageName -> entry.packageName
        activeOrCustomized -> summarizeRuleInline(entry.rule, entry.otpOnly)
        entry.capabilityTags.isNotEmpty() -> entry.capabilityTags.first()
        else -> ""
    }
    val subtitleColor = if (!showPackageName && entry.hasCustomRule) {
        scheme.primary
    } else {
        scheme.onSurfaceVariant
    }

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (activeOrCustomized) {
                            onOpenRule()
                        } else {
                            onToggleEnabled(true)
                        }
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIconBadge(
                    packageName = entry.packageName,
                    label = entry.label,
                    size = 40.dp,
                    cornerRadius = 12.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (activeOrCustomized) {
                IconButton(
                    onClick = onOpenRule,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "配置 ${entry.label} 规则",
                        tint = if (entry.hasCustomRule) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(scheme.outlineVariant.copy(alpha = 0.5f)),
                )
            }

            Switch(
                checked = entry.isEnabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.semantics {
                    contentDescription = if (entry.isEnabled) "移出白名单" else "加入白名单"
                },
            )
        }
    }

    if (activeOrCustomized) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = scheme.surfaceContainerLow,
        ) {
            rowContent()
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            rowContent()
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp, end = 12.dp),
                color = scheme.outlineVariant.copy(alpha = 0.28f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleSubscriptionSheet(
    ruleState: RuleSubscriptionUiState,
    onDismiss: () -> Unit,
    onToggleSubscription: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallAvailable: () -> Unit,
    onRestoreBuiltIn: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    val available = ruleState.availableManifest

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "识别词库与规则订阅",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "只从官方 GitHub 获取已签名的验证码及链接识别词条。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )

            TonalSectionCard(
                containerColor = scheme.surfaceContainerLow,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                SettingSwitchRow(
                    title = "订阅规则更新",
                    summary = "默认开启；进入流体胶囊时检查，有新版本时在应用内提示",
                    checked = ruleState.subscriptionEnabled,
                    onCheckedChange = onToggleSubscription,
                    switchContentDescription = "订阅规则更新",
                )
            }

            TonalSectionCard(
                containerColor = scheme.surfaceContainer,
                contentPadding = PaddingValues(14.dp),
            ) {
                Text(
                    text = "已安装规则：${ruleState.installedVersion} · ${if (ruleState.isBuiltInSource) "内置规则" else "官方 GitHub"}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = ruleState.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (ruleState.isBusy) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                    )
                }
                if (available != null && available.manifest.notes.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Text(
                        text = "版本 v${available.manifest.version} 更新说明",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                    )
                    Text(
                        text = available.manifest.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Button(
                onClick = onCheckUpdates,
                enabled = !ruleState.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("检查规则更新")
            }
            FilledTonalButton(
                onClick = onInstallAvailable,
                enabled = available != null && !ruleState.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (available != null) {
                        "更新到规则 ${available.manifest.version}"
                    } else {
                        "更新规则"
                    },
                )
            }
            OutlinedButton(
                onClick = onRestoreBuiltIn,
                enabled = !ruleState.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("恢复内置规则")
            }
        }
    }
}
