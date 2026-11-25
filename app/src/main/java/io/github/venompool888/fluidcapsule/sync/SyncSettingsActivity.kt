package io.github.venompool888.fluidcapsule.sync

import android.animation.LayoutTransition
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import io.github.venompool888.fluidcapsule.ui.UiPalette
import io.github.venompool888.fluidcapsule.ui.configureFluidSystemBars
import io.github.venompool888.fluidcapsule.ui.withFluidThemeMode
import org.json.JSONObject
import java.net.URI

class SyncSettingsActivity : Activity() {
    private val palette by lazy { UiPalette.from(this) }
    private val main = Handler(Looper.getMainLooper())
    private lateinit var content: LinearLayout
    private lateinit var errorText: TextView
    private var statusText: TextView? = null
    private var devicesCard: LinearLayout? = null
    private var renderedStateJson: String? = null
    private var renderedDeviceState: SyncDeviceState? = null
    private var busy = false
    private var renderedConnected: Boolean? = null
    private var codeMode = true
    private var serverDraft = ""
    private var codeDraft = ""
    private var usernameDraft = ""
    private val statusRefresh = object : Runnable {
        override fun run() {
            updateStatus()
            if (!busy) updateDevices()
            main.postDelayed(this, 1_000)
        }
    }

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(newBase.withFluidThemeMode())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureFluidSystemBars()
        codeMode = savedInstanceState?.getBoolean("codeMode", true) ?: true
        serverDraft = savedInstanceState?.getString("server").orEmpty()
        codeDraft = savedInstanceState?.getString("code").orEmpty()
        usernameDraft = savedInstanceState?.getString("username").orEmpty()
        buildShell()
        render()
    }

    override fun onResume() {
        super.onResume()
        main.post(statusRefresh)
        if (SyncConfig(this).isConnected) refreshDevices()
    }

    override fun onPause() {
        main.removeCallbacks(statusRefresh)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("codeMode", codeMode)
        outState.putString("server", serverDraft)
        outState.putString("code", codeDraft)
        outState.putString("username", usernameDraft)
        super.onSaveInstanceState(outState)
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.page)
        }
        root.addView(label("‹  短信云同步", 24).apply {
            minHeight = dp(56)
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(typeface, Typeface.BOLD)
            contentDescription = "返回，短信云同步"
            isFocusable = true
            background = selectableBackground()
            setOnClickListener { finish() }
        })
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            view.setPadding(dp(18), bars.top + dp(8), dp(18), bars.bottom + dp(12))
            insets
        }
        root.requestApplyInsets()
    }

    private fun render() {
        content.removeAllViews()
        statusText = null
        devicesCard = null
        renderedStateJson = null
        renderedDeviceState = null
        errorText = label("", 14).apply {
            setTextColor(errorColor())
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        }
        val connected = SyncConfig(this).isConnected
        if (connected) renderConnected() else renderConnect()
        // Ease in only when switching between the connect form and the connected view, not on
        // every refresh after a route change.
        if (renderedConnected != null && renderedConnected != connected) {
            content.alpha = 0f
            content.translationY = dp(12).toFloat()
            content.animate().alpha(1f).translationY(0f).setDuration(220)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        renderedConnected = connected
    }

    private fun renderConnect() {
        val card = card().apply { layoutTransition = LayoutTransition() }
        content.addView(card, spaced())
        content.addView(errorText, spaced())
        card.addView(label("连接短信云同步", 19).apply { setTypeface(typeface, Typeface.BOLD) })
        val server = field(card, "服务器地址", serverDraft, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        server.hint = "https://…"
        val mode = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val enrollmentChoice = RadioButton(this).apply { id = View.generateViewId(); text = "注册码" }
        val accountChoice = RadioButton(this).apply { id = View.generateViewId(); text = "账号密码" }
        listOf(enrollmentChoice, accountChoice).forEach { choice ->
            choice.setTextColor(palette.textPrimary)
            choice.buttonTintList = ColorStateList.valueOf(palette.accentText)
            choice.background = rounded(palette.control)
            mode.addView(choice, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        card.addView(mode, spaced())
        val enrollmentFields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val accountFields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val code = field(enrollmentFields, "注册码", codeDraft, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val username = field(accountFields, "账号", usernameDraft, InputType.TYPE_CLASS_TEXT)
        val password = field(accountFields, "密码", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        card.addView(enrollmentFields)
        card.addView(accountFields)
        fun selectMode() {
            enrollmentFields.visibility = if (codeMode) View.VISIBLE else View.GONE
            accountFields.visibility = if (codeMode) View.GONE else View.VISIBLE
        }
        mode.check(if (codeMode) enrollmentChoice.id else accountChoice.id)
        selectMode()
        mode.setOnCheckedChangeListener { _, checked -> codeMode = checked == enrollmentChoice.id; selectMode() }
        val connect = button("连接", primary = true)
        card.addView(connect, spaced())
        connect.setOnClickListener {
            serverDraft = server.text.toString()
            codeDraft = code.text.toString()
            usernameDraft = username.text.toString()
            val credentials = SyncCredentials(serverDraft, if (codeMode) codeDraft else "",
                if (codeMode) "" else usernameDraft, if (codeMode) "" else password.text.toString())
            requestConnect(credentials, connect)
        }
        card.addView(label("注册码在同步服务器网页的“添加设备”里生成；也可以直接用同一账号的用户名和密码。", 13, palette.textSecondary))
    }

    private fun requestConnect(credentials: SyncCredentials, connect: Button) {
        val normalized = runCatching { SyncApi.normalizeServer(credentials.server) }
        val server = normalized.getOrElse { showError(it); return }
        if (codeMode && credentials.code.isBlank()) { showError(IllegalArgumentException("请输入注册码")); return }
        if (SyncConfig(this).requiresHttpApproval(server)) {
            AlertDialog.Builder(this).setTitle("使用未加密连接？")
                .setMessage("HTTP 不会加密注册码、账号密码和设备令牌，网络中的其他人可能读取这些凭据。")
                .setNegativeButton("取消", null).setPositiveButton("继续连接") { _, _ ->
                    SyncConfig(this).approveHttp(server)
                    connect(credentials.copy(server = server), connect)
                }.show()
        } else connect(credentials.copy(server = server), connect)
    }

    private fun connect(credentials: SyncCredentials, connect: Button) {
        connect.text = "连接中…"
        connect.isEnabled = false
        runNetwork({ SyncEngine.connect(this, credentials) }, { render() }, {
            connect.text = "连接"
            connect.isEnabled = true
            // Registration may succeed before a subsequent device refresh fails.
            if (SyncConfig(this).isConnected) render()
            showError(it)
        })
    }

    private fun renderConnected() {
        val config = SyncConfig(this)
        val state = config.cachedState.takeIf { it.isNotBlank() }?.let { SyncEngine.parseState(JSONObject(it)) }
        val summary = card()
        content.addView(summary, spaced())
        summary.addView(label(config.account, 20).apply { setTypeface(typeface, Typeface.BOLD) })
        summary.addView(label(URI(config.server).host, 14, palette.textSecondary))
        summary.addView(label(state?.devices?.find { it.id == config.deviceId }?.name ?: "FluidCapsule · ${android.os.Build.MODEL}", 14))
        statusText = label("", 14).also { summary.addView(it, spaced()) }
        updateStatus()
        content.addView(label("从这些设备接收短信", 18).apply { setTypeface(typeface, Typeface.BOLD) }, spaced())
        devicesCard = card().also { content.addView(it, spaced()) }
        updateDevices()
        val sync = button("立即同步")
        sync.isEnabled = !busy
        content.addView(sync, spaced())
        sync.setOnClickListener {
            sync.text = "同步中…"
            sync.isEnabled = false
            runNetwork({ SyncEngine.pull(this) }, { count ->
                sync.text = "已同步 $count 条 · 立即同步"
                updateDevices()
                updateStatus()
            }, {
                sync.text = "立即同步"; sync.isEnabled = true; showError(it)
            })
        }
        val disconnect = button("断开连接").apply { setTextColor(errorColor()); isEnabled = !busy }
        content.addView(disconnect, spaced())
        disconnect.setOnClickListener {
            AlertDialog.Builder(this).setTitle("断开短信云同步？").setMessage("将清除本机连接设置和设备令牌。")
                .setNegativeButton("取消", null).setPositiveButton("断开连接") { _, _ ->
                    runNetwork({ SyncEngine.disconnect(this) }, { render() })
                }.show()
        }
    }

    private fun updateDevices() {
        val devices = devicesCard ?: return
        val json = SyncConfig(this).cachedState
        if (json == renderedStateJson) return
        val state = json.takeIf { it.isNotBlank() }?.let {
            val latest = SyncEngine.parseState(JSONObject(it))
            latest.copy(devices = latest.devices.filter { device -> device.id != latest.currentDeviceId })
        }
        val needsRender = renderedStateJson == null || renderedDeviceState != state
        renderedStateJson = json
        renderedDeviceState = state
        if (!needsRender) return
        devices.removeAllViews()
        when {
            state == null -> devices.addView(label("正在获取设备…", 14))
            state.devices.isEmpty() -> devices.addView(label("还没有其他设备。在胶囊短信的「云备份与同步」里连接同一账号后，这里会出现它。", 14, palette.textSecondary))
            else -> state.devices.forEach { addDeviceRow(devices, it, state) }
        }
    }

    @Suppress("DEPRECATION")
    private fun addDeviceRow(parent: LinearLayout, device: SyncDevice, state: SyncDeviceState) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), 0, dp(8))
            background = selectableBackground()
            // The switch carries the accessible name and state; the row is just a bigger target.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(label(device.name, 16))
        labels.addView(label("${device.platform} · ${relativeTime(device.lastSeenAt)}", 12, palette.textTertiary))
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = Switch(this).apply {
            minHeight = dp(48)
            contentDescription = "从${device.name}接收短信"
            isChecked = device.id in state.receiveFrom
            isEnabled = !busy
        }
        row.addView(toggle)
        row.setOnClickListener { if (toggle.isEnabled) toggle.toggle() }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
        toggle.setOnCheckedChangeListener { _, checked ->
            toggle.isEnabled = false
            runNetwork({
                SyncEngine.saveRoutes(this) { latest ->
                    if (checked) latest.receiveFrom + device.id else latest.receiveFrom - device.id
                }
            }, { render() }, {
                // Rebuilding from the last confirmed state restores the switch without another PUT.
                render(); showError(it)
            })
        }
    }

    private fun refreshDevices() {
        if (busy) return
        runNetwork({ SyncEngine.getDeviceState(this) }, {
            render()
        })
    }

    private fun <T> runNetwork(work: () -> T, success: (T) -> Unit, failure: (Throwable) -> Unit = ::showError) {
        if (busy) return
        busy = true
        errorText.visibility = View.GONE
        content.isEnabled = false
        setControlsEnabled(content, false)
        SyncEngine.executor.execute {
            val outcome = runCatching(work)
            main.post {
                if (isFinishing || isDestroyed) return@post
                busy = false
                content.isEnabled = true
                setControlsEnabled(content, true)
                outcome.onSuccess(success).onFailure(failure)
            }
        }
    }

    private fun setControlsEnabled(parent: ViewGroup, enabled: Boolean) {
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            child.isEnabled = enabled
            if (child is ViewGroup) setControlsEnabled(child, enabled)
        }
    }

    private fun updateStatus() {
        statusText?.apply {
            val connected = SyncRealtimeService.connected
            val config = SyncConfig(this@SyncSettingsActivity)
            val error = config.lastError
            val skipped = config.lastSkipped
            text = when {
                connected -> "● 实时接收已连接"
                error.isNotBlank() -> "● 实时接收重连中"
                else -> "● 正在连接实时接收"
            }
            if (error.isNotBlank()) append("\n同步错误：$error")
            if (skipped.isNotBlank()) append("\n最近跳过：$skipped")
            setTextColor(if (connected) palette.accentText else palette.textSecondary)
        }
    }

    private fun showError(error: Throwable) {
        if (statusText != null) {
            updateStatus()
            return
        }
        errorText.text = error.message ?: error.javaClass.simpleName
        errorText.visibility = View.VISIBLE
    }

    private fun field(parent: LinearLayout, title: String, initial: String, type: Int): EditText {
        parent.addView(label(title, 14), spaced())
        return EditText(this).apply {
            // setSingleLine replaces the transformation method, so it must come before
            // inputType or a password would show in plain text.
            setSingleLine(true)
            inputType = type
            if (type and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            }
            setText(initial)
            setTextColor(palette.textPrimary)
            setHintTextColor(palette.textTertiary)
            backgroundTintList = ColorStateList.valueOf(palette.accentText)
            minHeight = dp(52)
            contentDescription = title
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                    when (title) {
                        "服务器地址" -> serverDraft = text.toString()
                        "注册码" -> codeDraft = text.toString()
                        "账号" -> usernameDraft = text.toString()
                    }
                }
                override fun afterTextChanged(text: android.text.Editable?) = Unit
            })
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun relativeTime(timestamp: Long): String {
        if (timestamp <= 0) return "尚未上线"
        val minutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0) / 60_000)
        return when {
            minutes == 0L -> "刚刚在线"
            minutes < 60 -> "$minutes 分钟前"
            minutes < 1_440 -> "${minutes / 60} 小时前"
            else -> "${minutes / 1_440} 天前"
        }
    }

    private fun label(title: String, size: Int, color: Int = palette.textPrimary) = TextView(this).apply {
        text = title; textSize = size.toFloat(); setTextColor(color); setPadding(0, dp(4), 0, dp(4))
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(14)); background = rounded(palette.surface)
    }
    private fun button(title: String, primary: Boolean = false) = Button(this).apply {
        text = title; isAllCaps = false; minHeight = dp(52)
        setTextColor(if (primary) Color.WHITE else palette.accentText)
        backgroundTintList = ColorStateList.valueOf(if (primary) palette.primary else palette.control)
    }
    private fun selectableBackground() = obtainStyledAttributes(
        intArrayOf(android.R.attr.selectableItemBackground),
    ).let { attrs -> attrs.getDrawable(0).also { attrs.recycle() } }
    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(18).toFloat() }
    private fun errorColor() = if (palette.isDark) Color.rgb(255, 160, 154) else Color.rgb(175, 36, 36)
    private fun spaced() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    private fun dp(size: Int): Int = (size * resources.displayMetrics.density).toInt()
}
