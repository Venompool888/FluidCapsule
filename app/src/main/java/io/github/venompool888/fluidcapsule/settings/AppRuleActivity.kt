package io.github.venompool888.fluidcapsule.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.venompool888.fluidcapsule.ui.components.AppIconBadge
import io.github.venompool888.fluidcapsule.ui.components.ConfirmAlertDialog
import io.github.venompool888.fluidcapsule.ui.components.RuleSandboxCard
import io.github.venompool888.fluidcapsule.ui.components.RuleSummaryChips
import io.github.venompool888.fluidcapsule.ui.components.SectionLabel
import io.github.venompool888.fluidcapsule.ui.components.SettingSwitchRow
import io.github.venompool888.fluidcapsule.ui.components.StatusBadge
import io.github.venompool888.fluidcapsule.ui.components.StatusTone
import io.github.venompool888.fluidcapsule.ui.components.TonalSectionCard
import io.github.venompool888.fluidcapsule.ui.theme.FluidCapsuleTheme
import io.github.venompool888.fluidcapsule.ui.theme.FluidTheme
import io.github.venompool888.fluidcapsule.ui.theme.configureEdgeToEdgeForTheme
import io.github.venompool888.fluidcapsule.ui.theme.isDarkThemeForContext
import io.github.venompool888.fluidcapsule.ui.withFluidThemeMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class AppRuleActivity : ComponentActivity() {
    private lateinit var sourcePackage: String
    private var themeModeState by mutableStateOf(ThemeMode.SYSTEM)
    private var dynamicColorState by mutableStateOf(true)
    private var globalDurationState by mutableIntStateOf(CapsuleDisplayDuration.defaultMinutes)
    private var globalHistoryEnabledState by mutableStateOf(false)
    private var globalShowWhitelistContentState by mutableStateOf(true)
    private var isDefaultSmsState by mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withFluidThemeMode())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourcePackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty().trim()
        if (sourcePackage.isBlank()) {
            finish()
            return
        }

        val isSyncedSms = sourcePackage == packageName
        val rawLabel = intent.getStringExtra(EXTRA_LABEL).orEmpty().trim()
        val displayName = if (isSyncedSms) {
            SYNCED_SMS_LABEL
        } else {
            rawLabel.ifBlank { resolveAppLabel(sourcePackage) ?: sourcePackage }
        }
        title = "$displayName · 专属规则"

        refreshEnvironmentState()
        enableEdgeToEdge()
        configureEdgeToEdgeForTheme(this, isDarkThemeForContext(this, themeModeState))

        setContent {
            FluidCapsuleTheme(
                themeMode = themeModeState,
                dynamicColor = dynamicColorState,
            ) {
                AppRuleScreen(
                    context = this,
                    sourcePackage = sourcePackage,
                    displayName = displayName,
                    isSyncedSms = isSyncedSms,
                    isDefaultSmsApp = isDefaultSmsState,
                    globalDurationMinutes = globalDurationState,
                    globalHistoryEnabled = globalHistoryEnabledState,
                    globalShowWhitelistContent = globalShowWhitelistContentState,
                    onNavigateBack = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshEnvironmentState()
    }

    private fun refreshEnvironmentState() {
        themeModeState = UserSettings.themeMode(this)
        dynamicColorState = UserSettings.dynamicColorEnabled(this)
        globalDurationState = UserSettings.capsuleDisplayDurationMinutes(this)
        globalHistoryEnabledState = UserSettings.notificationHistoryEnabled(this)
        globalShowWhitelistContentState = UserSettings.showWhitelistContent(this)
        isDefaultSmsState = Telephony.Sms.getDefaultSmsPackage(this) == sourcePackage
    }

    private fun resolveAppLabel(packageName: String): String? = runCatching {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_LABEL = "label"
        private const val SYNCED_SMS_LABEL = "云同步短信"

        fun createIntent(
            context: Context,
            packageName: String,
            label: String? = null,
        ): Intent = Intent(context, AppRuleActivity::class.java).apply {
            putExtra(EXTRA_PACKAGE, packageName)
            if (!label.isNullOrBlank()) {
                putExtra(EXTRA_LABEL, label)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppRuleScreen(
    context: Context,
    sourcePackage: String,
    displayName: String,
    isSyncedSms: Boolean,
    isDefaultSmsApp: Boolean,
    globalDurationMinutes: Int,
    globalHistoryEnabled: Boolean,
    globalShowWhitelistContent: Boolean,
    onNavigateBack: () -> Unit,
) {
    val initialRule = remember(sourcePackage) { AppRuleStore.get(context, sourcePackage) }
    val initialOtpOnly = remember(sourcePackage) {
        NotificationWhitelist.isOtpOnly(context, sourcePackage)
    }

    var savedRule by remember { mutableStateOf(initialRule) }
    var savedOtpOnly by remember { mutableStateOf(initialOtpOnly) }

    var ttlMinutes by rememberSaveable { mutableIntStateOf(initialRule.ttlMinutes) }
    var priority by rememberSaveable { mutableIntStateOf(initialRule.priority) }
    var maxBodyChars by rememberSaveable { mutableIntStateOf(initialRule.maxBodyChars) }
    var contentMode by rememberSaveable { mutableStateOf(initialRule.contentMode) }
    var otpOnly by rememberSaveable { mutableStateOf(initialOtpOnly) }
    var recordHistory by rememberSaveable { mutableStateOf(initialRule.recordHistory) }
    var includeKeywords by rememberSaveable { mutableStateOf(initialRule.includeKeywords) }
    var excludeKeywords by rememberSaveable { mutableStateOf(initialRule.excludeKeywords) }
    var sandboxSampleText by rememberSaveable { mutableStateOf("") }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val currentRule = AppRule(
        ttlMinutes = ttlMinutes,
        priority = priority,
        contentMode = contentMode,
        maxBodyChars = maxBodyChars,
        includeKeywords = includeKeywords.trim(),
        excludeKeywords = excludeKeywords.trim(),
        recordHistory = recordHistory,
    )
    val normalizedSavedRule = savedRule.copy(
        includeKeywords = savedRule.includeKeywords.trim(),
        excludeKeywords = savedRule.excludeKeywords.trim(),
    )
    val isDirty = currentRule != normalizedSavedRule || otpOnly != savedOtpOnly

    fun attemptNavigateBack() {
        if (isDirty) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    fun saveRule() {
        AppRuleStore.set(context, sourcePackage, currentRule)
        NotificationWhitelist.setOtpOnly(context, sourcePackage, otpOnly)
        val persistedRule = AppRuleStore.get(context, sourcePackage)
        val persistedOtpOnly = NotificationWhitelist.isOtpOnly(context, sourcePackage)
        savedRule = persistedRule
        savedOtpOnly = persistedOtpOnly
        includeKeywords = persistedRule.includeKeywords
        excludeKeywords = persistedRule.excludeKeywords
        coroutineScope.launch {
            snackbarHostState.showSnackbar("规则已保存，下一条通知开始生效")
        }
    }

    fun resetToDefault() {
        AppRuleStore.reset(context, sourcePackage)
        NotificationWhitelist.setOtpOnly(context, sourcePackage, false)
        val defaultRule = AppRuleStore.get(context, sourcePackage)
        savedRule = defaultRule
        savedOtpOnly = false
        ttlMinutes = defaultRule.ttlMinutes
        priority = defaultRule.priority
        maxBodyChars = defaultRule.maxBodyChars
        contentMode = defaultRule.contentMode
        otpOnly = false
        recordHistory = defaultRule.recordHistory
        includeKeywords = defaultRule.includeKeywords
        excludeKeywords = defaultRule.excludeKeywords
        coroutineScope.launch {
            snackbarHostState.showSnackbar("已恢复默认规则")
        }
    }

    BackHandler(enabled = isDirty) {
        showDiscardDialog = true
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = ::attemptNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回，专属规则",
                        )
                    }
                },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "专属规则 · $displayName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = sourcePackage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (isDirty) {
                        StatusBadge(
                            text = "未保存",
                            tone = StatusTone.WARNING,
                            leadingDot = true,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showResetDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            Text(
                                text = "恢复继承全局设置",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(
                            onClick = ::saveRule,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            Text(
                                text = "保存专属规则",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val scheme = MaterialTheme.colorScheme
        val semantic = FluidTheme.semanticColors

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TonalSectionCard(containerColor = scheme.surfaceContainerLow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIconBadge(
                        packageName = sourcePackage,
                        label = displayName,
                        size = 48.dp,
                        cornerRadius = 15.dp,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isSyncedSms) {
                                StatusBadge(text = "云同步通道", tone = StatusTone.PRIMARY)
                            } else if (isDefaultSmsApp) {
                                StatusBadge(text = "默认短信", tone = StatusTone.SECONDARY)
                            }
                        }
                        Text(
                            text = if (isSyncedSms) {
                                "控制通过「短信云同步」接收并弹出的云同步短信胶囊行为（停留时间、优先级与正文隐私）。"
                            } else {
                                "这些设置仅影响此应用；选择「继承」或「跟随全局」时将使用全局设置。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                RuleSummaryChips(rule = currentRule, otpOnly = otpOnly)
            }

            if (!globalHistoryEnabled) {
                ContextBanner(
                    icon = Icons.Rounded.Info,
                    title = "全局「记录新通知」当前已暂停",
                    message = "即使此应用开启「写入本地通知历史」，新通知目前也不会写入历史记录；请先在历史管理或设置中开启全局记录。",
                    containerColor = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                )
            }

            TonalSectionCard(containerColor = scheme.surfaceContainerLow) {
                SectionLabel(
                    title = "胶囊停留时间",
                    subtitle = "设置单条胶囊的保留时长；0 表示继承全局设置",
                    trailing = {
                        StatusBadge(
                            text = if (ttlMinutes == 0) {
                                "继承全局（$globalDurationMinutes 分钟）"
                            } else {
                                "$ttlMinutes 分钟"
                            },
                            tone = if (ttlMinutes > 0) StatusTone.PRIMARY else StatusTone.NEUTRAL,
                        )
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = { ttlMinutes = (ttlMinutes - 1).coerceAtLeast(0) },
                        enabled = ttlMinutes > 0,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "减少胶囊停留时间",
                        )
                    }
                    Slider(
                        value = ttlMinutes.toFloat(),
                        onValueChange = { ttlMinutes = it.roundToInt().coerceIn(0, 30) },
                        valueRange = 0f..30f,
                        steps = 29,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(
                        onClick = { ttlMinutes = (ttlMinutes + 1).coerceAtMost(30) },
                        enabled = ttlMinutes < 30,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "增加胶囊停留时间",
                        )
                    }
                }
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val ttlPresets = listOf(
                        0 to "继承全局",
                        1 to "1 分钟",
                        5 to "5 分钟",
                        15 to "15 分钟",
                        30 to "30 分钟",
                    )
                    ttlPresets.forEach { (minutes, label) ->
                        val selected = ttlMinutes == minutes
                        FilterChip(
                            selected = selected,
                            onClick = { ttlMinutes = minutes },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                            label = { Text(label) },
                        )
                    }
                }
                AnimatedVisibility(visible = ttlMinutes > 0 && ttlMinutes > globalDurationMinutes) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        ContextBanner(
                            icon = Icons.Rounded.WarningAmber,
                            title = "受全局展示时长上限限制",
                            message = "当前全局单条胶囊显示时长为 $globalDurationMinutes 分钟。胶囊队列的展示窗口上限由全局时长封顶，此应用实际展示达到 $globalDurationMinutes 分钟后仍会先收起。",
                            containerColor = semantic.warningContainer,
                            contentColor = semantic.onWarningContainer,
                        )
                    }
                }
            }

            TonalSectionCard(containerColor = scheme.surfaceContainerLow) {
                SectionLabel(
                    title = "队列优先级",
                    subtitle = "多条胶囊同时到达时影响排队与抢占权重",
                )
                Spacer(modifier = Modifier.height(12.dp))
                val priorityOptions = listOf(
                    -1 to "低",
                    0 to "普通",
                    1 to "高",
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    priorityOptions.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = priority == value,
                            onClick = { priority = value },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = priorityOptions.size,
                            ),
                        ) {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            TonalSectionCard(containerColor = scheme.surfaceContainerLow) {
                SectionLabel(
                    title = "正文最大长度",
                    subtitle = "控制截断后展示在胶囊正文中的最大字符数",
                    trailing = {
                        StatusBadge(
                            text = "$maxBodyChars 字",
                            tone = if (maxBodyChars == AppRule.DEFAULT_MAX_BODY_CHARS) {
                                StatusTone.NEUTRAL
                            } else {
                                StatusTone.PRIMARY
                            },
                        )
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = { maxBodyChars = (maxBodyChars - 10).coerceAtLeast(20) },
                        enabled = maxBodyChars > 20,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "减少正文最大长度",
                        )
                    }
                    Slider(
                        value = maxBodyChars.toFloat(),
                        onValueChange = { maxBodyChars = it.roundToInt().coerceIn(20, 500) },
                        valueRange = 20f..500f,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(
                        onClick = { maxBodyChars = (maxBodyChars + 10).coerceAtMost(500) },
                        enabled = maxBodyChars < 500,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "增加正文最大长度",
                        )
                    }
                }
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(80, 220, 500).forEach { preset ->
                        val selected = maxBodyChars == preset
                        FilterChip(
                            selected = selected,
                            onClick = { maxBodyChars = preset },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                            label = { Text("$preset 字") },
                        )
                    }
                }
            }

            TonalSectionCard(containerColor = scheme.surfaceContainerLow) {
                SectionLabel(
                    title = "正文隐私",
                    subtitle = if (contentMode == AppRule.CONTENT_INHERIT) {
                        "当前跟随全局「白名单显示正文」（${if (globalShowWhitelistContent) "显示正文" else "隐藏正文"}）"
                    } else {
                        "已为该应用单独指定锁屏与胶囊正文可见性"
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                val contentModes = listOf(
                    AppRule.CONTENT_INHERIT to "跟随全局",
                    AppRule.CONTENT_SHOW to "始终显示",
                    AppRule.CONTENT_HIDE to "始终隐藏",
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    contentModes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = contentMode == mode,
                            onClick = { contentMode = mode },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = contentModes.size,
                            ),
                        ) {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = scheme.outlineVariant.copy(alpha = 0.5f),
                )
                SettingSwitchRow(
                    title = "仅在识别到验证码时展示",
                    summary = "开启后，该应用的普通通知不会弹出胶囊（仍可匹配验证链接）",
                    checked = otpOnly,
                    onCheckedChange = { otpOnly = it },
                )
                SettingSwitchRow(
                    title = "写入本地通知历史",
                    summary = if (globalHistoryEnabled) {
                        "关闭后，此应用的新通知不会记录到本地历史数据库"
                    } else {
                        "全局「记录新通知」当前已暂停，暂不会写入历史数据库"
                    },
                    checked = recordHistory,
                    onCheckedChange = { recordHistory = it },
                )
            }

            TonalSectionCard(containerColor = scheme.surfaceContainerLow) {
                SectionLabel(
                    title = "关键词过滤",
                    subtitle = "支持英文逗号、中文逗号或换行分隔；大小写不敏感",
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = scheme.primary,
                        )
                        Text(
                            text = "关键词过滤先于验证码解析生效（排除关键词优先于包含关键词）。若通知正文命中排除关键词，即使包含验证码也会被跳过。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = includeKeywords,
                    onValueChange = { includeKeywords = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("包含关键词") },
                    placeholder = { Text("留空表示不限制，例如：验证码, 登录") },
                    supportingText = { Text("填写后，仅包含任意关键词的通知才会继续处理") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (includeKeywords.isNotEmpty()) {
                            IconButton(onClick = { includeKeywords = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "清空包含关键词",
                                )
                            }
                        }
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = excludeKeywords,
                    onValueChange = { excludeKeywords = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("排除关键词") },
                    placeholder = { Text("例如：广告, 促销, 营销") },
                    supportingText = { Text("命中任意排除关键词将直接拦截，优先于包含规则与验证码解析") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (excludeKeywords.isNotEmpty()) {
                            IconButton(onClick = { excludeKeywords = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "清空排除关键词",
                                )
                            }
                        }
                    },
                )
            }

            RuleSandboxCard(
                rule = currentRule,
                otpOnly = otpOnly,
                sampleText = sandboxSampleText,
                onSampleTextChange = { sandboxSampleText = it },
            )

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    if (showDiscardDialog) {
        ConfirmAlertDialog(
            title = "放弃未保存的规则修改？",
            message = "当前修改尚未保存，返回将丢失这些规则调整。",
            confirmLabel = "放弃并返回",
            dismissLabel = "继续编辑",
            isDestructive = true,
            onConfirm = {
                showDiscardDialog = false
                onNavigateBack()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }

    if (showResetDialog) {
        ConfirmAlertDialog(
            title = "恢复继承全局设置？",
            message = "将清除「$displayName」的所有自定义规则与仅验证码限制，恢复为继承全局设置。",
            confirmLabel = "恢复默认",
            dismissLabel = "取消",
            isDestructive = true,
            onConfirm = {
                showResetDialog = false
                resetToDefault()
            },
            onDismiss = { showResetDialog = false },
        )
    }
}

@Composable
private fun ContextBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
