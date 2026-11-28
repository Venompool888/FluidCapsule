package io.github.venompool888.fluidcapsule.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import io.github.venompool888.fluidcapsule.ui.components.RuleSummaryChips
import io.github.venompool888.fluidcapsule.ui.components.SettingSwitchRow
import io.github.venompool888.fluidcapsule.ui.components.StatusBadge
import io.github.venompool888.fluidcapsule.ui.components.StatusTone
import io.github.venompool888.fluidcapsule.ui.components.TonalSectionCard
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val visibleEntries = appsState.visibleEntries

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
            navigationIcon = {
                if (showBackButton) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                }
            },
            title = {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                    )
                    Text(
                        text = if (appsState.isInitialLoading && appsState.entries.isEmpty()) {
                            "正在加载应用…"
                        } else {
                            "已选 ${appsState.selectedCount} 个 · 显示 ${visibleEntries.size} 个"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                if (ruleState != null && onOpenRuleSheet != null) {
                    Surface(
                        onClick = onOpenRuleSheet,
                        shape = CircleShape,
                        color = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("rule_subscription_pill"),
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
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = appsState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("搜索应用名或包名") },
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
                FilledTonalIconButton(
                    onClick = onRefreshAndSort,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "刷新并重排应用列表",
                    )
                }
            }

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

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("apps_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = bottomContentPadding + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "default_sms_banner") {
                DefaultSmsBanner(
                    defaultSmsLabel = appsState.defaultSmsLabel,
                    defaultSmsPackage = appsState.defaultSmsPackage,
                )
            }

            appsState.cloudSmsRule?.let { cloudRule ->
                item(key = "cloud_sms_rule_card") {
                    CloudSmsRuleCard(
                        rule = cloudRule.rule,
                        otpOnly = cloudRule.otpOnly,
                        onClick = {
                            context.startActivity(
                                Intent(context, AppRuleActivity::class.java).apply {
                                    putExtra(AppRuleActivity.EXTRA_PACKAGE, cloudRule.packageName)
                                    putExtra(AppRuleActivity.EXTRA_LABEL, "云同步短信")
                                },
                            )
                        },
                    )
                }
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
}

@Composable
private fun DefaultSmsBanner(
    defaultSmsLabel: String?,
    defaultSmsPackage: String?,
) {
    val scheme = MaterialTheme.colorScheme
    val smsName = defaultSmsLabel?.takeIf { it.isNotBlank() } ?: defaultSmsPackage ?: "系统默认短信"
    TonalSectionCard(
        containerColor = scheme.secondaryContainer.copy(alpha = 0.55f),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Sms,
                contentDescription = null,
                tint = scheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "默认短信（$smsName）默认自动识别验证码与验证链接",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSecondaryContainer,
                )
                Text(
                    text = "无需勾选即可提取短信验证码；若还希望镜像普通短信，可在下方打开其白名单开关。勾选应用不会自动跳位，点右上刷新按钮可重新置顶排序。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSecondaryContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun CloudSmsRuleCard(
    rule: io.github.venompool888.fluidcapsule.settings.AppRule,
    otpOnly: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = scheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.primary,
                    contentColor = scheme.onPrimary,
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
                        text = "云同步短信专属规则",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = "控制远程手机同步短信的停留时长、优先级与正文隐私",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "编辑云同步短信规则",
                    tint = scheme.primary,
                )
            }
            RuleSummaryChips(rule = rule, otpOnly = otpOnly)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppWhitelistItemCard(
    entry: AppEntryUi,
    onToggleEnabled: (Boolean) -> Unit,
    onOpenRule: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    TonalSectionCard(
        containerColor = scheme.surfaceContainerLow,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggleEnabled(!entry.isEnabled) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconBadge(
                packageName = entry.packageName,
                label = entry.label,
                size = 44.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.capabilityTags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        entry.capabilityTags.forEach { tag ->
                            StatusBadge(
                                text = tag,
                                tone = when (tag) {
                                    "默认短信" -> StatusTone.SUCCESS
                                    "邮箱" -> StatusTone.TERTIARY
                                    else -> StatusTone.SECONDARY
                                },
                            )
                        }
                    }
                }
            }
            Switch(
                checked = entry.isEnabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.semantics {
                    contentDescription = if (entry.isEnabled) "移出白名单" else "加入白名单"
                },
            )
        }

        AnimatedVisibility(
            visible = entry.isEnabled || entry.hasCustomRule,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                onClick = onOpenRule,
                shape = RoundedCornerShape(14.dp),
                color = scheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${entry.label} · 专属规则",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "配置规则",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.primary,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    RuleSummaryChips(rule = entry.rule, otpOnly = entry.otpOnly)
                }
            }
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
