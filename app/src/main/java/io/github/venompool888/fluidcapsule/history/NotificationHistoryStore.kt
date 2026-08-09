package io.github.venompool888.fluidcapsule.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.venompool888.fluidcapsule.notification.NormalizedNotification
import java.util.UUID

object NotificationHistoryStore {
    private const val DATABASE_NAME = "notification_history.db"
    private const val DATABASE_VERSION = 4
    private const val TABLE_HISTORY = "notification_history"

    @Volatile
    private var helper: HistoryDatabase? = null

    fun record(context: Context, notification: NormalizedNotification) {
        val appContext = context.applicationContext
        val label = runCatching {
            val info = appContext.packageManager.getApplicationInfo(notification.packageName, 0)
            appContext.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(notification.packageName)
        val fingerprint = NotificationHistoryFingerprint.create(
            notificationKey = notification.notificationKey,
            postedAtMillis = notification.postedAtMillis,
            title = notification.title,
            combinedText = notification.combinedText,
        )
        val values = ContentValues().apply {
            put("fingerprint", fingerprint)
            put("notification_key", notification.notificationKey)
            put("package_name", notification.packageName)
            put("app_label", label)
            put("title", notification.title)
            put("primary_text", notification.primaryText)
            put("combined_text", notification.combinedText)
            put("posted_at", notification.postedAtMillis)
            put("captured_at", System.currentTimeMillis())
            put("active", 1)
        }
        val db = database(appContext).writableDatabase
        db.beginTransaction()
        try {
            val updated = db.update(
                TABLE_HISTORY,
                values,
                "notification_key = ? AND active = 1",
                arrayOf(notification.notificationKey),
            )
            if (updated == 0) {
                values.put("event_identity", UUID.randomUUID().toString())
                db.insertWithOnConflict(
                    TABLE_HISTORY,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun markRemoved(context: Context, notificationKey: String) {
        val values = ContentValues().apply { put("active", 0) }
        database(context.applicationContext).writableDatabase.update(
            TABLE_HISTORY,
            values,
            "notification_key = ? AND active = 1",
            arrayOf(notificationKey),
        )
    }

    fun reconcileActiveNotifications(context: Context, activeKeys: Set<String>) {
        val db = database(context.applicationContext).writableDatabase
        val values = ContentValues().apply { put("active", 0) }
        if (activeKeys.isEmpty()) {
            db.update(TABLE_HISTORY, values, "active = 1", null)
            return
        }
        val placeholders = activeKeys.joinToString(",") { "?" }
        db.update(
            TABLE_HISTORY,
            values,
            "active = 1 AND notification_key NOT IN ($placeholders)",
            activeKeys.toTypedArray(),
        )
    }

    fun recent(context: Context, limit: Int = 250): List<NotificationHistoryEntry> {
        val safeLimit = limit.coerceIn(1, 1_000)
        return database(context.applicationContext).readableDatabase.query(
            TABLE_HISTORY,
            ENTRY_COLUMNS,
            null,
            null,
            null,
            null,
            "captured_at DESC, id DESC",
            safeLimit.toString(),
        ).use(::readEntries)
    }

    fun forPackage(
        context: Context,
        sourcePackage: String,
        limit: Int = 250,
    ): List<NotificationHistoryEntry> {
        val safeLimit = limit.coerceIn(1, 1_000)
        return database(context.applicationContext).readableDatabase.query(
            TABLE_HISTORY,
            ENTRY_COLUMNS,
            "package_name = ?",
            arrayOf(sourcePackage),
            null,
            null,
            "captured_at DESC, id DESC",
            safeLimit.toString(),
        ).use(::readEntries)
    }

    fun appGroups(context: Context): List<NotificationHistoryAppGroup> =
        database(context.applicationContext).readableDatabase.rawQuery(
            """
            SELECT package_name, MAX(app_label), COUNT(*), MAX(captured_at)
            FROM $TABLE_HISTORY
            GROUP BY package_name
            ORDER BY COUNT(*) DESC, MAX(captured_at) DESC, package_name ASC
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        NotificationHistoryAppGroup(
                            sourcePackage = cursor.getString(0),
                            sourceLabel = cursor.getString(1),
                            notificationCount = cursor.getLong(2),
                            latestCapturedAtMillis = cursor.getLong(3),
                        ),
                    )
                }
            }
        }

    fun count(context: Context): Long =
        database(context.applicationContext).readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_HISTORY",
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

    private fun readEntries(cursor: android.database.Cursor): List<NotificationHistoryEntry> =
        buildList {
            while (cursor.moveToNext()) {
                add(
                    NotificationHistoryEntry(
                        id = cursor.getLong(0),
                        sourcePackage = cursor.getString(1),
                        sourceLabel = cursor.getString(2),
                        title = cursor.getString(3),
                        primaryText = cursor.getString(4),
                        combinedText = cursor.getString(5),
                        postedAtMillis = cursor.getLong(6),
                        capturedAtMillis = cursor.getLong(7),
                    ),
                )
            }
        }

    private fun database(context: Context): HistoryDatabase =
        helper ?: synchronized(this) {
            helper ?: HistoryDatabase(context).also { helper = it }
        }

    private class HistoryDatabase(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_HISTORY (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fingerprint TEXT NOT NULL UNIQUE,
                    event_identity TEXT NOT NULL UNIQUE,
                    notification_key TEXT,
                    package_name TEXT NOT NULL,
                    app_label TEXT NOT NULL,
                    title TEXT NOT NULL,
                    primary_text TEXT NOT NULL,
                    combined_text TEXT NOT NULL,
                    posted_at INTEGER NOT NULL,
                    captured_at INTEGER NOT NULL,
                    active INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX history_captured_at ON $TABLE_HISTORY(captured_at DESC)",
            )
            db.execSQL(
                "CREATE INDEX history_active_key ON $TABLE_HISTORY(notification_key, active)",
            )
            db.execSQL(
                "CREATE INDEX history_package_time ON $TABLE_HISTORY(package_name, captured_at DESC)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE $TABLE_HISTORY ADD COLUMN event_identity TEXT")
                db.execSQL("UPDATE $TABLE_HISTORY SET event_identity = fingerprint")
                db.execSQL(
                    "CREATE UNIQUE INDEX history_event_identity ON $TABLE_HISTORY(event_identity)",
                )
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE $TABLE_HISTORY ADD COLUMN notification_key TEXT")
                db.execSQL(
                    "ALTER TABLE $TABLE_HISTORY ADD COLUMN active INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "CREATE INDEX history_active_key ON $TABLE_HISTORY(notification_key, active)",
                )
            }
            if (oldVersion < 4) {
                db.execSQL(
                    "CREATE INDEX history_package_time ON $TABLE_HISTORY(package_name, captured_at DESC)",
                )
            }
        }
    }

    private val ENTRY_COLUMNS = arrayOf(
        "id",
        "package_name",
        "app_label",
        "title",
        "primary_text",
        "combined_text",
        "posted_at",
        "captured_at",
    )
}
