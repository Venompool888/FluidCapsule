package io.github.venompool888.fluidcapsule.settings

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.venompool888.fluidcapsule.ui.UiPalette
import io.github.venompool888.fluidcapsule.ui.withFluidThemeMode

class AppRuleActivity : Activity() {
    private val palette by lazy { UiPalette.from(this) }
    private lateinit var sourcePackage: String
    private lateinit var ttlValue: TextView
    private lateinit var priorityValue: TextView
    private lateinit var maxBodyValue: TextView
    private lateinit var contentModeButton: Button
    private lateinit var otpOnlySwitch: Switch
    private lateinit var recordHistorySwitch: Switch
    private lateinit var includeInput: EditText
    private lateinit var excludeInput: EditText
    private lateinit var ttlSlider: SeekBar
    private lateinit var prioritySlider: SeekBar
    private lateinit var maxBodySlider: SeekBar
    private var contentMode = AppRule.CONTENT_INHERIT

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withFluidThemeMode())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourcePackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (sourcePackage.isBlank()) {
            finish()
            return
        }
        val sourceLabel = intent.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { sourcePackage }
        title = "$sourceLabel · 专属规则"
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
            setBackgroundColor(palette.page)
        }
        content.addView(label(sourceLabel, 25f, true))
        content.addView(label(sourcePackage, 12f, false).apply { setTextColor(palette.textTertiary) })
        content.addView(label("这些设置只影响此应用；“继承”会跟随全局设置。", 13f, false).apply {
            setPadding(0, dp(7), 0, dp(18))
        })

        ttlValue = addSlider(content, "胶囊停留时间", 30) { progress ->
            if (progress == 0) "继承全局" else "$progress 分钟"
        }.also { ttlSlider = it.first }.second
        priorityValue = addSlider(content, "队列优先级", 2) { progress ->
            listOf("低", "普通", "高")[progress]
        }.also { prioritySlider = it.first }.second
        maxBodyValue = addSlider(content, "正文最大长度", 480) { progress ->
            "${progress + 20} 字"
        }.also { maxBodySlider = it.first }.second

        content.addView(sectionTitle("正文隐私"))
        contentModeButton = Button(this).apply {
            isAllCaps = false
            setTextColor(palette.textPrimary)
            background = rounded(palette.control, 14f)
            setOnClickListener {
                contentMode = when (contentMode) {
                    AppRule.CONTENT_INHERIT -> AppRule.CONTENT_SHOW
                    AppRule.CONTENT_SHOW -> AppRule.CONTENT_HIDE
                    else -> AppRule.CONTENT_INHERIT
                }
                refreshContentMode()
            }
        }
        content.addView(contentModeButton, matchWrap())

        @Suppress("DEPRECATION")
        otpOnlySwitch = Switch(this).apply {
            text = "仅识别为验证码时上云"
            setTextColor(palette.textPrimary)
        }
        content.addView(otpOnlySwitch, matchWrap())
        @Suppress("DEPRECATION")
        recordHistorySwitch = Switch(this).apply {
            text = "写入本地通知历史"
            setTextColor(palette.textPrimary)
        }
        content.addView(recordHistorySwitch, matchWrap())

        includeInput = keywordInput("包含关键词（逗号分隔；留空表示不限制）")
        excludeInput = keywordInput("排除关键词（逗号分隔；优先于包含规则）")
        content.addView(includeInput, matchWrap())
        content.addView(excludeInput, matchWrap())

        content.addView(Button(this).apply {
            text = "保存专属规则"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(palette.primary, 14f)
            setOnClickListener { save() }
        }, matchWrap().apply { topMargin = dp(18) })
        content.addView(Button(this).apply {
            text = "恢复继承全局设置"
            isAllCaps = false
            setTextColor(palette.textPrimary)
            background = rounded(palette.control, 14f)
            setOnClickListener {
                AppRuleStore.reset(this@AppRuleActivity, sourcePackage)
                NotificationWhitelist.setOtpOnly(this@AppRuleActivity, sourcePackage, false)
                loadRule()
                Toast.makeText(this@AppRuleActivity, "已恢复默认规则", Toast.LENGTH_SHORT).show()
            }
        }, matchWrap().apply { topMargin = dp(7) })

        setContentView(ScrollView(this).apply { addView(content) })
        loadRule()
    }

    private fun loadRule() {
        val rule = AppRuleStore.get(this, sourcePackage)
        ttlSlider.progress = rule.ttlMinutes
        prioritySlider.progress = rule.priority + 1
        maxBodySlider.progress = rule.maxBodyChars - 20
        contentMode = rule.contentMode
        refreshContentMode()
        otpOnlySwitch.isChecked = NotificationWhitelist.isOtpOnly(this, sourcePackage)
        recordHistorySwitch.isChecked = rule.recordHistory
        includeInput.setText(rule.includeKeywords)
        excludeInput.setText(rule.excludeKeywords)
    }

    private fun save() {
        AppRuleStore.set(
            this,
            sourcePackage,
            AppRule(
                ttlMinutes = ttlSlider.progress,
                priority = prioritySlider.progress - 1,
                contentMode = contentMode,
                maxBodyChars = maxBodySlider.progress + 20,
                includeKeywords = includeInput.text.toString().trim(),
                excludeKeywords = excludeInput.text.toString().trim(),
                recordHistory = recordHistorySwitch.isChecked,
            ),
        )
        NotificationWhitelist.setOtpOnly(this, sourcePackage, otpOnlySwitch.isChecked)
        Toast.makeText(this, "规则已保存，下一条通知开始生效", Toast.LENGTH_SHORT).show()
    }

    private fun refreshContentMode() {
        contentModeButton.text = "正文显示：" + when (contentMode) {
            AppRule.CONTENT_SHOW -> "始终显示"
            AppRule.CONTENT_HIDE -> "始终隐藏"
            else -> "继承全局"
        }
    }

    private fun addSlider(
        parent: LinearLayout,
        title: String,
        max: Int,
        formatter: (Int) -> String,
    ): Pair<SeekBar, TextView> {
        parent.addView(sectionTitle(title))
        val value = label("", 18f, true).apply { gravity = Gravity.CENTER_HORIZONTAL }
        parent.addView(value, matchWrap())
        val slider = SeekBar(this).apply {
            this.max = max
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = formatter(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        parent.addView(slider, matchWrap())
        value.text = formatter(0)
        return slider to value
    }

    private fun keywordInput(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 14f
        minLines = 2
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setTextColor(palette.textPrimary)
        setHintTextColor(palette.textTertiary)
        background = rounded(palette.surface, 14f).apply {
            setStroke(dp(1), palette.border)
        }
    }

    private fun sectionTitle(text: String) = label(text, 16f, true).apply {
        setPadding(0, dp(18), 0, dp(5))
    }

    private fun label(text: String, size: Float, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(palette.textPrimary)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_LABEL = "label"
    }
}
