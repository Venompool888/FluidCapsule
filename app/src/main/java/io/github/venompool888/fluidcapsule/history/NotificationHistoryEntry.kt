package io.github.venompool888.fluidcapsule.history

data class NotificationHistoryEntry(
    val id: Long,
    val sourcePackage: String,
    val sourceLabel: String,
    val title: String,
    val primaryText: String,
    val combinedText: String,
    val postedAtMillis: Long,
    val capturedAtMillis: Long,
    val decision: String,
    val decisionDetail: String,
)
