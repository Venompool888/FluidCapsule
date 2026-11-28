package io.github.venompool888.fluidcapsule.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import io.github.venompool888.fluidcapsule.history.HistoryTransferAction
import io.github.venompool888.fluidcapsule.history.HistoryTransferState
import io.github.venompool888.fluidcapsule.history.NotificationHistoryAppGroup
import io.github.venompool888.fluidcapsule.history.NotificationHistoryEntry
import io.github.venompool888.fluidcapsule.settings.AppRuleActivity
import io.github.venompool888.fluidcapsule.settings.HistoryRetentionPolicy
import io.github.venompool888.fluidcapsule.settings.HistoryRetentionUnit
import io.github.venompool888.fluidcapsule.ui.components.AppIconBadge
import io.github.venompool888.fluidcapsule.ui.components.ConfirmAlertDialog
import io.github.venompool888.fluidcapsule.ui.components.RelativeTimeFormatter
import io.github.venompool888.fluidcapsule.ui.components.SettingSwitchRow
import io.github.venompool888.fluidcapsule.ui.components.StatusBadge
import io.github.venompool888.fluidcapsule.ui.components.StatusTone
import io.github.venompool888.fluidcapsule.ui.components.TonalSectionCard
import io.github.venompool888.fluidcapsule.ui.state.HistoryDecisionFilter
import io.github.venompool888.fluidcapsule.ui.state.HistorySortMode
import io.github.venompool888.fluidcapsule.ui.state.HistoryUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyState: HistoryUiState,
    transferState: HistoryTransferState,
    bottomContentPadding: Dp,
    onSearchQueryChange: (String) -> Unit,
    onDecisionFilterChange: (HistoryDecisionFilter) -> Unit,
    onSortModeChange: (HistorySortMode) -> Unit,
    onTogglePrivacyBlur: () -> Unit,
    onToggleAppGroup: (String) -> Unit,
    onTogglePackageRecordHistory: (String, Boolean) -> Unit,
    onCopyOtp: (String) -> Unit,
    onCopyBody: (String) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onDeletePackage: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    onToggleRecording: (Boolean) -> Unit,
    onChangeRetentionPolicy: (HistoryRetentionPolicy) -> Unit,
    onExportHistory: () -> Unit,
    onImportHistory: () -> Unit,
    onBlockedActionMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showManageSheet by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<NotificationHistoryEntry?>(null) }
    var pendingDeleteEntry by remember { mutableStateOf<NotificationHistoryEntry?>(null) }
    var pendingDeleteGroup by remember { mutableStateOf<NotificationHistoryAppGroup?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

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
                        text = "通知历史",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                    )
                    Text(
                        text = "${if (historyState.recordingEnabled) "● 正在记录" else "○ 记录已关闭"} · 共 ${historyState.totalCount} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onTogglePrivacyBlur,
                    modifier = Modifier.semantics {
                        contentDescription = if (historyState.privacyBlurEnabled) "关闭隐私模糊" else "开启隐私模糊"
                    },
                ) {
                    Icon(
                        imageVector = if (historyState.privacyBlurEnabled) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = if (historyState.privacyBlurEnabled) "隐私模糊已开启" else "隐私模糊已关闭",
                        tint = if (historyState.privacyBlurEnabled) scheme.primary else scheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = { showManageSheet = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .testTag("history_manage_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SettingsBackupRestore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("管理与备份", style = MaterialTheme.typography.labelMedium)
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = historyState.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索应用、标题或通知正文") },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (historyState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(imageVector = Icons.Rounded.Clear, contentDescription = "清除搜索")
                        }
                    }
                },
                shape = CircleShape,
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = HistorySortMode.entries
                modes.forEachIndexed { index, mode ->
                    val label = if (mode == HistorySortMode.APP_COUNT) {
                        val countSuffix = if (historyState.sortMode == HistorySortMode.APP_COUNT) {
                            "(${historyState.appGroups.size})"
                        } else {
                            ""
                        }
                        "按软件$countSuffix"
                    } else {
                        mode.label
                    }
                    SegmentedButton(
                        selected = historyState.sortMode == mode,
                        onClick = { onSortModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryDecisionFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = historyState.decisionFilter == filter,
                        onClick = { onDecisionFilterChange(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("history_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = bottomContentPadding + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (historyState.privacyBlurEnabled) {
                item(key = "privacy_blur_banner") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer,
                    ) {
                        Text(
                            text = "已开启隐私模糊模式：列表正文已脱敏，点击条目或关闭顶部锁图标查看原文。",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            if (historyState.sortMode == HistorySortMode.TIME) {
                if (historyState.timelineEntries.isEmpty()) {
                    item(key = "history_empty_timeline") {
                        HistoryEmptyPlaceholder(hasTotal = historyState.totalCount > 0L)
                    }
                } else {
                    items(
                        items = historyState.timelineEntries,
                        key = { "entry:${it.id}" },
                    ) { entry ->
                        HistoryEntryCard(
                            entry = entry,
                            otpCode = historyState.entryOtps[entry.id],
                            blurred = historyState.privacyBlurEnabled,
                            nested = false,
                            onClick = { selectedEntry = entry },
                            onCopyOtp = { code -> onCopyOtp(code) },
                        )
                    }
                }
            } else {
                if (historyState.appGroups.isEmpty()) {
                    item(key = "history_empty_groups") {
                        HistoryEmptyPlaceholder(hasTotal = historyState.totalCount > 0L)
                    }
                } else {
                    items(
                        items = historyState.appGroups,
                        key = { "group:${it.sourcePackage}" },
                    ) { group ->
                        val expanded = group.sourcePackage in historyState.expandedPackages
                        val entries = historyState.expandedGroupEntries[group.sourcePackage].orEmpty()
                        val recordingPackage = historyState.packageRecordHistory[group.sourcePackage] ?: true
                        AppGroupHistoryCard(
                            group = group,
                            expanded = expanded,
                            entries = entries,
                            entryOtps = historyState.entryOtps,
                            blurred = historyState.privacyBlurEnabled,
                            recordingPackage = recordingPackage,
                            onToggleExpand = { onToggleAppGroup(group.sourcePackage) },
                            onEditRule = {
                                context.startActivity(
                                    Intent(context, AppRuleActivity::class.java).apply {
                                        putExtra(AppRuleActivity.EXTRA_PACKAGE, group.sourcePackage)
                                        putExtra(AppRuleActivity.EXTRA_LABEL, group.sourceLabel)
                                    },
                                )
                            },
                            onToggleRecordPackage = {
                                onTogglePackageRecordHistory(group.sourcePackage, !recordingPackage)
                            },
                            onRequestDeletePackage = {
                                if (transferState != HistoryTransferState.Idle) {
                                    onBlockedActionMessage("通知历史正在处理，请稍后重试")
                                } else {
                                    pendingDeleteGroup = group
                                }
                            },
                            onSelectEntry = { selectedEntry = it },
                            onCopyOtp = onCopyOtp,
                        )
                    }
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        HistoryDetailSheet(
            entry = entry,
            otpCode = historyState.entryOtps[entry.id],
            blurred = historyState.privacyBlurEnabled,
            onDismiss = { selectedEntry = null },
            onCopyOtp = { code -> onCopyOtp(code) },
            onCopyBody = { text -> onCopyBody(text) },
            onEditAppRule = {
                selectedEntry = null
                context.startActivity(
                    Intent(context, AppRuleActivity::class.java).apply {
                        putExtra(AppRuleActivity.EXTRA_PACKAGE, entry.sourcePackage)
                        putExtra(AppRuleActivity.EXTRA_LABEL, entry.sourceLabel)
                    },
                )
            },
            onRequestDelete = {
                if (transferState != HistoryTransferState.Idle) {
                    onBlockedActionMessage("通知历史正在处理，请稍后重试")
                } else {
                    pendingDeleteEntry = entry
                }
            },
        )
    }

    if (showManageSheet) {
        HistoryManageSheet(
            historyState = historyState,
            transferState = transferState,
            onDismiss = { showManageSheet = false },
            onToggleRecording = onToggleRecording,
            onChangeRetentionPolicy = onChangeRetentionPolicy,
            onExportHistory = onExportHistory,
            onImportHistory = onImportHistory,
            onRequestClearAll = {
                if (transferState != HistoryTransferState.Idle) {
                    onBlockedActionMessage("通知历史正在处理，请稍后重试")
                } else {
                    showClearAllConfirm = true
                }
            },
        )
    }

    pendingDeleteEntry?.let { entry ->
        ConfirmAlertDialog(
            title = "删除这条通知？",
            message = "${entry.sourceLabel} · ${entry.title.ifBlank { "无标题" }}",
            confirmLabel = "删除",
            isDestructive = true,
            onConfirm = {
                val id = entry.id
                pendingDeleteEntry = null
                selectedEntry = null
                onDeleteEntry(id)
            },
            onDismiss = { pendingDeleteEntry = null },
        )
    }

    pendingDeleteGroup?.let { group ->
        ConfirmAlertDialog(
            title = "删除 ${group.sourceLabel.ifBlank { group.sourcePackage }} 的历史？",
            message = "只删除本机历史，不会修改该应用的白名单或专属规则。",
            confirmLabel = "删除",
            isDestructive = true,
            onConfirm = {
                val pkg = group.sourcePackage
                pendingDeleteGroup = null
                onDeletePackage(pkg)
            },
            onDismiss = { pendingDeleteGroup = null },
        )
    }

    if (showClearAllConfirm) {
        ConfirmAlertDialog(
            title = "清空通知历史？",
            message = "这会永久删除本机保存的全部通知正文和处理结果。",
            confirmLabel = "清空",
            isDestructive = true,
            onConfirm = {
                showClearAllConfirm = false
                onClearAllHistory()
            },
            onDismiss = { showClearAllConfirm = false },
        )
    }
}

@Composable
private fun HistoryEmptyPlaceholder(hasTotal: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (hasTotal) {
                "没有符合当前筛选条件的历史记录"
            } else {
                "还没有通知历史\n打开右上「管理与备份」中的记录开关后，新通知会保存在这里"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryEntryCard(
    entry: NotificationHistoryEntry,
    otpCode: String?,
    blurred: Boolean,
    nested: Boolean,
    onClick: () -> Unit,
    onCopyOtp: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (decisionLabel, decisionTone) = historyDecisionPresentation(entry.decision)
    val titleText = entry.title.takeIf(String::isNotBlank) ?: entry.sourceLabel.ifBlank { "新通知" }
    val bodyText = entry.primaryText.takeIf(String::isNotBlank)
        ?: entry.combinedText.takeIf(String::isNotBlank)
        ?: "通知没有可显示的正文"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(if (nested) 16.dp else 20.dp),
        color = if (nested) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (!nested) {
                AppIconBadge(
                    packageName = entry.sourcePackage,
                    label = entry.sourceLabel,
                    size = 42.dp,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.sourceLabel.ifBlank { entry.sourcePackage },
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = RelativeTimeFormatter.format(entry.postedAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }

                Column(
                    modifier = if (blurred) Modifier.blur(8.dp) else Modifier,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = bodyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge(text = decisionLabel, tone = decisionTone)
                    if (otpCode != null && !blurred) {
                        AssistChip(
                            onClick = { onCopyOtp(otpCode) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                            },
                            label = { Text("复制 $otpCode") },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppGroupHistoryCard(
    group: NotificationHistoryAppGroup,
    expanded: Boolean,
    entries: List<NotificationHistoryEntry>,
    entryOtps: Map<Long, String>,
    blurred: Boolean,
    recordingPackage: Boolean,
    onToggleExpand: () -> Unit,
    onEditRule: () -> Unit,
    onToggleRecordPackage: () -> Unit,
    onRequestDeletePackage: () -> Unit,
    onSelectEntry: (NotificationHistoryEntry) -> Unit,
    onCopyOtp: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    TonalSectionCard(
        containerColor = if (expanded) scheme.primaryContainer.copy(alpha = 0.25f) else scheme.surfaceContainerLow,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggleExpand)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconBadge(
                packageName = group.sourcePackage,
                label = group.sourceLabel,
                size = 46.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = group.sourceLabel.ifBlank { group.sourcePackage },
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(text = "${group.notificationCount} 条通知", tone = StatusTone.PRIMARY)
                }
                Text(
                    text = "最近 ${RelativeTimeFormatter.format(group.latestCapturedAtMillis)} · ${group.sourcePackage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = scheme.primary,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilledTonalButton(
                        onClick = onEditRule,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("编辑专属规则", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onToggleRecordPackage,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = if (recordingPackage) "停止记录此应用" else "恢复记录此应用",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    TextButton(
                        onClick = onRequestDeletePackage,
                        colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除该应用历史", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (entries.isEmpty()) {
                    Text(
                        text = "当前筛选下暂无该应用通知",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    entries.forEach { entry ->
                        HistoryEntryCard(
                            entry = entry,
                            otpCode = entryOtps[entry.id],
                            blurred = blurred,
                            nested = true,
                            onClick = { onSelectEntry(entry) },
                            onCopyOtp = onCopyOtp,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDetailSheet(
    entry: NotificationHistoryEntry,
    otpCode: String?,
    blurred: Boolean,
    onDismiss: () -> Unit,
    onCopyOtp: (String) -> Unit,
    onCopyBody: (String) -> Unit,
    onEditAppRule: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    val (decisionLabel, decisionTone) = historyDecisionPresentation(entry.decision)
    var revealBlurred by remember(entry.id) { mutableStateOf(!blurred) }
    val fullBody = entry.combinedText.takeIf(String::isNotBlank)
        ?: entry.primaryText.takeIf(String::isNotBlank)
        ?: "通知没有可显示的正文"
    val fullTime = remember(entry.postedAtMillis) {
        SimpleDateFormat("yyyy年M月d日 HH:mm:ss", Locale.getDefault()).format(Date(entry.postedAtMillis))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIconBadge(
                    packageName = entry.sourcePackage,
                    label = entry.sourceLabel,
                    size = 46.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.sourceLabel.ifBlank { entry.sourcePackage },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "$fullTime (${RelativeTimeFormatter.format(entry.postedAtMillis)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = entry.sourcePackage,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusBadge(text = decisionLabel, tone = decisionTone)
                if (entry.decisionDetail.isNotBlank()) {
                    Text(
                        text = entry.decisionDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            TonalSectionCard(containerColor = scheme.surfaceContainerLow) {
                if (blurred && !revealBlurred) {
                    Text(
                        text = "当前处于隐私模糊保护状态",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { revealBlurred = true }) {
                        Text("点击显示此条通知原文")
                    }
                } else {
                    if (entry.title.isNotBlank()) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = fullBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            if (otpCode != null && (!blurred || revealBlurred)) {
                Button(
                    onClick = { onCopyOtp(otpCode) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("复制验证码 $otpCode")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { onCopyBody(fullBody) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("复制正文")
                }
                FilledTonalButton(
                    onClick = onEditAppRule,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("编辑该应用规则")
                }
            }

            Button(
                onClick = onRequestDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.errorContainer,
                    contentColor = scheme.onErrorContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("删除此条记录")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HistoryManageSheet(
    historyState: HistoryUiState,
    transferState: HistoryTransferState,
    onDismiss: () -> Unit,
    onToggleRecording: (Boolean) -> Unit,
    onChangeRetentionPolicy: (HistoryRetentionPolicy) -> Unit,
    onExportHistory: () -> Unit,
    onImportHistory: () -> Unit,
    onRequestClearAll: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    val transferBusy = transferState != HistoryTransferState.Idle

    var showCustomRetention by remember { mutableStateOf(false) }
    var customUnit by remember(historyState.retentionPolicy) {
        mutableStateOf(historyState.retentionPolicy.unit)
    }
    var customValue by remember(historyState.retentionPolicy) {
        mutableIntStateOf(historyState.retentionPolicy.value.takeIf { it > 0 } ?: 30)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "通知历史管理与备份",
                style = MaterialTheme.typography.titleLarge,
            )

            if (transferBusy) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val statusText = when (transferState) {
                            is HistoryTransferState.Working -> transferState.message
                            is HistoryTransferState.Picking -> "正在选择${if (transferState.action == HistoryTransferAction.EXPORT) "保存位置" else "备份文件"}…"
                            else -> "正在处理通知历史…"
                        }
                        Text(text = statusText, style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            TonalSectionCard(
                containerColor = scheme.surfaceContainerLow,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                SettingSwitchRow(
                    title = "记录新通知",
                    summary = "关闭后停止写入，已有历史不会被删除",
                    checked = historyState.recordingEnabled,
                    onCheckedChange = onToggleRecording,
                    switchContentDescription = "记录新通知",
                )
            }

            TonalSectionCard(
                containerColor = scheme.surfaceContainerLow,
                contentPadding = PaddingValues(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "自动保留时间",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    StatusBadge(
                        text = historyState.retentionPolicy.chineseLabel(),
                        tone = StatusTone.PRIMARY,
                    )
                }
                Text(
                    text = "按自然日、自然月或自然年自动清理；选择永久后不会自动删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )

                val presets = listOf(
                    HistoryRetentionPolicy(7, HistoryRetentionUnit.DAYS),
                    HistoryRetentionPolicy(30, HistoryRetentionUnit.DAYS),
                    HistoryRetentionPolicy(3, HistoryRetentionUnit.MONTHS),
                    HistoryRetentionPolicy(1, HistoryRetentionUnit.YEARS),
                    HistoryRetentionPolicy(0, HistoryRetentionUnit.FOREVER),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = !showCustomRetention && historyState.retentionPolicy == preset,
                            enabled = !transferBusy,
                            onClick = {
                                showCustomRetention = false
                                onChangeRetentionPolicy(preset)
                            },
                            label = { Text(preset.chineseLabel()) },
                        )
                    }
                    FilterChip(
                        selected = showCustomRetention,
                        enabled = !transferBusy,
                        onClick = { showCustomRetention = !showCustomRetention },
                        label = { Text("自定义") },
                    )
                }

                AnimatedVisibility(visible = showCustomRetention) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val unitOptions = listOf(
                            HistoryRetentionUnit.DAYS to "天",
                            HistoryRetentionUnit.MONTHS to "月",
                            HistoryRetentionUnit.YEARS to "年",
                            HistoryRetentionUnit.FOREVER to "永久",
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            unitOptions.forEachIndexed { index, (unit, label) ->
                                SegmentedButton(
                                    selected = customUnit == unit,
                                    enabled = !transferBusy,
                                    onClick = { customUnit = unit },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = unitOptions.size,
                                    ),
                                    label = { Text(label) },
                                )
                            }
                        }

                        if (customUnit != HistoryRetentionUnit.FOREVER) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilledTonalIconButton(
                                    enabled = !transferBusy && customValue > HistoryRetentionPolicy.MIN_VALUE,
                                    onClick = {
                                        customValue = (customValue - 1).coerceAtLeast(HistoryRetentionPolicy.MIN_VALUE)
                                    },
                                ) {
                                    Icon(Icons.Rounded.Remove, contentDescription = "减少保留数量")
                                }
                                Text(
                                    text = HistoryRetentionPolicy(customValue, customUnit).chineseLabel(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                FilledTonalIconButton(
                                    enabled = !transferBusy && customValue < HistoryRetentionPolicy.MAX_VALUE,
                                    onClick = {
                                        customValue = (customValue + 1).coerceAtMost(HistoryRetentionPolicy.MAX_VALUE)
                                    },
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "增加保留数量")
                                }
                            }
                        }

                        FilledTonalButton(
                            enabled = !transferBusy,
                            onClick = {
                                val nextPolicy = if (customUnit == HistoryRetentionUnit.FOREVER) {
                                    HistoryRetentionPolicy(0, HistoryRetentionUnit.FOREVER)
                                } else {
                                    HistoryRetentionPolicy(customValue, customUnit)
                                }
                                onChangeRetentionPolicy(nextPolicy)
                                showCustomRetention = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("应用自定义保留时间")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onExportHistory,
                    enabled = !transferBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("导出历史")
                }
                FilledTonalButton(
                    onClick = onImportHistory,
                    enabled = !transferBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("导入历史")
                }
            }

            Button(
                onClick = onRequestClearAll,
                enabled = !transferBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.errorContainer,
                    contentColor = scheme.onErrorContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("清空全部通知历史")
            }
        }
    }
}

private fun historyDecisionPresentation(decision: String): Pair<String, StatusTone> = when (decision) {
    "PUBLISHED" -> "已弹出胶囊" to StatusTone.SUCCESS
    "FILTERED" -> "已被规则过滤" to StatusTone.WARNING
    "SKIPPED" -> "仅记录 · 未触发胶囊" to StatusTone.NEUTRAL
    "CAPTURED" -> "正在处理" to StatusTone.SECONDARY
    else -> "处理结果未知" to StatusTone.NEUTRAL
}
