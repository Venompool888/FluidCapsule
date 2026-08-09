package io.github.venompool888.fluidcapsule.settings

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Telephony
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.util.Log
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import io.github.venompool888.fluidcapsule.R
import java.util.concurrent.Executors

class WhitelistActivity : Activity() {
    private lateinit var selectedCountView: TextView
    private lateinit var searchInput: EditText
    private lateinit var listView: ListView
    private lateinit var loadingView: TextView
    private lateinit var adapter: AppListAdapter
    private var selectedOnly = false
    private var applications: List<AppEntry> = emptyList()
    private val appLoader = Executors.newSingleThreadExecutor()
    private val iconLoader = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iconCache = object : android.util.LruCache<String, Drawable>(96) {}
    private var createdAtMillis = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createdAtMillis = SystemClock.elapsedRealtime()
        title = "通知岛白名单"
        configureSystemBars()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 248, 252))
        }
        root.addView(TextView(this).apply {
            text = "通知岛白名单"
            textSize = 24f
            setTextColor(Color.rgb(20, 43, 68))
        })
        root.addView(TextView(this).apply {
            text = "勾选后立即生效；邮箱应用可进一步选择“仅验证码上云”。"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(4), 0, dp(10))
        })

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchInput = EditText(this).apply {
            hint = "搜索应用名或包名"
            isSingleLine = true
            textSize = 16f
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
            compoundDrawablePadding = dp(8)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (::adapter.isInitialized) adapter.refresh()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, dp(52), 1f))
        searchRow.addView(Button(this).apply {
            text = "清除"
            isAllCaps = false
            background = rippleBackground(Color.rgb(235, 241, 246), 12f)
            setOnTouchListener(pressAnimator())
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                searchInput.text.clear()
            }
        }, LinearLayout.LayoutParams(dp(82), dp(52)).apply { marginStart = dp(8) })
        root.addView(searchRow, matchWidthWrapHeight())

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        selectedCountView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.DKGRAY)
        }
        filterRow.addView(selectedCountView, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
        @Suppress("DEPRECATION")
        filterRow.addView(Switch(this).apply {
            text = "只看已选"
            textSize = 14f
            setOnCheckedChangeListener { _, checked ->
                selectedOnly = checked
                if (::adapter.isInitialized) adapter.refresh()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        root.addView(filterRow, matchWidthWrapHeight())

        adapter = AppListAdapter()
        listView = ListView(this).apply {
            adapter = this@WhitelistActivity.adapter
            divider = null
            dividerHeight = 0
            clipToPadding = false
        }
        loadingView = TextView(this).apply {
            text = "正在读取应用列表…"
            textSize = 15f
            setTextColor(Color.rgb(109, 123, 137))
            gravity = Gravity.CENTER
        }
        val listFrame = FrameLayout(this).apply {
            addView(listView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            addView(loadingView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
        root.addView(listFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        setContentView(root)
        applySystemBarInsets(root)
        selectedCountView.text = "正在加载应用…"
        loadApplicationsAsync()
    }

    override fun onDestroy() {
        appLoader.shutdownNow()
        iconLoader.shutdownNow()
        super.onDestroy()
    }

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

    private fun applySystemBarInsets(root: View) {
        root.setOnApplyWindowInsetsListener { target, insets ->
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
            target.setPadding(dp(16), top + dp(16), dp(16), bottom + dp(12))
            insets
        }
        root.requestApplyInsets()
    }

    private fun createAppRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(9), dp(2), dp(9))
            isClickable = true
            isFocusable = true
            background = rippleBackground(Color.TRANSPARENT, 12f)
        }
        val iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.sym_def_app_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        row.addView(iconView, LinearLayout.LayoutParams(dp(46), dp(46)))

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        val labelView = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.BLACK)
            maxLines = 1
        }
        labels.addView(labelView)
        val packageView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.GRAY)
            maxLines = 1
        }
        labels.addView(packageView)
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val checkBox = CheckBox(this)
        row.addView(checkBox, LinearLayout.LayoutParams(dp(52), dp(52)))

        val otpOnlyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(64), dp(4), dp(2), dp(10))
        }
        val otpOnlyLabels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        otpOnlyLabels.addView(TextView(this).apply {
            text = "仅验证码上云"
            textSize = 15f
            setTextColor(Color.rgb(20, 43, 68))
        })
        otpOnlyLabels.addView(TextView(this).apply {
            text = "忽略普通邮件，只显示识别成功的验证码"
            textSize = 12f
            setTextColor(Color.GRAY)
        })
        otpOnlyRow.addView(
            otpOnlyLabels,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        @Suppress("DEPRECATION")
        val otpOnlySwitch = Switch(this)
        otpOnlyRow.addView(
            otpOnlySwitch,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row, matchWidthWrapHeight())
            addView(otpOnlyRow, matchWidthWrapHeight())
            addView(View(this@WhitelistActivity).apply {
                setBackgroundColor(Color.rgb(232, 232, 232))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        }
        wrapper.tag = AppRowHolder(
            row,
            iconView,
            labelView,
            packageView,
            checkBox,
            otpOnlyRow,
            otpOnlySwitch,
        )
        return wrapper
    }

    private fun loadApplicationsAsync() {
        appLoader.execute {
            val loaded = loadApplications()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                applications = loaded
                loadingView.visibility = View.GONE
                adapter.refresh()
                reportFullyDrawn()
                Log.i(TAG, "Application list ready in ${SystemClock.elapsedRealtime() - createdAtMillis} ms (${loaded.size} apps)")
            }
        }
    }

    private fun loadApplications(): List<AppEntry> {
        val selected = NotificationWhitelist.packages(this)
        val defaultSms = Telephony.Sms.getDefaultSmsPackage(this)
        val mailtoIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:test@example.com"))
        val mailtoHandlers = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(
                mailtoIntent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(mailtoIntent, 0)
        }.asSequence().map { it.activityInfo.packageName }.toHashSet()
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchablePackages = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }.asSequence().map { it.activityInfo.packageName }.toHashSet()
        val installed = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getInstalledApplications(
                android.content.pm.PackageManager.ApplicationInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(0)
        }
        return installed.asSequence()
            .filter { it.packageName != packageName }
            .filter { app ->
                app.packageName in launchablePackages ||
                    app.packageName == defaultSms ||
                    app.packageName in selected ||
                    app.flags and ApplicationInfo.FLAG_SYSTEM == 0
            }
            .map { app ->
                AppEntry(
                    packageName = app.packageName,
                    label = packageManager.getApplicationLabel(app).toString(),
                    isEmailApp = EmailAppClassifier.isEmailApp(app.packageName, mailtoHandlers),
                )
            }
            .distinctBy { it.packageName }
            .toList()
    }

    private inner class AppListAdapter : BaseAdapter() {
        private var visible: List<AppEntry> = emptyList()

        fun refresh() {
            val selected = NotificationWhitelist.packages(this@WhitelistActivity)
            val query = searchInput.text.toString().trim().lowercase()
            visible = applications.asSequence()
                .filter { !selectedOnly || it.packageName in selected }
                .filter {
                    query.isEmpty() || it.label.lowercase().contains(query) ||
                        it.packageName.lowercase().contains(query)
                }
                .sortedWith(
                    compareByDescending<AppEntry> { it.packageName in selected }
                        .thenBy { it.label.lowercase() },
                )
                .toList()
            selectedCountView.text = "已选 ${selected.size} 个 · 显示 ${visible.size} 个"
            loadingView.text = if (visible.isEmpty()) "没有匹配的应用" else ""
            loadingView.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
            notifyDataSetChanged()
        }

        override fun getCount(): Int = visible.size
        override fun getItem(position: Int): AppEntry = visible[position]
        override fun getItemId(position: Int): Long = getItem(position).packageName.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: createAppRow()
            val holder = view.tag as AppRowHolder
            val entry = getItem(position)
            val selected = entry.packageName in NotificationWhitelist.packages(this@WhitelistActivity)

            holder.label.text = entry.label
            holder.packageName.text = entry.packageName
            holder.icon.contentDescription = entry.label
            holder.icon.tag = entry.packageName
            holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
            synchronized(iconCache) { iconCache.get(entry.packageName) }?.let(holder.icon::setImageDrawable)
                ?: loadIcon(entry.packageName, holder.icon)

            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = selected
            holder.checkBox.contentDescription = if (selected) "移出白名单" else "加入白名单"
            holder.checkBox.setOnCheckedChangeListener { _, checked ->
                NotificationWhitelist.setEnabled(this@WhitelistActivity, entry.packageName, checked)
                holder.row.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                refresh()
            }
            holder.row.setOnClickListener { holder.checkBox.isChecked = !holder.checkBox.isChecked }

            holder.otpOnlyRow.visibility = if (selected && entry.isEmailApp) View.VISIBLE else View.GONE
            holder.otpOnlySwitch.setOnCheckedChangeListener(null)
            holder.otpOnlySwitch.isChecked = NotificationWhitelist.isOtpOnly(
                this@WhitelistActivity,
                entry.packageName,
            )
            holder.otpOnlySwitch.contentDescription = "仅验证码上云"
            holder.otpOnlySwitch.setOnCheckedChangeListener { _, checked ->
                NotificationWhitelist.setOtpOnly(
                    this@WhitelistActivity,
                    entry.packageName,
                    checked,
                )
                holder.otpOnlyRow.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            return view
        }
    }

    private fun loadIcon(packageName: String, imageView: ImageView) {
        iconLoader.execute {
            val drawable = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull() ?: return@execute
            synchronized(iconCache) { iconCache.put(packageName, drawable) }
            mainHandler.post {
                if (!isFinishing && !isDestroyed && imageView.tag == packageName) {
                    imageView.setImageDrawable(drawable)
                }
            }
        }
    }

    private fun rippleBackground(color: Int, radius: Float): RippleDrawable = RippleDrawable(
        ColorStateList.valueOf(Color.argb(48, 10, 145, 136)),
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radius.toInt()).toFloat()
        },
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = dp(radius.toInt()).toFloat()
        },
    )

    private fun pressAnimator() = View.OnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.98f).scaleY(0.98f).alpha(0.88f).setDuration(70).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(110).start()
        }
        false
    }

    private fun matchWidthWrapHeight() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class AppEntry(
        val packageName: String,
        val label: String,
        val isEmailApp: Boolean,
    )
    private data class AppRowHolder(
        val row: LinearLayout,
        val icon: ImageView,
        val label: TextView,
        val packageName: TextView,
        val checkBox: CheckBox,
        val otpOnlyRow: LinearLayout,
        val otpOnlySwitch: Switch,
    )

    companion object {
        private const val TAG = "FluidCapsuleWhitelist"
    }
}
