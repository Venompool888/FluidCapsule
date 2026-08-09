package io.github.venompool888.fluidcapsule

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.res.ColorStateList
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.venompool888.fluidcapsule.core.CapsuleAction
import io.github.venompool888.fluidcapsule.core.CapsuleEvent
import io.github.venompool888.fluidcapsule.core.CapsuleKind
import io.github.venompool888.fluidcapsule.core.CapsulePrivacy
import io.github.venompool888.fluidcapsule.diagnostics.DiagnosticsStore
import io.github.venompool888.fluidcapsule.keepalive.KeepAliveService
import io.github.venompool888.fluidcapsule.notification.CapsuleNotificationListenerService
import io.github.venompool888.fluidcapsule.publisher.PublisherRouter
import io.github.venompool888.fluidcapsule.settings.NotificationWhitelist
import io.github.venompool888.fluidcapsule.settings.UserSettings
import io.github.venompool888.fluidcapsule.settings.WhitelistActivity
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var notificationPermissionButton: Button
    private lateinit var whitelistButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "流体胶囊"
        configureSystemBars()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(12))
        }
        content.addView(buildHero(), matchWidthWrapHeight())
        content.addView(buildFlowCard(), matchWidthWrapHeight().apply { topMargin = dp(14) })

        addSectionLabel(content, "运行状态", "通知链路与系统授权")
        val statusCard = card().apply {
            statusView = TextView(this@MainActivity).apply {
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setTextColor(COLOR_TEXT_SECONDARY)
                setLineSpacing(dp(2).toFloat(), 1f)
                setPadding(dp(16), dp(15), dp(16), dp(15))
                background = rounded(COLOR_STATUS_FILL, 14f)
            }
            addView(statusView, matchWidthWrapHeight())
        }
        content.addView(statusCard, matchWidthWrapHeight())

        addSectionLabel(content, "隐私显示", "控制锁屏、胶囊和剪贴板中的敏感内容")
        val privacyCard = card()
        privacyCard.addSettingRow(
            title = "直接显示验证码",
            summary = "包含锁屏胶囊；关闭后仍可点击复制",
            checked = UserSettings.showOtpDirectly(this),
        ) { checked ->
            UserSettings.setShowOtpDirectly(this, checked)
            toast(if (checked) "将直接显示验证码" else "将隐藏验证码，点击仍可复制")
        }
        privacyCard.addDivider()
        privacyCard.addSettingRow(
            title = "隐藏剪贴板预览",
            summary = "复制验证码时，用掩码保护系统预览",
            checked = UserSettings.maskOtpClipboardPreview(this),
        ) { checked ->
            UserSettings.setMaskOtpClipboardPreview(this, checked)
            toast(if (checked) "复制提示将打码" else "复制提示将直接显示验证码")
        }
        privacyCard.addDivider()
        privacyCard.addSettingRow(
            title = "白名单显示正文",
            summary = "包含锁屏；适用于已信任的通知来源",
            checked = UserSettings.showWhitelistContent(this),
        ) { checked ->
            UserSettings.setShowWhitelistContent(this, checked)
            toast(if (checked) "白名单通知将直接显示正文" else "锁屏将隐藏白名单通知正文")
        }
        content.addView(privacyCard, matchWidthWrapHeight())

        addSectionLabel(content, "专用上云适配", "按需读取受支持应用的实时状态")
        val integrationCard = card()
        integrationCard.addSettingRow(
            title = "Speedtest 测速胶囊",
            summary = "仅在 Speedtest 前台时读取可见测速数值；不点击、不输入、不保存正文",
            checked = UserSettings.speedtestCloudEnabled(this),
        ) { checked ->
            UserSettings.setSpeedtestCloudEnabled(this, checked)
            toast(if (checked) "已启用；还需开启无障碍服务" else "已停用 Speedtest 读取")
        }
        content.addView(integrationCard, matchWidthWrapHeight())

        addSectionLabel(content, "快速设置", "按顺序完成通知权限与来源配置")
        val setupCard = card()
        setupCard.addActionButton("1. 授予通知读取权限", ButtonTone.PRIMARY) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        notificationPermissionButton = setupCard.addActionButton("2. 授予通知发布权限") {
            requestNotificationPermissionOrOpenSettings()
        }
        whitelistButton = setupCard.addActionButton("管理通知岛白名单") {
            whitelistButton.text = "正在打开白名单…"
            whitelistButton.isEnabled = false
            startActivity(Intent(this, WhitelistActivity::class.java))
        }
        setupCard.addActionButton("3. 发布测试验证码 482913", ButtonTone.ACCENT) {
            publishTestOtp()
        }
        content.addView(setupCard, matchWidthWrapHeight())

        addSectionLabel(content, "后台与诊断", "需要时再调整，日常无需反复操作")
        val toolsCard = card()
        toolsCard.addActionButton("开启前台保活", ButtonTone.PRIMARY) { startKeepAlive() }
        toolsCard.addActionButton("关闭前台保活") { stopKeepAlive() }
        toolsCard.addActionButton("打开无障碍保活设置") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        toolsCard.addActionButton("申请忽略电池优化") {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
        if (Build.VERSION.SDK_INT >= 36) {
            toolsCard.addActionButton("打开实时通知提升设置") { openPromotionSettingsSafely() }
        }
        toolsCard.addActionButton("刷新诊断状态", ButtonTone.QUIET) { refreshStatus() }
        content.addView(toolsCard, matchWidthWrapHeight().apply { bottomMargin = dp(8) })

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(COLOR_PAGE)
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        val statusBarScrim = View(this).apply {
            setBackgroundColor(COLOR_PAGE)
            elevation = dp(20).toFloat()
        }
        val navigationBarScrim = View(this).apply {
            setBackgroundColor(COLOR_PAGE)
            elevation = dp(20).toFloat()
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(COLOR_PAGE)
            addView(scrollView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            addView(statusBarScrim, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                Gravity.TOP,
            ))
            addView(navigationBarScrim, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                Gravity.BOTTOM,
            ))
        }
        setContentView(root)
        applySystemBarInsets(root, scrollView, statusBarScrim, navigationBarScrim)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildHero(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(COLOR_HERO, 24f)

        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "流体胶囊图标"
            background = rounded(Color.WHITE, 16f)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }, LinearLayout.LayoutParams(dp(62), dp(62)))

        val copy = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), 0, 0, 0)
        }
        copy.addView(TextView(this@MainActivity).apply {
            text = "通知云"
            textSize = 13f
            setTextColor(COLOR_HERO_ACCENT)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.12f
        })
        copy.addView(TextView(this@MainActivity).apply {
            text = "流体胶囊"
            textSize = 25f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        })
        copy.addView(TextView(this@MainActivity).apply {
            text = "通知提取、验证码解析与胶囊展示"
            textSize = 13f
            setTextColor(COLOR_HERO_SECONDARY)
            setPadding(0, dp(4), 0, 0)
        })
        addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun buildFlowCard(): View = TextView(this).apply {
        text = "短信通知  →  提取验证码  →  胶囊显示  →  点击复制"
        textSize = 14f
        setTextColor(COLOR_TEXT_SECONDARY)
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = rounded(Color.WHITE, 16f, COLOR_BORDER, 1)
    }

    private fun addSectionLabel(parent: LinearLayout, title: String, subtitle: String) {
        val label = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(24), dp(4), dp(10))
        }
        label.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(COLOR_TEXT_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
        })
        label.addView(TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(COLOR_TEXT_TERTIARY)
            setPadding(0, dp(2), 0, 0)
        })
        parent.addView(label, matchWidthWrapHeight())
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = rounded(Color.WHITE, 20f, COLOR_BORDER, 1)
        elevation = dp(1).toFloat()
    }

    @Suppress("DEPRECATION")
    private fun LinearLayout.addSettingRow(
        title: String,
        summary: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit,
    ) {
        val row = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(10), dp(3), dp(10))
        }
        val labels = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
        }
        labels.addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 16f
            setTextColor(COLOR_TEXT_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
        })
        labels.addView(TextView(this@MainActivity).apply {
            text = summary
            textSize = 12f
            setTextColor(COLOR_TEXT_TERTIARY)
            setPadding(0, dp(3), dp(8), 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = Switch(this@MainActivity).apply {
            isChecked = checked
            contentDescription = title
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }
        row.addView(toggle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
        addView(row, matchWidthWrapHeight())
    }

    private fun LinearLayout.addDivider() {
        addView(View(this@MainActivity).apply { setBackgroundColor(COLOR_DIVIDER) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                marginStart = dp(5)
                marginEnd = dp(5)
            },
        )
    }

    private fun LinearLayout.addActionButton(
        label: String,
        tone: ButtonTone = ButtonTone.SECONDARY,
        action: () -> Unit,
    ): Button {
        val colors = when (tone) {
            ButtonTone.PRIMARY -> COLOR_PRIMARY to Color.WHITE
            ButtonTone.ACCENT -> COLOR_ACCENT_SOFT to COLOR_PRIMARY_DARK
            ButtonTone.QUIET -> COLOR_QUIET to COLOR_TEXT_SECONDARY
            ButtonTone.SECONDARY -> COLOR_CONTROL to COLOR_TEXT_PRIMARY
        }
        val button = Button(this@MainActivity).apply {
            text = label
            textSize = 15f
            isAllCaps = false
            setTextColor(colors.second)
            setTypeface(typeface, Typeface.BOLD)
            background = RippleDrawable(
                ColorStateList.valueOf(COLOR_RIPPLE),
                rounded(colors.first, 14f),
                rounded(Color.WHITE, 14f),
            )
            stateListAnimator = null
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate()
                        .scaleX(0.985f).scaleY(0.985f).alpha(0.88f).setDuration(70).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f).setDuration(110).start()
                }
                false
            }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                action()
            }
        }
        addView(button, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54),
        ).apply { topMargin = dp(7) })
        return button
    }

    private fun publishTestOtp() {
        val now = System.currentTimeMillis()
        val showDirectly = UserSettings.showOtpDirectly(this)
        PublisherRouter.publish(
            this,
            CapsuleEvent(
                sourcePackage = packageName,
                eventId = UUID.randomUUID().toString(),
                kind = CapsuleKind.OTP,
                title = "测试验证码",
                shortText = "482913",
                body = "5 分钟内有效 · 点击复制",
                action = CapsuleAction.CopySensitiveText("482913"),
                privacy = if (showDirectly) CapsulePrivacy.SHOW_FULL else CapsulePrivacy.HIDE_SENSITIVE,
                createdAtMillis = now,
                expiresAtMillis = now + 5 * 60_000L,
                dedupeKey = "test:482913",
            ),
        )
        refreshStatus()
    }

    private fun requestNotificationPermissionOrOpenSettings() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.areNotificationsEnabled()) {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    private fun openPromotionSettingsSafely() {
        val promotionIntent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
        try {
            startActivity(promotionIntent)
        } catch (_: ActivityNotFoundException) {
            DiagnosticsStore.markPublish(
                this,
                "PROMOTION_SETTINGS",
                "unsupported_by_coloros_fallback_to_app_notifications",
            )
            Toast.makeText(
                this,
                "ColorOS 未提供标准实时通知提升入口，已打开普通通知设置",
                Toast.LENGTH_LONG,
            ).show()
            try {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                )
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "系统没有可用的通知设置入口", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startKeepAlive() {
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled() && Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            return
        }
        getSharedPreferences("settings", MODE_PRIVATE)
            .edit()
            .putBoolean("keep_alive_enabled", true)
            .apply()
        startForegroundService(Intent(this, KeepAliveService::class.java))
        refreshStatus()
    }

    private fun stopKeepAlive() {
        getSharedPreferences("settings", MODE_PRIVATE)
            .edit()
            .putBoolean("keep_alive_enabled", false)
            .apply()
        stopService(Intent(this, KeepAliveService::class.java))
        refreshStatus()
    }

    private fun refreshStatus() {
        val manager = getSystemService(NotificationManager::class.java)
        val listenerEnabled = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners",
        )?.split(':')
            ?.mapNotNull(ComponentName::unflattenFromString)
            ?.any { it.packageName == packageName && it.className == CapsuleNotificationListenerService::class.java.name }
            ?: false
        val powerManager = getSystemService(PowerManager::class.java)
        val keepAliveRequested = getSharedPreferences("settings", MODE_PRIVATE)
            .getBoolean("keep_alive_enabled", false)
        val promotion = if (Build.VERSION.SDK_INT >= 36) {
            manager.canPostPromotedNotifications()
        } else {
            false
        }

        notificationPermissionButton.text = if (manager.areNotificationsEnabled()) {
            "2. 通知发布已授权 ✓（点此查看设置）"
        } else {
            "2. 授予通知发布权限"
        }
        whitelistButton.text = "管理通知岛白名单（已选 ${NotificationWhitelist.packages(this).size} 个）"
        whitelistButton.isEnabled = true

        statusView.text = buildString {
            appendLine(statusLine("通知发布", manager.areNotificationsEnabled()))
            appendLine(statusLine("通知读取", listenerEnabled))
            appendLine(statusLine("实时通知提升", promotion))
            appendLine(statusLine("忽略电池优化", powerManager.isIgnoringBatteryOptimizations(packageName)))
            appendLine(statusLine("前台保活请求", keepAliveRequested))
            appendLine()
            appendLine("最近活动")
            append(DiagnosticsStore.snapshot(this@MainActivity))
        }
    }

    private fun statusLine(label: String, enabled: Boolean): String =
        "${if (enabled) "✓" else "○"}  ${label.padEnd(8, '　')} ${if (enabled) "已开启" else "未开启"}"

    private fun configureSystemBars() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun applySystemBarInsets(
        root: View,
        scrollView: View,
        statusBarScrim: View,
        navigationBarScrim: View,
    ) {
        root.setOnApplyWindowInsetsListener { _, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                top = bars.top
                bottom = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            scrollView.setPadding(0, top + dp(14), 0, bottom + dp(28))
            statusBarScrim.layoutParams = statusBarScrim.layoutParams.apply { height = top }
            navigationBarScrim.layoutParams = navigationBarScrim.layoutParams.apply { height = bottom }
            insets
        }
        root.requestApplyInsets()
    }

    private fun rounded(
        color: Int,
        radius: Float,
        strokeColor: Int? = null,
        strokeWidth: Int = 0,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
        if (strokeColor != null && strokeWidth > 0) setStroke(dp(strokeWidth), strokeColor)
    }

    private fun matchWidthWrapHeight() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class ButtonTone { PRIMARY, SECONDARY, ACCENT, QUIET }

    companion object {
        private val COLOR_PAGE = Color.rgb(245, 248, 252)
        private val COLOR_HERO = Color.rgb(11, 39, 64)
        private val COLOR_HERO_ACCENT = Color.rgb(86, 220, 210)
        private val COLOR_HERO_SECONDARY = Color.rgb(194, 211, 225)
        private val COLOR_PRIMARY = Color.rgb(10, 145, 136)
        private val COLOR_PRIMARY_DARK = Color.rgb(6, 92, 89)
        private val COLOR_TEXT_PRIMARY = Color.rgb(20, 38, 55)
        private val COLOR_TEXT_SECONDARY = Color.rgb(66, 81, 96)
        private val COLOR_TEXT_TERTIARY = Color.rgb(109, 123, 137)
        private val COLOR_BORDER = Color.rgb(223, 231, 238)
        private val COLOR_DIVIDER = Color.rgb(234, 239, 244)
        private val COLOR_STATUS_FILL = Color.rgb(244, 248, 252)
        private val COLOR_CONTROL = Color.rgb(235, 241, 246)
        private val COLOR_ACCENT_SOFT = Color.rgb(218, 244, 241)
        private val COLOR_QUIET = Color.rgb(247, 249, 251)
        private val COLOR_RIPPLE = Color.argb(48, 10, 145, 136)
    }
}
