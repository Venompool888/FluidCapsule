package io.github.venompool888.fluidcapsule.settings

enum class ThemeMode(val storageValue: String, val label: String) {
    DARK("dark", "开启"),
    LIGHT("light", "关闭"),
    SYSTEM("system", "跟随系统");

    companion object {
        fun fromStorageValue(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
