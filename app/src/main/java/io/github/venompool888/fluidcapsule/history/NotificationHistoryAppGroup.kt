package io.github.venompool888.fluidcapsule.history

data class NotificationHistoryAppGroup(
    val sourcePackage: String,
    val sourceLabel: String,
    val notificationCount: Long,
    val latestCapturedAtMillis: Long,
)
