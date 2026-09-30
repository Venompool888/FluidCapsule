package io.github.venompool888.fluidcapsule.history

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.github.venompool888.fluidcapsule.settings.HistoryRetentionPolicy
import io.github.venompool888.fluidcapsule.settings.UserSettings
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class HistoryTransferAction { EXPORT, IMPORT }

sealed interface HistoryTransferState {
    data object Idle : HistoryTransferState
    data class Picking(val action: HistoryTransferAction) : HistoryTransferState
    data class Working(val message: String) : HistoryTransferState
    data class Preview(
        val file: File,
        val metadata: NotificationHistoryBackupMetadata,
        val policy: HistoryRetentionPolicy,
        val expired: Long,
        val cutoffMillis: Long?,
    ) : HistoryTransferState
    data class Finished(val message: String, val success: Boolean) : HistoryTransferState
}

class NotificationHistoryTransfer(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var observer: ((HistoryTransferState) -> Unit)? = null
    private var stagedFile: File? = null
    @Volatile private var closed = false
    @Volatile
    var state: HistoryTransferState = HistoryTransferState.Idle
        private set

    init {
        // On the first controller in a new process, remove only this feature's abandoned staging files.
        if (cleanedOrphans.compareAndSet(false, true)) {
            appContext.cacheDir.listFiles { _, name -> name.startsWith(TEMP_PREFIX) }?.forEach { it.delete() }
        }
    }

    fun attach(observer: (HistoryTransferState) -> Unit) {
        this.observer = observer
        observer(state)
    }

    fun detach() { observer = null }

    fun beginPicking(action: HistoryTransferAction): Boolean {
        if (closed || state != HistoryTransferState.Idle) return false
        publish(HistoryTransferState.Picking(action))
        return true
    }

    fun acceptDocument(uri: Uri?) {
        val picking = state as? HistoryTransferState.Picking ?: return
        if (uri == null) {
            publish(HistoryTransferState.Idle)
            return
        }
        if (picking.action == HistoryTransferAction.EXPORT) export(uri) else prepareImport(uri)
    }

    fun confirmImport(includeExpired: Boolean) {
        val confirmed = state as? HistoryTransferState.Preview ?: return
        work("正在导入通知历史…") {
            val latest = review(confirmed.file)
            if (latest.policy != confirmed.policy || latest.expired != confirmed.expired) {
                latest
            } else {
                try {
                    val result = NotificationHistoryStore.importBackup(
                        appContext, confirmed.file, latest.policy, includeExpired, latest.cutoffMillis,
                    )
                    deleteStagedFile()
                    HistoryTransferState.Finished(
                        "新增 ${result.inserted} 条，重复跳过 ${result.duplicates} 条，过期跳过 ${result.expired} 条。" +
                            if (includeExpired) "\n历史保留期限已设为永久。" else "",
                        true,
                    )
                } catch (_: HistoryRetentionChangedException) {
                    review(confirmed.file)
                }
            }
        }
    }

    fun cancelPreview() {
        if (state !is HistoryTransferState.Preview) return
        deleteStagedFile()
        publish(HistoryTransferState.Idle)
    }

    fun acknowledgeResult() {
        if (state is HistoryTransferState.Finished) publish(HistoryTransferState.Idle)
    }

    fun interrupted() {
        publish(HistoryTransferState.Finished("上次操作已中断，请重新选择文件。", false))
    }

    fun close() {
        if (closed) return
        closed = true
        observer = null
        // Let an already confirmed transaction finish; dispose its private file afterwards.
        executor.execute { deleteStagedFile() }
        executor.shutdown()
    }

    private fun export(uri: Uri) = work("正在导出通知历史…", exporting = true) {
        val file = File.createTempFile(TEMP_PREFIX, ".json", appContext.cacheDir)
        try {
            val version = appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()
            val count = file.bufferedWriter(Charsets.UTF_8).use { output ->
                NotificationHistoryStore.exportBackup(appContext, output, version, System.currentTimeMillis())
            }
            requireNotNull(appContext.contentResolver.openOutputStream(uri, "wt")).use { output ->
                file.inputStream().use { it.copyTo(output) }
            }
            HistoryTransferState.Finished("已导出 $count 条通知历史。", true)
        } finally {
            file.delete()
        }
    }

    private fun prepareImport(uri: Uri) = work("正在读取并校验备份…") {
        val file = File.createTempFile(TEMP_PREFIX, ".json", appContext.cacheDir)
        stagedFile = file
        requireNotNull(appContext.contentResolver.openInputStream(uri)).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        review(file)
    }

    private fun review(file: File): HistoryTransferState.Preview {
        val policy = UserSettings.notificationHistoryRetentionPolicy(appContext)
        val cutoff = policy.cutoffMillis()
        var expired = 0L
        val metadata = NotificationHistoryBackupCodec.read(NotificationHistoryBackupCodec.utf8Reader(file.inputStream())) {
            if (cutoff != null && it.capturedAtMillis < cutoff) expired++
        }
        return HistoryTransferState.Preview(file, metadata, policy, expired, cutoff)
    }

    private fun work(message: String, exporting: Boolean = false, operation: () -> HistoryTransferState) {
        if (closed) return
        publish(HistoryTransferState.Working(message))
        executor.execute {
            val next = try {
                operation()
            } catch (error: Exception) {
                deleteStagedFile()
                HistoryTransferState.Finished(
                    if (error is HistoryRetentionRestoreException) error.message!!
                    else if (exporting) "导出失败，目标文件可能不完整，请重新导出。"
                    else "无法读取或导入备份。请确认文件是有效的通知历史 JSON 备份，且存储空间和文件访问权限正常。",
                    false,
                )
            }
            main.post {
                if (closed) {
                    if (next is HistoryTransferState.Preview) next.file.delete()
                } else publish(next)
            }
        }
    }

    private fun publish(next: HistoryTransferState) {
        state = next
        observer?.invoke(next)
    }

    private fun deleteStagedFile() {
        stagedFile?.delete()
        stagedFile = null
    }

    companion object {
        private const val TEMP_PREFIX = "history-transfer-"
        private val cleanedOrphans = AtomicBoolean(false)
    }
}
