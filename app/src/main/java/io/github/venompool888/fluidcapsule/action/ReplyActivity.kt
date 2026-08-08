package io.github.venompool888.fluidcapsule.action

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.venompool888.fluidcapsule.publisher.NotificationFactory

class ReplyActivity : Activity() {
    private lateinit var replyInput: EditText
    private lateinit var sendButton: Button
    private lateinit var inputBackground: GradientDrawable
    private lateinit var palette: Palette

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accent = resolveSourceAccent()
        palette = if (isDarkMode()) Palette.dark(accent) else Palette.light(accent)
        configureWindow()
        setContentView(buildContent())
        replyInput.requestFocus()
        replyInput.postDelayed({
            getSystemService(InputMethodManager::class.java)
                .showSoftInput(replyInput, InputMethodManager.SHOW_IMPLICIT)
        }, 180)
    }

    private fun configureWindow() {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0.40f }
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
        )
    }

    private fun buildContent(): View {
        val conversationTitle = intent.getStringExtra(EXTRA_CONVERSATION_TITLE)
            .orEmpty()
            .ifBlank { "这条消息" }
        val sourceLabel = intent.getStringExtra(EXTRA_SOURCE_LABEL)
            .orEmpty()
            .ifBlank { "原应用" }
        val originalMessage = intent.getStringExtra(EXTRA_ORIGINAL_MESSAGE)
            .orEmpty()
            .ifBlank { "未能读取原消息" }

        val frame = FrameLayout(this).apply {
            isClickable = true
            setOnClickListener { finish() }
        }
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(20))
            background = rounded(palette.surface, 28f)
            elevation = dp(18).toFloat()
            isClickable = true
        }
        frame.addView(
            sheet,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ).apply {
                marginStart = dp(22)
                marginEnd = dp(22)
            },
        )
        if (Build.VERSION.SDK_INT >= 30) {
            frame.setOnApplyWindowInsetsListener { _, insets ->
                val keyboardHeight = insets.getInsets(WindowInsets.Type.ime()).bottom
                sheet.translationY = -(keyboardHeight / 2f) - dp(26)
                insets
            }
            frame.requestApplyInsets()
        } else {
            sheet.translationY = -dp(32).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(this).apply {
            text = "回复 $conversationTitle"
            textSize = 22f
            setTextColor(palette.primaryText)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        }, matchWidthWrapHeight())
        heading.addView(TextView(this).apply {
            text = "通过 $sourceLabel 发送"
            textSize = 13f
            setTextColor(palette.secondaryText)
            setPadding(0, dp(3), 0, 0)
        }, matchWidthWrapHeight())
        header.addView(heading, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(palette.secondaryText)
            contentDescription = "关闭回复"
            background = rounded(palette.controlFill, 100f)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        sheet.addView(header, matchWidthWrapHeight())

        sheet.addView(TextView(this).apply {
            text = "原消息"
            textSize = 13f
            setTextColor(palette.secondaryText)
        }, matchWidthWrapHeight().apply {
            topMargin = dp(17)
            bottomMargin = dp(7)
        })
        sheet.addView(TextView(this).apply {
            text = originalMessage
            textSize = 16f
            setTextColor(palette.primaryText)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            background = rounded(palette.controlFill, 16f)
            setPadding(dp(15), dp(12), dp(15), dp(12))
        }, matchWidthWrapHeight())

        val smartReplies = intent.getStringArrayListExtra(EXTRA_SMART_REPLIES).orEmpty()
        if (smartReplies.isNotEmpty()) {
            sheet.addView(TextView(this).apply {
                text = "快捷回复"
                textSize = 13f
                setTextColor(palette.secondaryText)
            }, matchWidthWrapHeight().apply {
                topMargin = dp(16)
                bottomMargin = dp(9)
            })
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            smartReplies.take(3).forEach { reply ->
                row.addView(Button(this).apply {
                    text = reply
                    textSize = 15f
                    isAllCaps = false
                    setTextColor(palette.accent)
                    background = rounded(palette.accentSoft, 100f)
                    minHeight = 0
                    minimumHeight = 0
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(18), dp(10), dp(18), dp(10))
                    setOnClickListener {
                        sendReply(reply)
                    }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(9) })
            }
            sheet.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }, matchWidthWrapHeight())
        }

        inputBackground = inputShape(focused = false)
        replyInput = EditText(this).apply {
            hint = "输入回复…"
            textSize = 17f
            setTextColor(palette.primaryText)
            setHintTextColor(palette.hintText)
            minLines = 1
            maxLines = 4
            gravity = Gravity.CENTER_VERTICAL
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = inputBackground
            setPadding(dp(16), dp(13), dp(16), dp(13))
            setOnFocusChangeListener { _, focused ->
                inputBackground = inputShape(focused)
                background = inputBackground
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateSendButton(!s.isNullOrBlank())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        sheet.addView(replyInput, matchWidthWrapHeight().apply {
            topMargin = dp(if (smartReplies.isEmpty()) 16 else 14)
        })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        controls.addView(Button(this).apply {
            text = "取消"
            textSize = 16f
            isAllCaps = false
            setTextColor(palette.secondaryText)
            background = ColorDrawable(Color.TRANSPARENT)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(10) })
        sendButton = Button(this).apply {
            text = "发送"
            textSize = 16f
            isAllCaps = false
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { sendReply() }
        }
        controls.addView(sendButton, LinearLayout.LayoutParams(0, dp(52), 1.65f))
        sheet.addView(controls, matchWidthWrapHeight().apply { topMargin = dp(14) })
        updateSendButton(false)

        return frame
    }

    private fun updateSendButton(enabled: Boolean) {
        sendButton.isEnabled = enabled
        sendButton.setTextColor(if (enabled) Color.WHITE else palette.disabledText)
        sendButton.background = rounded(
            if (enabled) palette.accent else palette.disabledFill,
            100f,
        )
    }

    private fun inputShape(focused: Boolean) = rounded(palette.inputFill, 18f).apply {
        setStroke(dp(if (focused) 2 else 1), if (focused) palette.accent else palette.inputStroke)
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun sendReply(quickReply: String? = null) {
        val text = (quickReply ?: replyInput.text.toString()).trim()
        if (text.isEmpty()) return
        val sourceAction = sourceAction() ?: return
        val remoteInputs = sourceAction.remoteInputs.orEmpty()
        if (remoteInputs.isEmpty()) return

        val results = Bundle().apply { putCharSequence(remoteInputs.first().resultKey, text) }
        val fillInIntent = Intent()
        RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, results)
        try {
            sourceAction.actionIntent.send(this, 0, fillInIntent)
            getSystemService(NotificationManager::class.java)
                .cancel(NotificationFactory.CAPSULE_NOTIFICATION_ID)
            finish()
        } catch (_: PendingIntent.CanceledException) {
            Toast.makeText(this, "原回复动作已失效", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sourceAction(): Notification.Action? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(EXTRA_SOURCE_ACTION, Notification.Action::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(EXTRA_SOURCE_ACTION)
    }

    private fun isDarkMode(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun resolveSourceAccent(): Int {
        val sourcePackage = intent.getStringExtra(EXTRA_SOURCE_PACKAGE).orEmpty()
        if (sourcePackage.isBlank()) return DEFAULT_ACCENT
        return runCatching {
            val icon = packageManager.getApplicationIcon(sourcePackage)
            val size = dp(48).coerceAtLeast(48)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, size, size)
            icon.draw(Canvas(bitmap))
            dominantAccent(bitmap)
        }.getOrDefault(DEFAULT_ACCENT)
    }

    private fun dominantAccent(bitmap: Bitmap): Int {
        val bucketCount = 24
        val weights = DoubleArray(bucketCount)
        val red = DoubleArray(bucketCount)
        val green = DoubleArray(bucketCount)
        val blue = DoubleArray(bucketCount)
        val hsv = FloatArray(3)
        val step = (bitmap.width / 24).coerceAtLeast(1)
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) < 160) continue
                Color.colorToHSV(color, hsv)
                val saturation = hsv[1]
                val value = hsv[2]
                if (saturation < 0.28f || value < 0.22f || value > 0.98f) continue
                val bucket = ((hsv[0] / 360f) * bucketCount).toInt().coerceIn(0, bucketCount - 1)
                val weight = saturation.toDouble() * (0.45 + value)
                weights[bucket] += weight
                red[bucket] += Color.red(color) * weight
                green[bucket] += Color.green(color) * weight
                blue[bucket] += Color.blue(color) * weight
            }
        }
        val winner = weights.indices.maxByOrNull(weights::get) ?: return DEFAULT_ACCENT
        if (weights[winner] <= 0.0) return DEFAULT_ACCENT
        val color = Color.rgb(
            (red[winner] / weights[winner]).toInt().coerceIn(0, 255),
            (green[winner] / weights[winner]).toInt().coerceIn(0, 255),
            (blue[winner] / weights[winner]).toInt().coerceIn(0, 255),
        )
        Color.colorToHSV(color, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.58f)
        hsv[2] = if (isDarkMode()) {
            hsv[2].coerceIn(0.78f, 0.94f)
        } else {
            hsv[2].coerceIn(0.48f, 0.72f)
        }
        return Color.HSVToColor(hsv)
    }

    private fun matchWidthWrapHeight() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class Palette(
        val surface: Int,
        val primaryText: Int,
        val secondaryText: Int,
        val hintText: Int,
        val handle: Int,
        val controlFill: Int,
        val inputFill: Int,
        val inputStroke: Int,
        val accent: Int,
        val accentSoft: Int,
        val disabledFill: Int,
        val disabledText: Int,
    ) {
        companion object {
            fun light(accent: Int) = Palette(
                surface = Color.rgb(250, 251, 252),
                primaryText = Color.rgb(24, 27, 31),
                secondaryText = Color.rgb(103, 111, 121),
                hintText = Color.rgb(142, 149, 158),
                handle = Color.rgb(211, 215, 220),
                controlFill = Color.rgb(238, 241, 244),
                inputFill = Color.rgb(244, 246, 248),
                inputStroke = Color.rgb(220, 225, 230),
                accent = accent,
                accentSoft = blend(accent, Color.rgb(250, 251, 252), 0.18f),
                disabledFill = Color.rgb(226, 230, 233),
                disabledText = Color.rgb(153, 160, 167),
            )

            fun dark(accent: Int) = Palette(
                surface = Color.rgb(28, 30, 34),
                primaryText = Color.rgb(242, 244, 246),
                secondaryText = Color.rgb(168, 174, 182),
                hintText = Color.rgb(132, 139, 148),
                handle = Color.rgb(78, 82, 88),
                controlFill = Color.rgb(47, 50, 56),
                inputFill = Color.rgb(39, 42, 47),
                inputStroke = Color.rgb(68, 73, 80),
                accent = accent,
                accentSoft = blend(accent, Color.rgb(28, 30, 34), 0.30f),
                disabledFill = Color.rgb(55, 59, 65),
                disabledText = Color.rgb(111, 117, 125),
            )

            private fun blend(foreground: Int, background: Int, ratio: Float): Int = Color.rgb(
                (Color.red(foreground) * ratio + Color.red(background) * (1f - ratio)).toInt(),
                (Color.green(foreground) * ratio + Color.green(background) * (1f - ratio)).toInt(),
                (Color.blue(foreground) * ratio + Color.blue(background) * (1f - ratio)).toInt(),
            )
        }
    }

    companion object {
        const val ACTION_REPLY = "io.github.venompool888.fluidcapsule.action.REPLY"
        const val EXTRA_SOURCE_ACTION = "source_action"
        const val EXTRA_CONVERSATION_TITLE = "conversation_title"
        const val EXTRA_SOURCE_LABEL = "source_label"
        const val EXTRA_SOURCE_PACKAGE = "source_package"
        const val EXTRA_ORIGINAL_MESSAGE = "original_message"
        const val EXTRA_SMART_REPLIES = "smart_replies"
        private val DEFAULT_ACCENT = Color.rgb(0, 122, 112)
    }
}
