package io.github.venompool888.fluidcapsule

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.res.ColorStateList
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.venompool888.fluidcapsule.core.CapsuleAction
import io.github.venompool888.fluidcapsule.core.CapsuleEvent
import io.github.venompool888.fluidcapsule.core.CapsuleKind
import io.github.venompool888.fluidcapsule.core.CapsulePrivacy
import io.github.venompool888.fluidcapsule.diagnostics.DiagnosticsStore
import io.github.venompool888.fluidcapsule.history.NotificationHistoryAppGroup
import io.github.venompool888.fluidcapsule.history.NotificationHistoryEntry
import io.github.venompool888.fluidcapsule.history.NotificationHistoryStore
import io.github.venompool888.fluidcapsule.keepalive.KeepAliveService
import io.github.venompool888.fluidcapsule.notification.CapsuleNotificationListenerService
import io.github.venompool888.fluidcapsule.publisher.PublisherRouter
import io.github.venompool888.fluidcapsule.settings.CapsuleDisplayDuration
import io.github.venompool888.fluidcapsule.settings.NotificationWhitelist
import io.github.venompool888.fluidcapsule.settings.UserSettings
import io.github.venompool888.fluidcapsule.settings.WhitelistActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var notificationPermissionButton: Button
    private lateinit var whitelistCountView: TextView
    private lateinit var whitelistSummaryView: TextView
    private lateinit var homePage: View
    private lateinit var rulesPage: View
    private lateinit var historyPage: View
    private lateinit var backendPage: View
    private lateinit var pageHost: FrameLayout
    private lateinit var homeTab: NavigationTab
    private lateinit var rulesTab: NavigationTab
    private lateinit var historyTab: NavigationTab
    private lateinit var backendTab: NavigationTab
    private lateinit var historyCountView: TextView
    private lateinit var historyEmptyView: TextView
    private lateinit var historyListView: ListView
    private lateinit var historyAdapter: NotificationHistoryAdapter
    private lateinit var historySortTimeTab: TextView
    private lateinit var historySortCountTab: TextView
    private var currentPage = Page.HOME
    private var historySortMode = HistorySortMode.TIME
    private var expandedHistoryPackage: String? = null
    private val historyIconLoader = Executors.newFixedThreadPool(2)
    private val historyIconHandler = Handler(Looper.getMainLooper())
    private val historyIconRequests = ConcurrentHashMap.newKeySet<String>()
    private val historyIconCache = object : android.util.LruCache<String, Drawable>(96) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "流体胶囊"
        configureSystemBars()

        homePage = buildHomePage()
        rulesPage = buildRulesPage().apply { visibility = View.GONE }
        historyPage = buildHistoryPage().apply { visibility = View.GONE }
        backendPage = buildBackendPage().apply { visibility = View.GONE }
        pageHost = FrameLayout(this).apply {
            setBackgroundColor(COLOR_PAGE)
            addView(homePage, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            addView(historyPage, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            addView(rulesPage, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            addView(backendPage, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
        val bottomNavigation = buildBottomNavigation()
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
            addView(pageHost, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            addView(bottomNavigation, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(BOTTOM_NAV_HEIGHT_DP),
                Gravity.BOTTOM,
            ).apply {
                marginStart = dp(14)
                marginEnd = dp(14)
            })
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
        applySystemBarInsets(root, pageHost, bottomNavigation, statusBarScrim, navigationBarScrim)
        pageHost.alpha = 0.72f
        pageHost.translationY = dp(10).toFloat()
        pageHost.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(240)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        if (currentPage == Page.HISTORY) refreshHistory()
    }

    override fun onDestroy() {
        historyIconLoader.shutdownNow()
        super.onDestroy()
    }

    private fun buildHomePage(): View {
        val content = pageContent()
        content.addView(buildHero(), matchWidthWrapHeight())
        content.addView(buildFlowCard(), matchWidthWrapHeight().apply { topMargin = dp(14) })
        addSectionLabel(content, "快速设置", "按顺序完成系统授权与功能测试")
        val setupCard = card()
        setupCard.addActionButton("1. 授予通知读取权限", ButtonTone.PRIMARY) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        notificationPermissionButton = setupCard.addActionButton("2. 授予通知发布权限") {
            requestNotificationPermissionOrOpenSettings()
        }
        setupCard.addActionButton("3. 发布测试验证码 482913", ButtonTone.ACCENT) {
            publishTestOtp()
        }
        content.addView(setupCard, matchWidthWrapHeight().apply { bottomMargin = dp(8) })
        return scrollPage(content)
    }

    private fun buildRulesPage(): View {
        val content = pageContent()
        content.addPageHeader("规则", "通知来源、显示时长与隐私")

        addSectionLabel(content, "通知来源", "选择允许转换为流体云的应用")
        content.addView(buildWhitelistManagementCard(), matchWidthWrapHeight())

        addSectionLabel(content, "胶囊时长", "控制 QQ、微信等消息在流体云中的停留时间")
        val durationCard = card()
        durationCard.addDisplayDurationSetting()
        content.addView(durationCard, matchWidthWrapHeight())

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
        content.addView(privacyCard, matchWidthWrapHeight().apply { bottomMargin = dp(8) })
        return scrollPage(content)
    }

    private fun buildBackendPage(): View {
        val content = pageContent()
        content.addPageHeader("后台与诊断", "保活设置、系统能力与运行记录")

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

        addSectionLabel(content, "后台工具", "需要时再调整，日常无需反复操作")
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
        return scrollPage(content)
    }

    private fun pageContent() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), 0, dp(20), dp(12))
    }

    private fun scrollPage(content: View): View = ScrollView(this).apply {
        isFillViewport = true
        clipToPadding = false
        setBackgroundColor(COLOR_PAGE)
        addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
    }

    private fun LinearLayout.addPageHeader(title: String, summary: String) {
        addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 26f
            setTextColor(COLOR_TEXT_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }, matchWidthWrapHeight())
        addView(TextView(this@MainActivity).apply {
            text = summary
            textSize = 13f
            setTextColor(COLOR_TEXT_TERTIARY)
            setPadding(0, dp(5), 0, dp(3))
        }, matchWidthWrapHeight())
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

    private fun buildBottomNavigation(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(6), dp(6), dp(6))
        background = rounded(Color.WHITE, 22f, COLOR_BORDER, 1)
        elevation = dp(12).toFloat()

        homeTab = buildNavigationTab("首页", R.drawable.ic_nav_home) { showPage(Page.HOME) }
        rulesTab = buildNavigationTab("规则", R.drawable.ic_nav_rules) { showPage(Page.RULES) }
        historyTab = buildNavigationTab("历史", R.drawable.ic_nav_history) { showPage(Page.HISTORY) }
        backendTab = buildNavigationTab("后台", R.drawable.ic_nav_backend) { showPage(Page.BACKEND) }
        listOf(homeTab, rulesTab, historyTab, backendTab).forEachIndexed { index, tab ->
            addView(tab.root, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                if (index > 0) marginStart = dp(3)
            })
        }
        updateNavigationSelection()
    }

    private fun buildNavigationTab(
        label: String,
        iconRes: Int,
        action: () -> Unit,
    ): NavigationTab {
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(COLOR_TEXT_TERTIARY)
            contentDescription = "$label 图标"
        }
        val text = TextView(this).apply {
            this.text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(COLOR_TEXT_TERTIARY)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(Color.TRANSPARENT, 16f)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.animate().alpha(0.72f).setDuration(55).start()
                        icon.animate().scaleX(0.82f).scaleY(0.82f).setDuration(55).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.animate().alpha(1f).setDuration(130).start()
                        icon.animate().scaleX(1f).scaleY(1f).setDuration(180)
                            .setInterpolator(OvershootInterpolator(1.25f)).start()
                    }
                }
                false
            }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                action()
            }
            addView(icon, LinearLayout.LayoutParams(dp(23), dp(23)))
            addView(text, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(3) })
        }
        return NavigationTab(root, icon, text)
    }

    private fun showPage(page: Page) {
        if (page == currentPage) return
        val direction = if (page.ordinal > currentPage.ordinal) 1f else -1f
        pageView(currentPage).apply {
            animate().cancel()
            visibility = View.GONE
            alpha = 1f
            translationX = 0f
        }
        currentPage = page
        pageView(page).apply {
            animate().cancel()
            alpha = 0.25f
            translationX = direction * dp(18)
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(210)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        updateNavigationSelection()
        if (page == Page.HISTORY) refreshHistory()
        if (page == Page.BACKEND) refreshStatus()
    }

    private fun pageView(page: Page): View = when (page) {
        Page.HOME -> homePage
        Page.RULES -> rulesPage
        Page.HISTORY -> historyPage
        Page.BACKEND -> backendPage
    }

    private fun updateNavigationSelection() {
        if (!::homeTab.isInitialized || !::rulesTab.isInitialized ||
            !::historyTab.isInitialized || !::backendTab.isInitialized
        ) return
        styleNavigationTab(homeTab, currentPage == Page.HOME)
        styleNavigationTab(rulesTab, currentPage == Page.RULES)
        styleNavigationTab(historyTab, currentPage == Page.HISTORY)
        styleNavigationTab(backendTab, currentPage == Page.BACKEND)
    }

    private fun styleNavigationTab(tab: NavigationTab, selected: Boolean) {
        val color = if (selected) COLOR_PRIMARY_DARK else COLOR_TEXT_TERTIARY
        tab.icon.imageTintList = ColorStateList.valueOf(color)
        tab.label.setTextColor(color)
        tab.root.background = rounded(if (selected) COLOR_ACCENT_SOFT else Color.TRANSPARENT, 16f)
        if (tab.selected == selected) return
        tab.selected = selected
        tab.root.animate().cancel()
        tab.icon.animate().cancel()
        if (selected) {
            tab.root.alpha = 0.72f
            tab.root.scaleX = 0.94f
            tab.root.scaleY = 0.94f
            tab.root.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(1.15f))
                .start()
            tab.icon.rotation = -9f
            tab.icon.scaleX = 0.78f
            tab.icon.scaleY = 0.78f
            tab.icon.animate()
                .rotation(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240)
                .setInterpolator(OvershootInterpolator(1.45f))
                .start()
        } else {
            tab.root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start()
            tab.icon.animate().rotation(0f).scaleX(1f).scaleY(1f).setDuration(120).start()
        }
    }

    private fun buildHistoryPage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(4))
            setBackgroundColor(COLOR_PAGE)
        }
        page.addView(TextView(this).apply {
            text = "通知历史记录"
            textSize = 26f
            setTextColor(COLOR_TEXT_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }, matchWidthWrapHeight())
        page.addView(TextView(this).apply {
            text = "Notification History"
            textSize = 13f
            setTextColor(COLOR_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.08f
            setPadding(0, dp(3), 0, dp(14))
        }, matchWidthWrapHeight())

        val recordingCard = card()
        recordingCard.addSettingRow(
            title = "记录新通知",
            summary = "关闭后停止写入，已有历史不会被删除",
            checked = UserSettings.notificationHistoryEnabled(this),
        ) { checked ->
            UserSettings.setNotificationHistoryEnabled(this, checked)
            toast(if (checked) "已开始记录新通知" else "已停止记录，原有历史已保留")
            refreshHistory()
        }
        page.addView(recordingCard, matchWidthWrapHeight())

        val privacyCard = card()
        privacyCard.addHistoryRetentionSetting()
        privacyCard.addActionButton("清空全部通知历史", ButtonTone.QUIET) {
            AlertDialog.Builder(this)
                .setTitle("清空通知历史？")
                .setMessage("这会永久删除本机保存的全部通知正文和处理结果。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空") { _, _ ->
                    val deleted = NotificationHistoryStore.clear(this)
                    expandedHistoryPackage = null
                    refreshHistory(animate = true)
                    toast("已删除 $deleted 条历史")
                }
                .show()
        }
        page.addView(privacyCard, matchWidthWrapHeight().apply { topMargin = dp(10) })

        historySortMode = HistorySortMode.fromStorageValue(
            UserSettings.notificationHistorySortMode(this),
        )
        val sortCard = card().apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        sortCard.addView(TextView(this).apply {
            text = "排序方式"
            textSize = 13f
            setTextColor(COLOR_TEXT_SECONDARY)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), 0, dp(4), dp(8))
        }, matchWidthWrapHeight())
        val sortRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        historySortTimeTab = buildHistorySortTab("按时间") {
            setHistorySortMode(HistorySortMode.TIME)
        }
        historySortCountTab = buildHistorySortTab("按软件通知次数") {
            setHistorySortMode(HistorySortMode.APP_COUNT)
        }
        sortRow.addView(
            historySortTimeTab,
            LinearLayout.LayoutParams(0, dp(44), 1f),
        )
        sortRow.addView(
            historySortCountTab,
            LinearLayout.LayoutParams(0, dp(44), 1.35f).apply { marginStart = dp(7) },
        )
        sortCard.addView(sortRow, matchWidthWrapHeight())
        page.addView(sortCard, matchWidthWrapHeight().apply { topMargin = dp(10) })
        updateHistorySortSelection()

        historyCountView = TextView(this).apply {
            textSize = 13f
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(dp(4), dp(16), dp(4), dp(9))
        }
        page.addView(historyCountView, matchWidthWrapHeight())

        historyAdapter = NotificationHistoryAdapter { sourcePackage ->
            expandedHistoryPackage = if (expandedHistoryPackage == sourcePackage) {
                null
            } else {
                sourcePackage
            }
            refreshHistory(animate = true)
        }
        val listFrame = FrameLayout(this)
        historyListView = ListView(this).apply {
            adapter = historyAdapter
            divider = null
            dividerHeight = dp(10)
            clipToPadding = false
            setPadding(0, 0, 0, dp(8))
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
        }
        listFrame.addView(historyListView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        historyEmptyView = TextView(this).apply {
            text = "还没有通知历史\n打开上方开关后，新通知会保存在这里"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(COLOR_TEXT_TERTIARY)
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(20), dp(36), dp(20), dp(36))
        }
        listFrame.addView(historyEmptyView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
        page.addView(listFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        refreshHistory()
        return page
    }

    private fun buildHistorySortTab(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setOnTouchListener(subtlePressAnimator())
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                action()
            }
        }

    private fun setHistorySortMode(mode: HistorySortMode) {
        if (historySortMode == mode) return
        historySortMode = mode
        expandedHistoryPackage = null
        UserSettings.setNotificationHistorySortMode(this, mode.storageValue)
        updateHistorySortSelection()
        refreshHistory(animate = true)
    }

    private fun updateHistorySortSelection() {
        if (!::historySortTimeTab.isInitialized || !::historySortCountTab.isInitialized) return
        styleHistorySortTab(historySortTimeTab, historySortMode == HistorySortMode.TIME)
        styleHistorySortTab(historySortCountTab, historySortMode == HistorySortMode.APP_COUNT)
    }

    private fun styleHistorySortTab(tab: TextView, selected: Boolean) {
        tab.setTextColor(if (selected) COLOR_PRIMARY_DARK else COLOR_TEXT_TERTIARY)
        tab.background = rounded(if (selected) COLOR_ACCENT_SOFT else COLOR_CONTROL, 13f)
    }

    private fun refreshHistory(animate: Boolean = false) {
        if (!::historyAdapter.isInitialized) return
        val total = NotificationHistoryStore.count(this)
        val rows: List<HistoryListItem>
        val appCount: Int
        val visibleNotificationCount: Int
        when (historySortMode) {
            HistorySortMode.TIME -> {
                val entries = NotificationHistoryStore.recent(this, HISTORY_DISPLAY_LIMIT)
                rows = entries.map { HistoryListItem.Entry(it, nested = false) }
                appCount = 0
                visibleNotificationCount = entries.size
            }
            HistorySortMode.APP_COUNT -> {
                val groups = NotificationHistoryStore.appGroups(this)
                rows = buildList {
                    groups.forEach { group ->
                        val expanded = group.sourcePackage == expandedHistoryPackage
                        add(HistoryListItem.AppGroup(group, expanded))
                        if (expanded) {
                            NotificationHistoryStore.forPackage(
                                this@MainActivity,
                                group.sourcePackage,
                                HISTORY_DISPLAY_LIMIT,
                            ).forEach { entry ->
                                add(HistoryListItem.Entry(entry, nested = true))
                            }
                        }
                    }
                }
                appCount = groups.size
                visibleNotificationCount = 0
            }
        }
        historyAdapter.replace(rows)
        if (animate) animateHistoryList()
        historyCountView.text = buildString {
            append(if (UserSettings.notificationHistoryEnabled(this@MainActivity)) "● 正在记录" else "○ 记录已关闭")
            append("  ·  共 $total 条")
            if (historySortMode == HistorySortMode.TIME && total > visibleNotificationCount) {
                append("  ·  显示最近 $visibleNotificationCount 条")
            }
            if (historySortMode == HistorySortMode.APP_COUNT) {
                append("  ·  $appCount 个软件")
            }
        }
        historyEmptyView.visibility = if (total == 0L) View.VISIBLE else View.GONE
    }

    private fun animateHistoryList() {
        if (!::historyListView.isInitialized) return
        historyListView.animate().cancel()
        historyListView.alpha = 0.45f
        historyListView.translationY = dp(9).toFloat()
        historyListView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(190)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun decisionLabel(decision: String): String = when (decision) {
        "PUBLISHED" -> "● 已提交上云"
        "FILTERED" -> "● 已被规则过滤"
        "SKIPPED" -> "● 未上云"
        "CAPTURED" -> "● 正在处理"
        else -> "● 处理结果未知"
    }

    private fun confirmDeleteHistoryEntry(entry: NotificationHistoryEntry) {
        AlertDialog.Builder(this)
            .setTitle("删除这条通知？")
            .setMessage("${entry.sourceLabel} · ${entry.title.ifBlank { "无标题" }}")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                NotificationHistoryStore.deleteEntry(this, entry.id)
                refreshHistory(animate = true)
            }
            .show()
    }

    private fun confirmDeleteHistoryPackage(sourcePackage: String, sourceLabel: String) {
        AlertDialog.Builder(this)
            .setTitle("删除 ${sourceLabel.ifBlank { sourcePackage }} 的历史？")
            .setMessage("只删除本机历史，不会修改该应用的白名单或专属规则。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                val deleted = NotificationHistoryStore.deletePackage(this, sourcePackage)
                expandedHistoryPackage = null
                refreshHistory(animate = true)
                toast("已删除 $deleted 条历史")
            }
            .show()
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

    private fun buildWhitelistManagementCard(): View = card().apply {
        setPadding(dp(8), dp(8), dp(8), dp(8))
        val row = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = RippleDrawable(
                ColorStateList.valueOf(COLOR_RIPPLE),
                rounded(COLOR_STATUS_FILL, 16f),
                rounded(Color.WHITE, 16f),
            )
        }
        whitelistCountView = TextView(this@MainActivity).apply {
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(COLOR_PRIMARY, 15f)
            contentDescription = "已选择应用数量"
        }
        row.addView(whitelistCountView, LinearLayout.LayoutParams(dp(54), dp(54)))

        val labels = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
        }
        labels.addView(TextView(this@MainActivity).apply {
            text = "管理通知来源"
            textSize = 16f
            setTextColor(COLOR_TEXT_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
        })
        whitelistSummaryView = TextView(this@MainActivity).apply {
            textSize = 12f
            setTextColor(COLOR_TEXT_TERTIARY)
            setPadding(0, dp(3), 0, 0)
        }
        labels.addView(whitelistSummaryView)
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(TextView(this@MainActivity).apply {
            text = "管理  ›"
            textSize = 14f
            setTextColor(COLOR_PRIMARY_DARK)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(48),
        ))
        row.setOnClickListener {
            row.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startActivity(Intent(this@MainActivity, WhitelistActivity::class.java))
        }
        addView(row, matchWidthWrapHeight())
        refreshWhitelistSummary()
    }

    private fun refreshWhitelistSummary() {
        if (!::whitelistCountView.isInitialized || !::whitelistSummaryView.isInitialized) return
        val count = NotificationWhitelist.packages(this).size
        whitelistCountView.text = count.toString()
        whitelistSummaryView.text = if (count == 0) {
            "尚未选择应用"
        } else {
            "已选择 $count 个应用 · 点击查看或修改"
        }
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

    private fun LinearLayout.addDisplayDurationSetting() {
        val currentMinutes = UserSettings.capsuleDisplayDurationMinutes(this@MainActivity)
        addView(TextView(this@MainActivity).apply {
            text = "单条胶囊显示时长"
            textSize = 16f
            setTextColor(COLOR_TEXT_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(5), dp(7), dp(5), 0)
        }, matchWidthWrapHeight())
        addView(TextView(this@MainActivity).apply {
            text = "1–30 分钟连续可调；从下一条胶囊开始生效"
            textSize = 12f
            setTextColor(COLOR_TEXT_TERTIARY)
            setPadding(dp(5), dp(4), dp(5), dp(6))
        }, matchWidthWrapHeight())

        val valueView = TextView(this@MainActivity).apply {
            text = "$currentMinutes 分钟"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(COLOR_PRIMARY_DARK)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        }
        addView(valueView, matchWidthWrapHeight())

        var selectedMinutes = currentMinutes
        val slider = SeekBar(this@MainActivity).apply {
            max = CapsuleDisplayDuration.maxMinutes - CapsuleDisplayDuration.minMinutes
            progress = currentMinutes - CapsuleDisplayDuration.minMinutes
            contentDescription = "胶囊显示时长"
            setPadding(dp(3), 0, dp(3), 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedMinutes = progress + CapsuleDisplayDuration.minMinutes
                    valueView.text = "$selectedMinutes 分钟"
                    contentDescription = "胶囊显示 $selectedMinutes 分钟"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    UserSettings.setCapsuleDisplayDurationMinutes(
                        this@MainActivity,
                        selectedMinutes,
                    )
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    toast("胶囊将显示 $selectedMinutes 分钟")
                }
            })
        }
        addView(slider, matchWidthWrapHeight())

        val rangeLabels = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(9), 0, dp(9), dp(5))
        }
        rangeLabels.addView(TextView(this@MainActivity).apply {
            text = "${CapsuleDisplayDuration.minMinutes} 分钟"
            textSize = 11f
            setTextColor(COLOR_TEXT_TERTIARY)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rangeLabels.addView(TextView(this@MainActivity).apply {
            text = "${CapsuleDisplayDuration.maxMinutes} 分钟"
            textSize = 11f
            gravity = Gravity.END
            setTextColor(COLOR_TEXT_TERTIARY)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(rangeLabels, matchWidthWrapHeight())
    }

    private fun LinearLayout.addHistoryRetentionSetting() {
        val currentDays = UserSettings.notificationHistoryRetentionDays(this@MainActivity)
        addView(TextView(this@MainActivity).apply {
            text = "自动保留时间"
            textSize = 16f
            setTextColor(COLOR_TEXT_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(5), dp(7), dp(5), 0)
        }, matchWidthWrapHeight())
        val valueView = TextView(this@MainActivity).apply {
            text = "$currentDays 天"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(COLOR_PRIMARY_DARK)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(7), 0, 0)
        }
        addView(valueView, matchWidthWrapHeight())
        var selectedDays = currentDays
        addView(SeekBar(this@MainActivity).apply {
            max = 29
            progress = currentDays - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedDays = progress + 1
                    valueView.text = "$selectedDays 天"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    UserSettings.setNotificationHistoryRetentionDays(this@MainActivity, selectedDays)
                    val deleted = NotificationHistoryStore.purgeOlderThanDays(this@MainActivity, selectedDays)
                    refreshHistory(animate = deleted > 0)
                    toast("历史将保留 $selectedDays 天")
                }
            })
        }, matchWidthWrapHeight())
        addView(TextView(this@MainActivity).apply {
            text = "长按单条记录或应用分组也可以单独删除"
            textSize = 12f
            setTextColor(COLOR_TEXT_TERTIARY)
            setPadding(dp(5), 0, dp(5), dp(7))
        }, matchWidthWrapHeight())
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
        refreshWhitelistSummary()

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
        pageHost: View,
        bottomNavigation: View,
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
            pageHost.setPadding(0, top + dp(14), 0, bottom + dp(BOTTOM_NAV_HEIGHT_DP + 18))
            (bottomNavigation.layoutParams as FrameLayout.LayoutParams).apply {
                bottomMargin = bottom + dp(8)
                bottomNavigation.layoutParams = this
            }
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

    private fun subtlePressAnimator(scale: Float = 0.975f) = View.OnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.animate()
                .scaleX(scale).scaleY(scale).alpha(0.82f).setDuration(65).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                .scaleX(1f).scaleY(1f).alpha(1f).setDuration(150)
                .setInterpolator(OvershootInterpolator(1.1f)).start()
        }
        false
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class NotificationHistoryAdapter(
        private val onGroupClicked: (String) -> Unit,
    ) : BaseAdapter() {
        private var rows: List<HistoryListItem> = emptyList()
        private val timeFormat = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault())

        fun replace(newRows: List<HistoryListItem>) {
            rows = newRows
            notifyDataSetChanged()
        }

        override fun getCount(): Int = rows.size

        override fun getItem(position: Int): HistoryListItem = rows[position]

        override fun getItemId(position: Int): Long = when (val item = getItem(position)) {
            is HistoryListItem.Entry -> item.entry.id
            is HistoryListItem.AppGroup -> -kotlin.math.abs(item.group.sourcePackage.hashCode().toLong()) - 1L
        }

        override fun getViewTypeCount(): Int = 2

        override fun getItemViewType(position: Int): Int = when (getItem(position)) {
            is HistoryListItem.Entry -> 0
            is HistoryListItem.AppGroup -> 1
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val isNew = convertView == null
            val view = when (val item = getItem(position)) {
                is HistoryListItem.Entry -> bindHistoryEntry(item, convertView)
                is HistoryListItem.AppGroup -> bindAppGroup(item, convertView)
            }
            if (isNew) {
                view.alpha = 0f
                view.translationY = dp(8).toFloat()
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay((position.coerceAtMost(5) * 20L))
                    .setDuration(180)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            return view
        }

        private fun bindHistoryEntry(item: HistoryListItem.Entry, convertView: View?): View {
            val row = convertView ?: buildHistoryRow()
            val holder = row.tag as HistoryRowHolder
            val entry = item.entry
            row.setPadding(
                dp(if (item.nested) 24 else 15),
                dp(if (item.nested) 11 else 13),
                dp(15),
                dp(if (item.nested) 11 else 13),
            )
            row.background = rounded(
                if (item.nested) COLOR_STATUS_FILL else Color.WHITE,
                18f,
                COLOR_BORDER,
                1,
            )
            row.setOnClickListener(null)
            bindHistoryIcon(holder.icon, entry.sourcePackage, entry.sourceLabel)
            holder.source.text = entry.sourceLabel.ifBlank { entry.sourcePackage }
            holder.time.text = timeFormat.format(Date(entry.postedAtMillis))
            holder.title.text = entry.title
                .takeIf(String::isNotBlank)
                ?: entry.sourceLabel.ifBlank { "新通知" }
            holder.body.text = entry.primaryText
                .takeIf(String::isNotBlank)
                ?: entry.combinedText.takeIf(String::isNotBlank)
                ?: "通知没有可显示的正文"
            holder.packageName.maxLines = 3
            holder.packageName.text = "${decisionLabel(entry.decision)} · ${entry.decisionDetail}\n${entry.sourcePackage}"
            row.setOnLongClickListener {
                confirmDeleteHistoryEntry(entry)
                true
            }
            return row
        }

        private fun bindAppGroup(item: HistoryListItem.AppGroup, convertView: View?): View {
            val row = convertView ?: buildAppGroupRow()
            val holder = row.tag as AppGroupRowHolder
            val group = item.group
            row.background = rounded(
                if (item.expanded) COLOR_ACCENT_SOFT else Color.WHITE,
                18f,
                if (item.expanded) COLOR_PRIMARY else COLOR_BORDER,
                1,
            )
            bindHistoryIcon(holder.icon, group.sourcePackage, group.sourceLabel)
            holder.source.text = group.sourceLabel.ifBlank { group.sourcePackage }
            holder.count.text = "${group.notificationCount} 条通知"
            holder.latest.text = "最近 ${timeFormat.format(Date(group.latestCapturedAtMillis))}"
            holder.packageName.text = group.sourcePackage
            holder.disclosure.text = if (item.expanded) "收起 ⌃" else "展开 ⌄"
            row.setOnClickListener {
                row.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onGroupClicked(group.sourcePackage)
            }
            row.setOnLongClickListener {
                confirmDeleteHistoryPackage(group.sourcePackage, group.sourceLabel)
                true
            }
            return row
        }

        private fun buildHistoryRow(): View {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                setPadding(dp(15), dp(13), dp(15), dp(13))
                background = rounded(Color.WHITE, 18f, COLOR_BORDER, 1)
            }
            val icon = ImageView(this@MainActivity).apply {
                setImageResource(android.R.drawable.sym_def_app_icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))
            val content = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            val meta = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val source = TextView(this@MainActivity).apply {
                textSize = 12f
                setTextColor(COLOR_PRIMARY)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
            }
            val time = TextView(this@MainActivity).apply {
                textSize = 12f
                setTextColor(COLOR_TEXT_TERTIARY)
                gravity = Gravity.END
                maxLines = 1
            }
            meta.addView(source, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            meta.addView(time, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            content.addView(meta, matchWidthWrapHeight())

            val title = TextView(this@MainActivity).apply {
                textSize = 16f
                setTextColor(COLOR_TEXT_PRIMARY)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 2
                setPadding(0, dp(7), 0, 0)
            }
            content.addView(title, matchWidthWrapHeight())
            val body = TextView(this@MainActivity).apply {
                textSize = 14f
                setTextColor(COLOR_TEXT_SECONDARY)
                maxLines = 4
                setLineSpacing(dp(2).toFloat(), 1f)
                setPadding(0, dp(5), 0, 0)
            }
            content.addView(body, matchWidthWrapHeight())
            val packageName = TextView(this@MainActivity).apply {
                textSize = 10f
                setTextColor(COLOR_TEXT_TERTIARY)
                maxLines = 1
                setPadding(0, dp(8), 0, 0)
            }
            content.addView(packageName, matchWidthWrapHeight())
            row.addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.tag = HistoryRowHolder(icon, source, time, title, body, packageName)
            return row
        }

        private fun buildAppGroupRow(): View {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setOnTouchListener(subtlePressAnimator(0.985f))
            }
            val icon = ImageView(this@MainActivity).apply {
                setImageResource(android.R.drawable.sym_def_app_icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
            val content = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), 0, 0, 0)
            }
            val top = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val source = TextView(this@MainActivity).apply {
                textSize = 17f
                setTextColor(COLOR_TEXT_PRIMARY)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
            }
            val count = TextView(this@MainActivity).apply {
                textSize = 13f
                setTextColor(COLOR_PRIMARY_DARK)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                maxLines = 1
            }
            top.addView(source, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(count, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            content.addView(top, matchWidthWrapHeight())

            val detail = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(7), 0, 0)
            }
            val latest = TextView(this@MainActivity).apply {
                textSize = 12f
                setTextColor(COLOR_TEXT_SECONDARY)
                maxLines = 1
            }
            val disclosure = TextView(this@MainActivity).apply {
                textSize = 12f
                setTextColor(COLOR_PRIMARY)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
            }
            detail.addView(latest, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            detail.addView(disclosure, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            content.addView(detail, matchWidthWrapHeight())
            val packageName = TextView(this@MainActivity).apply {
                textSize = 10f
                setTextColor(COLOR_TEXT_TERTIARY)
                maxLines = 1
                setPadding(0, dp(6), 0, 0)
            }
            content.addView(packageName, matchWidthWrapHeight())
            row.addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.tag = AppGroupRowHolder(icon, source, count, latest, packageName, disclosure)
            return row
        }

        private fun bindHistoryIcon(imageView: ImageView, packageName: String, sourceLabel: String) {
            imageView.tag = packageName
            imageView.contentDescription = "${sourceLabel.ifBlank { packageName }} 图标"
            imageView.setImageResource(android.R.drawable.sym_def_app_icon)
            synchronized(historyIconCache) { historyIconCache.get(packageName) }
                ?.let(imageView::setImageDrawable)
                ?: loadHistoryIcon(packageName)
        }

        private fun loadHistoryIcon(packageName: String) {
            if (!historyIconRequests.add(packageName)) return
            historyIconLoader.execute {
                val icon = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
                if (icon != null) synchronized(historyIconCache) { historyIconCache.put(packageName, icon) }
                historyIconRequests.remove(packageName)
                if (icon != null) {
                    historyIconHandler.post {
                        if (!isFinishing && !isDestroyed) notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private data class HistoryRowHolder(
        val icon: ImageView,
        val source: TextView,
        val time: TextView,
        val title: TextView,
        val body: TextView,
        val packageName: TextView,
    )

    private data class AppGroupRowHolder(
        val icon: ImageView,
        val source: TextView,
        val count: TextView,
        val latest: TextView,
        val packageName: TextView,
        val disclosure: TextView,
    )

    private data class NavigationTab(
        val root: LinearLayout,
        val icon: ImageView,
        val label: TextView,
        var selected: Boolean = false,
    )

    private sealed interface HistoryListItem {
        data class Entry(
            val entry: NotificationHistoryEntry,
            val nested: Boolean,
        ) : HistoryListItem

        data class AppGroup(
            val group: NotificationHistoryAppGroup,
            val expanded: Boolean,
        ) : HistoryListItem
    }

    private enum class ButtonTone { PRIMARY, SECONDARY, ACCENT, QUIET }
    private enum class Page { HOME, RULES, HISTORY, BACKEND }
    private enum class HistorySortMode(val storageValue: String) {
        TIME("time"),
        APP_COUNT("app_count");

        companion object {
            fun fromStorageValue(value: String): HistorySortMode =
                entries.firstOrNull { it.storageValue == value } ?: TIME
        }
    }

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
        private const val BOTTOM_NAV_HEIGHT_DP = 72
        private const val HISTORY_DISPLAY_LIMIT = 250
    }
}
