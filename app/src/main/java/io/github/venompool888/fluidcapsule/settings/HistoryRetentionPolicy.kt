package io.github.venompool888.fluidcapsule.settings

import java.time.Instant
import java.time.ZoneId

enum class HistoryRetentionUnit(
    val storageValue: String,
    val pickerLabel: String,
) {
    DAYS("days", "天 / Days"),
    MONTHS("months", "个月 / Months"),
    YEARS("years", "年 / Years"),
    FOREVER("forever", "永久 / Forever"),
    ;

    companion object {
        fun fromStorageValue(value: String?): HistoryRetentionUnit? =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() }
    }
}

data class HistoryRetentionPolicy(
    val value: Int,
    val unit: HistoryRetentionUnit,
) {
    init {
        if (unit == HistoryRetentionUnit.FOREVER) {
            require(value == 0) { "Forever retention must use value 0" }
        } else {
            require(value in MIN_VALUE..MAX_VALUE) { "Retention value must be 1..999" }
        }
    }

    fun cutoffMillis(
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (unit == HistoryRetentionUnit.FOREVER) return null
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val cutoff = when (unit) {
            HistoryRetentionUnit.DAYS -> now.minusDays(value.toLong())
            HistoryRetentionUnit.MONTHS -> now.minusMonths(value.toLong())
            HistoryRetentionUnit.YEARS -> now.minusYears(value.toLong())
            HistoryRetentionUnit.FOREVER -> return null
        }
        return cutoff.toInstant().toEpochMilli()
    }

    fun bilingualLabel(): String = when (unit) {
        HistoryRetentionUnit.DAYS -> "$value 天 / $value days"
        HistoryRetentionUnit.MONTHS -> "$value 个月 / $value months"
        HistoryRetentionUnit.YEARS -> "$value 年 / $value years"
        HistoryRetentionUnit.FOREVER -> "永久保存 / Forever"
    }

    fun chineseLabel(): String = when (unit) {
        HistoryRetentionUnit.DAYS -> "$value 天"
        HistoryRetentionUnit.MONTHS -> "$value 个月"
        HistoryRetentionUnit.YEARS -> "$value 年"
        HistoryRetentionUnit.FOREVER -> "永久保存"
    }

    companion object {
        const val MIN_VALUE = 1
        const val MAX_VALUE = 999
        val DEFAULT = HistoryRetentionPolicy(7, HistoryRetentionUnit.DAYS)

        fun fromStored(
            value: Int,
            unitValue: String?,
            legacyDays: Int = DEFAULT.value,
        ): HistoryRetentionPolicy {
            val unit = HistoryRetentionUnit.fromStorageValue(unitValue)
                ?: return HistoryRetentionPolicy(
                    legacyDays.coerceIn(MIN_VALUE, MAX_VALUE),
                    HistoryRetentionUnit.DAYS,
                )
            return if (unit == HistoryRetentionUnit.FOREVER) {
                HistoryRetentionPolicy(0, unit)
            } else {
                HistoryRetentionPolicy(value.coerceIn(MIN_VALUE, MAX_VALUE), unit)
            }
        }
    }
}
