package io.github.venompool888.fluidcapsule.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import io.github.venompool888.fluidcapsule.settings.ThemeMode
import io.github.venompool888.fluidcapsule.settings.UserSettings

data class UiPalette(
    val page: Int,
    val surface: Int,
    val surfaceRaised: Int,
    val hero: Int,
    val heroAccent: Int,
    val heroSecondary: Int,
    val primary: Int,
    val accentText: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textTertiary: Int,
    val border: Int,
    val divider: Int,
    val statusFill: Int,
    val control: Int,
    val accentSoft: Int,
    val quiet: Int,
    val refresh: Int,
    val ripple: Int,
    val isDark: Boolean,
) {
    companion object {
        fun from(context: Context): UiPalette = if (context.isDarkMode()) dark() else light()

        private fun light() = UiPalette(
            page = Color.rgb(245, 248, 252),
            surface = Color.WHITE,
            surfaceRaised = Color.WHITE,
            hero = Color.rgb(11, 39, 64),
            heroAccent = Color.rgb(86, 220, 210),
            heroSecondary = Color.rgb(194, 211, 225),
            primary = Color.rgb(10, 145, 136),
            accentText = Color.rgb(6, 92, 89),
            textPrimary = Color.rgb(20, 38, 55),
            textSecondary = Color.rgb(66, 81, 96),
            textTertiary = Color.rgb(109, 123, 137),
            border = Color.rgb(223, 231, 238),
            divider = Color.rgb(234, 239, 244),
            statusFill = Color.rgb(244, 248, 252),
            control = Color.rgb(235, 241, 246),
            accentSoft = Color.rgb(218, 244, 241),
            quiet = Color.rgb(247, 249, 251),
            refresh = Color.rgb(196, 83, 10),
            ripple = Color.argb(48, 10, 145, 136),
            isDark = false,
        )

        private fun dark() = UiPalette(
            page = Color.rgb(11, 17, 23),
            surface = Color.rgb(20, 28, 36),
            surfaceRaised = Color.rgb(24, 35, 45),
            hero = Color.rgb(12, 38, 51),
            heroAccent = Color.rgb(103, 229, 218),
            heroSecondary = Color.rgb(174, 201, 216),
            primary = Color.rgb(17, 128, 120),
            accentText = Color.rgb(93, 220, 208),
            textPrimary = Color.rgb(232, 240, 246),
            textSecondary = Color.rgb(174, 190, 201),
            textTertiary = Color.rgb(126, 147, 160),
            border = Color.rgb(43, 57, 68),
            divider = Color.rgb(34, 47, 57),
            statusFill = Color.rgb(15, 24, 32),
            control = Color.rgb(29, 42, 52),
            accentSoft = Color.rgb(18, 55, 51),
            quiet = Color.rgb(16, 25, 33),
            refresh = Color.rgb(255, 171, 92),
            ripple = Color.argb(64, 83, 215, 204),
            isDark = true,
        )
    }
}

fun Context.isDarkMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

fun Context.withFluidThemeMode(): Context {
    val nightMode = when (UserSettings.themeMode(this)) {
        ThemeMode.DARK -> Configuration.UI_MODE_NIGHT_YES
        ThemeMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
        ThemeMode.SYSTEM -> return this
    }
    val override = Configuration(resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
    }
    return createConfigurationContext(override)
}

@Suppress("DEPRECATION")
fun Activity.configureFluidSystemBars() {
    val dark = isDarkMode()
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= 30) {
        window.setDecorFitsSystemWindows(false)
        window.decorView.windowInsetsController?.apply {
            val lightBars = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            setSystemBarsAppearance(if (dark) 0 else lightBars, lightBars)
        }
    } else {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                if (dark) 0 else {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                        View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
    }
}
