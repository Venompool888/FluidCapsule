package io.github.venompool888.fluidcapsule.sync

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.venompool888.fluidcapsule.settings.AppRule
import io.github.venompool888.fluidcapsule.settings.AppRuleActivity
import io.github.venompool888.fluidcapsule.settings.AppRuleStore
import io.github.venompool888.fluidcapsule.settings.NotificationWhitelist
import io.github.venompool888.fluidcapsule.settings.ThemeMode
import io.github.venompool888.fluidcapsule.settings.UserSettings
import io.github.venompool888.fluidcapsule.ui.components.ConfirmAlertDialog
import io.github.venompool888.fluidcapsule.ui.components.RelativeTimeFormatter
import io.github.venompool888.fluidcapsule.ui.components.RuleSummaryChips
import io.github.venompool888.fluidcapsule.ui.components.SectionLabel
import io.github.venompool888.fluidcapsule.ui.components.StatusBadge
import io.github.venompool888.fluidcapsule.ui.components.StatusTone
import io.github.venompool888.fluidcapsule.ui.components.TonalSectionCard
import io.github.venompool888.fluidcapsule.ui.theme.FluidCapsuleTheme
import io.github.venompool888.fluidcapsule.ui.theme.configureEdgeToEdgeForTheme
import io.github.venompool888.fluidcapsule.ui.theme.isDarkThemeForContext
import io.github.venompool888.fluidcapsule.ui.withFluidThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URI
import java.util.Locale

private data class SyncUiSnapshot(
    val isConnected: Boolean = false,
    val account: String = "",
    val server: String = "",
    val deviceId: String = "",
    val realtimeConnected: Boolean = false,
    val lastError: String = "",
    val lastSkipped: String = "",
    val lastSkippedAt: Long = 0L,
    val cachedStateJson: String = "",
    val deviceState: SyncDeviceState? = null,
    val sendableDevices: List<SyncDevice> = emptyList(),
)

class SyncSettingsActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val snackbarHostState = SnackbarHostState()

    private var themeModeState by mutableStateOf(ThemeMode.SYSTEM)
    private var dynamicColorState by mutableStateOf(true)

    private var snapshotState by mutableStateOf(SyncUiSnapshot())
    private var syncedSmsRuleState by mutableStateOf(AppRule())
    private var syncedSmsOtpOnlyState by mutableStateOf(false)

    private var codeMode by mutableStateOf(true)
    private var serverDraft by mutableStateOf("")
    private var codeDraft by mutableStateOf("")
    private var usernameDraft by mutableStateOf("")

    private var connectError by mutableStateOf<String?>(null)
    private var isConnecting by mutableStateOf(false)
    private var isRefreshingDevices by mutableStateOf(false)
    private var isPulling by mutableStateOf(false)
    private var isDisconnecting by mutableStateOf(false)
    private var pendingDeviceIds by mutableStateOf(emptySet<String>())
    private var optimisticReceiveFrom by mutableStateOf(emptyMap<String, Boolean>())
    private var lastPullCount by mutableStateOf<Int?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withFluidThemeMode())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "短信云同步"
        codeMode = savedInstanceState?.getBoolean(KEY_CODE_MODE, true) ?: true
        serverDraft = savedInstanceState?.getString(KEY_SERVER).orEmpty()
        codeDraft = savedInstanceState?.getString(KEY_CODE).orEmpty()
        usernameDraft = savedInstanceState?.getString(KEY_USERNAME).orEmpty()

        refreshThemeAndConfig()
        enableEdgeToEdge()
        configureEdgeToEdgeForTheme(this, isDarkThemeForContext(this, themeModeState))

        setContent {
            FluidCapsuleTheme(
                themeMode = themeModeState,
                dynamicColor = dynamicColorState,
            ) {
                SyncSettingsScreen(
                    snapshot = snapshotState,
                    syncedSmsRule = syncedSmsRuleState,
                    syncedSmsOtpOnly = syncedSmsOtpOnlyState,
                    codeMode = codeMode,
                    onCodeModeChange = { codeMode = it },
                    serverDraft = serverDraft,
                    onServerDraftChange = { serverDraft = it },
                    codeDraft = codeDraft,
                    onCodeDraftChange = { codeDraft = it.uppercase(Locale.ROOT) },
                    usernameDraft = usernameDraft,
                    onUsernameDraftChange = { usernameDraft = it },
                    connectError = connectError,
                    onDismissConnectError = { connectError = null },
                    isConnecting = isConnecting,
                    isRefreshingDevices = isRefreshingDevices,
                    isPulling = isPulling,
                    isDisconnecting = isDisconnecting,
                    pendingDeviceIds = pendingDeviceIds,
                    optimisticReceiveFrom = optimisticReceiveFrom,
                    lastPullCount = lastPullCount,
                    snackbarHostState = snackbarHostState,
                    onNavigateBack = ::finish,
                    onRefreshDevices = { refreshDevices(showFeedback = true) },
                    onConnect = ::requestConnect,
                    onClearLastError = {
                        SyncConfig(this).clearLastError()
                        refreshLocalSnapshot()
                    },
                    onClearLastSkipped = {
                        SyncConfig(this).clearLastSkipped()
                        refreshLocalSnapshot()
                    },
                    onOpenSyncedSmsRule = ::openSyncedSmsRule,
                    onToggleDevice = ::toggleDeviceRoute,
                    onPullNow = ::pullNow,
                    onDisconnect = ::disconnectNow,
                    onPollTick = ::refreshLocalSnapshot,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshThemeAndConfig()
        if (snapshotState.isConnected) {
            refreshDevices(showFeedback = false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_CODE_MODE, codeMode)
        outState.putString(KEY_SERVER, serverDraft)
        outState.putString(KEY_CODE, codeDraft)
        outState.putString(KEY_USERNAME, usernameDraft)
        super.onSaveInstanceState(outState)
    }

    private fun refreshThemeAndConfig() {
        themeModeState = UserSettings.themeMode(this)
        dynamicColorState = UserSettings.dynamicColorEnabled(this)
        refreshLocalSnapshot()
    }

    private fun refreshLocalSnapshot() {
        val config = SyncConfig(this)
        val connected = config.isConnected
        val cachedJson = config.cachedState
        val previous = snapshotState
        val parsedState = if (cachedJson == previous.cachedStateJson && previous.deviceState != null) {
            previous.deviceState
        } else {
            cachedJson.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { SyncEngine.parseState(JSONObject(raw)) }.getOrNull()
            }
        }
        val sendable = parsedState?.devices?.filter { device ->
            device.id != parsedState.currentDeviceId && device.canSend
        }.orEmpty()

        snapshotState = SyncUiSnapshot(
            isConnected = connected,
            account = config.account,
            server = config.server,
            deviceId = config.deviceId,
            realtimeConnected = connected && SyncRealtimeService.connected,
            lastError = config.lastError,
            lastSkipped = config.lastSkipped,
            lastSkippedAt = config.lastSkippedAt,
            cachedStateJson = cachedJson,
            deviceState = parsedState,
            sendableDevices = sendable,
        )
        syncedSmsRuleState = AppRuleStore.get(this, packageName)
        syncedSmsOtpOnlyState = NotificationWhitelist.isOtpOnly(this, packageName)
    }

    private fun requestConnect(password: String) {
        if (isConnecting) return
        val normalized = runCatching { SyncApi.normalizeServer(serverDraft) }
        val server = normalized.getOrElse { error ->
            connectError = error.message ?: error.javaClass.simpleName
            return
        }
        val trimmedCode = codeDraft.trim().uppercase(Locale.ROOT)
        val trimmedUsername = usernameDraft.trim()
        if (codeMode && trimmedCode.isBlank()) {
            connectError = "请输入注册码"
            return
        }
        if (!codeMode && (trimmedUsername.isBlank() || password.isEmpty())) {
            connectError = "请输入账号和密码"
            return
        }

        connectError = null
        isConnecting = true
        val credentials = SyncCredentials(
            server = server,
            code = if (codeMode) trimmedCode else "",
            username = if (codeMode) "" else trimmedUsername,
            password = if (codeMode) "" else password,
        )
        runSyncWork(
            work = { SyncEngine.connect(this, credentials) },
            onSuccess = {
                isConnecting = false
                lastPullCount = null
                refreshLocalSnapshot()
            },
            onFailure = { error ->
                isConnecting = false
                refreshLocalSnapshot()
                // Registration may succeed before a subsequent device refresh fails.
                if (SyncConfig(this).isConnected) {
                    showSnackbar("连接已建立，但刷新设备失败：${error.readableMessage()}")
                } else {
                    connectError = error.readableMessage()
                }
            },
        )
    }

    private fun refreshDevices(showFeedback: Boolean) {
        if (isRefreshingDevices || isDisconnecting || !SyncConfig(this).isConnected) return
        isRefreshingDevices = true
        runSyncWork(
            work = { SyncEngine.getDeviceState(this) },
            onSuccess = {
                isRefreshingDevices = false
                refreshLocalSnapshot()
                if (showFeedback) {
                    showSnackbar("设备列表已更新")
                }
            },
            onFailure = { error ->
                isRefreshingDevices = false
                refreshLocalSnapshot()
                if (showFeedback) {
                    showSnackbar("刷新设备失败：${error.readableMessage()}")
                }
            },
        )
    }

    private fun toggleDeviceRoute(device: SyncDevice, checked: Boolean) {
        if (device.id in pendingDeviceIds || isDisconnecting) return
        pendingDeviceIds = pendingDeviceIds + device.id
        optimisticReceiveFrom = optimisticReceiveFrom + (device.id to checked)
        runSyncWork(
            work = {
                SyncEngine.saveRoutes(this) { latest ->
                    if (checked) latest.receiveFrom + device.id else latest.receiveFrom - device.id
                }
            },
            onSuccess = {
                pendingDeviceIds = pendingDeviceIds - device.id
                optimisticReceiveFrom = optimisticReceiveFrom - device.id
                refreshLocalSnapshot()
            },
            onFailure = { error ->
                pendingDeviceIds = pendingDeviceIds - device.id
                optimisticReceiveFrom = optimisticReceiveFrom - device.id
                refreshLocalSnapshot()
                showSnackbar("保存接收设备失败：${error.readableMessage()}")
            },
        )
    }

    private fun pullNow() {
        if (isPulling || isDisconnecting) return
        isPulling = true
        runSyncWork(
            work = { SyncEngine.pull(this) },
            onSuccess = { count ->
                isPulling = false
                lastPullCount = count
                refreshLocalSnapshot()
                showSnackbar(if (count > 0) "已同步 $count 条新短信" else "当前没有待同步的新短信")
            },
            onFailure = { error ->
                isPulling = false
                refreshLocalSnapshot()
                showSnackbar("同步失败：${error.readableMessage()}")
            },
        )
    }

    private fun disconnectNow() {
        if (isDisconnecting) return
        isDisconnecting = true
        runSyncWork(
            work = { SyncEngine.disconnect(this) },
            onSuccess = {
                isDisconnecting = false
                lastPullCount = null
                connectError = null
                pendingDeviceIds = emptySet()
                optimisticReceiveFrom = emptyMap()
                refreshLocalSnapshot()
            },
            onFailure = { error ->
                isDisconnecting = false
                refreshLocalSnapshot()
                showSnackbar("断开连接失败：${error.readableMessage()}")
            },
        )
    }

    private fun openSyncedSmsRule() {
        startActivity(AppRuleActivity.createIntent(this, packageName, "云同步短信"))
    }

    private fun <T> runSyncWork(
        work: () -> T,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        SyncEngine.executor.execute {
            val outcome = runCatching(work)
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                outcome.onSuccess(onSuccess).onFailure(onFailure)
            }
        }
    }

    private fun showSnackbar(message: String) {
        lifecycleScope.launch {
            if (isFinishing || isDestroyed) return@launch
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    private fun Throwable.readableMessage(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    companion object {
        private const val KEY_CODE_MODE = "codeMode"
        private const val KEY_SERVER = "server"
        private const val KEY_CODE = "code"
        private const val KEY_USERNAME = "username"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncSettingsScreen(
    snapshot: SyncUiSnapshot,
    syncedSmsRule: AppRule,
    syncedSmsOtpOnly: Boolean,
    codeMode: Boolean,
    onCodeModeChange: (Boolean) -> Unit,
    serverDraft: String,
    onServerDraftChange: (String) -> Unit,
    codeDraft: String,
    onCodeDraftChange: (String) -> Unit,
    usernameDraft: String,
    onUsernameDraftChange: (String) -> Unit,
    connectError: String?,
    onDismissConnectError: () -> Unit,
    isConnecting: Boolean,
    isRefreshingDevices: Boolean,
    isPulling: Boolean,
    isDisconnecting: Boolean,
    pendingDeviceIds: Set<String>,
    optimisticReceiveFrom: Map<String, Boolean>,
    lastPullCount: Int?,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onRefreshDevices: () -> Unit,
    onConnect: (String) -> Unit,
    onClearLastError: () -> Unit,
    onClearLastSkipped: () -> Unit,
    onOpenSyncedSmsRule: () -> Unit,
    onToggleDevice: (SyncDevice, Boolean) -> Unit,
    onPullNow: () -> Unit,
    onDisconnect: () -> Unit,
    onPollTick: () -> Unit,
) {
    LaunchedEffect(snapshot.isConnected) {
        if (!snapshot.isConnected) return@LaunchedEffect
        while (isActive) {
            onPollTick()
            delay(1_000L)
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val isBusy = isConnecting || isRefreshingDevices || isPulling || isDisconnecting || pendingDeviceIds.isNotEmpty()
    var showDisconnectDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回，短信云同步",
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "短信云同步",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        if (snapshot.isConnected) {
                            IconButton(
                                onClick = onRefreshDevices,
                                enabled = !isRefreshingDevices && !isPulling && !isDisconnecting,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "刷新设备",
                                )
                            }
                        }
                    },
                )
                AnimatedVisibility(visible = isBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            AnimatedContent(
                targetState = snapshot.isConnected,
                label = "SyncConnectionState",
            ) { connected ->
                if (connected) {
                    ConnectedSyncContent(
                        snapshot = snapshot,
                        syncedSmsRule = syncedSmsRule,
                        syncedSmsOtpOnly = syncedSmsOtpOnly,
                        isPulling = isPulling,
                        isDisconnecting = isDisconnecting,
                        pendingDeviceIds = pendingDeviceIds,
                        optimisticReceiveFrom = optimisticReceiveFrom,
                        lastPullCount = lastPullCount,
                        onClearLastError = onClearLastError,
                        onClearLastSkipped = onClearLastSkipped,
                        onOpenSyncedSmsRule = onOpenSyncedSmsRule,
                        onToggleDevice = onToggleDevice,
                        onPullNow = onPullNow,
                        onRequestDisconnect = { showDisconnectDialog = true },
                    )
                } else {
                    DisconnectedSyncContent(
                        codeMode = codeMode,
                        onCodeModeChange = onCodeModeChange,
                        serverDraft = serverDraft,
                        onServerDraftChange = onServerDraftChange,
                        codeDraft = codeDraft,
                        onCodeDraftChange = onCodeDraftChange,
                        usernameDraft = usernameDraft,
                        onUsernameDraftChange = onUsernameDraftChange,
                        connectError = connectError,
                        onDismissConnectError = onDismissConnectError,
                        isConnecting = isConnecting,
                        onConnect = onConnect,
                    )
                }
            }
        }
    }

    if (showDisconnectDialog) {
        ConfirmAlertDialog(
            title = "断开短信云同步？",
            message = "将清除本机连接设置和设备令牌。",
            confirmLabel = "断开连接",
            dismissLabel = "取消",
            isDestructive = true,
            onConfirm = {
                showDisconnectDialog = false
                onDisconnect()
            },
            onDismiss = { showDisconnectDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisconnectedSyncContent(
    codeMode: Boolean,
    onCodeModeChange: (Boolean) -> Unit,
    serverDraft: String,
    onServerDraftChange: (String) -> Unit,
    codeDraft: String,
    onCodeDraftChange: (String) -> Unit,
    usernameDraft: String,
    onUsernameDraftChange: (String) -> Unit,
    connectError: String?,
    onDismissConnectError: () -> Unit,
    isConnecting: Boolean,
    onConnect: (String) -> Unit,
) {
    // Password stays in memory only and is never written into savedInstanceState.
    var passwordDraft by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TonalSectionCard(containerColor = scheme.surfaceContainerLow) {
            Text(
                text = "连接短信云同步",
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
            )
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = serverDraft,
                onValueChange = onServerDraftChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("服务器地址") },
                placeholder = { Text("https://…") },
                supportingText = { Text("需使用完整的 HTTPS 地址（调试环境环回地址除外）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    if (serverDraft.isNotEmpty()) {
                        IconButton(onClick = { onServerDraftChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "清空服务器地址",
                            )
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(14.dp))
            val modes = listOf(
                true to "注册码",
                false to "账号密码",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (isCode, label) ->
                    SegmentedButton(
                        selected = codeMode == isCode,
                        onClick = { onCodeModeChange(isCode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = modes.size,
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

            Spacer(modifier = Modifier.height(14.dp))
            if (codeMode) {
                OutlinedTextField(
                    value = codeDraft,
                    onValueChange = onCodeDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("注册码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConnect(passwordDraft) }),
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (codeDraft.isNotEmpty()) {
                            IconButton(onClick = { onCodeDraftChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "清空注册码",
                                )
                            }
                        }
                    },
                )
            } else {
                OutlinedTextField(
                    value = usernameDraft,
                    onValueChange = onUsernameDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("账号") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (usernameDraft.isNotEmpty()) {
                            IconButton(onClick = { onUsernameDraftChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "清空账号",
                                )
                            }
                        }
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = passwordDraft,
                    onValueChange = { passwordDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConnect(passwordDraft) }),
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            )
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onConnect(passwordDraft) },
                enabled = !isConnecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
            ) {
                Text(if (isConnecting) "连接中…" else "连接")
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "注册码在同步服务器网页的“添加设备”里生成；也可以直接用同一账号的用户名和密码。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        if (!connectError.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                shape = RoundedCornerShape(18.dp),
                color = scheme.errorContainer,
                contentColor = scheme.onErrorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = connectError,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismissConnectError) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "关闭错误提示",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedSyncContent(
    snapshot: SyncUiSnapshot,
    syncedSmsRule: AppRule,
    syncedSmsOtpOnly: Boolean,
    isPulling: Boolean,
    isDisconnecting: Boolean,
    pendingDeviceIds: Set<String>,
    optimisticReceiveFrom: Map<String, Boolean>,
    lastPullCount: Int?,
    onClearLastError: () -> Unit,
    onClearLastSkipped: () -> Unit,
    onOpenSyncedSmsRule: () -> Unit,
    onToggleDevice: (SyncDevice, Boolean) -> Unit,
    onPullNow: () -> Unit,
    onRequestDisconnect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val localDeviceName = remember(snapshot.deviceState, snapshot.deviceId) {
        snapshot.deviceState?.devices?.find { it.id == snapshot.deviceId }?.name
            ?: "FluidCapsule · ${Build.MODEL}"
    }
    val hostLabel = remember(snapshot.server) {
        runCatching {
            val uri = URI(snapshot.server)
            val host = uri.host.orEmpty()
            if (host.isBlank()) {
                snapshot.server
            } else if (uri.port != -1) {
                "$host:${uri.port}"
            } else {
                host
            }
        }.getOrDefault(snapshot.server)
    }

    val (statusLabel, statusTone) = when {
        snapshot.realtimeConnected -> "实时接收已连接" to StatusTone.SUCCESS
        snapshot.lastError.isNotBlank() -> "实时接收重连中" to StatusTone.WARNING
        else -> "正在连接实时接收" to StatusTone.NEUTRAL
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TonalSectionCard(containerColor = scheme.surfaceContainerLow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = snapshot.account.ifBlank { "已连接账号" },
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = hostLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusBadge(
                    text = statusLabel,
                    tone = statusTone,
                    leadingDot = true,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = localDeviceName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusBadge(
                    text = "只接收端",
                    tone = StatusTone.SECONDARY,
                )
            }
        }

        if (snapshot.lastError.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                shape = RoundedCornerShape(18.dp),
                color = scheme.errorContainer,
                contentColor = scheme.onErrorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "同步错误：${snapshot.lastError}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onClearLastError,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = scheme.onErrorContainer,
                        ),
                    ) {
                        Text("清除错误")
                    }
                }
            }
        }

        if (snapshot.lastSkipped.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "最近跳过 · ${
                                RelativeTimeFormatter.format(
                                    timestampMillis = snapshot.lastSkippedAt,
                                    emptyLabel = "时间未知",
                                )
                            }",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Text(
                            text = snapshot.lastSkipped,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurface,
                        )
                    }
                    TextButton(onClick = onClearLastSkipped) {
                        Text("清除")
                    }
                }
            }
        }

        TonalSectionCard(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onOpenSyncedSmsRule),
            containerColor = scheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "云同步短信胶囊显示规则",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = "调整云同步短信的停留时间、队列优先级与正文隐私显示",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "配置云同步短信专属规则",
                    tint = scheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            RuleSummaryChips(rule = syncedSmsRule, otpOnly = syncedSmsOtpOnly)
        }

        SectionLabel(
            title = "从这些设备接收短信",
            subtitle = "仅展示可发送短信的设备（已过滤本机与其他只接收端）",
        )

        TonalSectionCard(containerColor = scheme.surfaceContainerLow) {
            val state = snapshot.deviceState
            val devices = snapshot.sendableDevices
            when {
                state == null -> {
                    Text(
                        text = "正在获取设备…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                devices.isEmpty() -> {
                    Text(
                        text = "还没有其他设备。在胶囊短信的「云备份与同步」里连接同一账号后，这里会出现它。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                else -> {
                    devices.forEachIndexed { index, device ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = scheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        val checked = optimisticReceiveFrom[device.id] ?: (device.id in state.receiveFrom)
                        val deviceBusy = device.id in pendingDeviceIds
                        val enabled = !deviceBusy && !isDisconnecting
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = enabled) {
                                    onToggleDevice(device, !checked)
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${device.platformLabel} · ${formatDeviceLastSeen(device.lastSeenAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (deviceBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            Switch(
                                checked = checked,
                                onCheckedChange = { newValue -> onToggleDevice(device, newValue) },
                                enabled = enabled,
                                modifier = Modifier.semantics {
                                    contentDescription = "从${device.name}接收短信"
                                },
                            )
                        }
                    }
                }
            }
        }

        FilledTonalButton(
            onClick = onPullNow,
            enabled = !isPulling && !isDisconnecting,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp),
        ) {
            val label = when {
                isPulling -> "同步中…"
                lastPullCount != null -> "已同步 $lastPullCount 条 · 立即同步"
                else -> "立即同步"
            }
            Text(label)
        }

        Button(
            onClick = onRequestDisconnect,
            enabled = !isDisconnecting,
            colors = ButtonDefaults.buttonColors(
                containerColor = scheme.errorContainer,
                contentColor = scheme.onErrorContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp),
        ) {
            Text(if (isDisconnecting) "正在断开…" else "断开连接")
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

private fun formatDeviceLastSeen(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "尚未上线"
    val elapsedMillis = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L)
    if (elapsedMillis < 60_000L) return "刚刚在线"
    return RelativeTimeFormatter.format(timestampMillis)
}
