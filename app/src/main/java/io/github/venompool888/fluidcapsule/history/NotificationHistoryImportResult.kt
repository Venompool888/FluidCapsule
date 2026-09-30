package io.github.venompool888.fluidcapsule.history

data class NotificationHistoryImportResult(val inserted: Long, val duplicates: Long, val expired: Long)
class HistoryRetentionChangedException : IllegalStateException()
class HistoryRetentionRestoreException(cause: Exception) : java.io.IOException(
    "导入已回滚，但原保留期限未能保存。请检查存储空间后重新设置保留期限。", cause,
)
