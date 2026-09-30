package io.github.venompool888.fluidcapsule.history

data class NotificationHistoryImportResult(val inserted: Long, val duplicates: Long, val expired: Long)
class HistoryRetentionChangedException : IllegalStateException()
