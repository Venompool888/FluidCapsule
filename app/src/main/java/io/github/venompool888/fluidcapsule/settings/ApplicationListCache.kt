package io.github.venompool888.fluidcapsule.settings

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Base64

data class CachedApplicationEntry(
    val packageName: String,
    val label: String,
    val isEmailApp: Boolean,
)

object ApplicationListCache {
    private const val PREFS = "application_list_cache"
    private const val KEY_ENTRIES = "entries_v1"

    fun load(context: Context): List<CachedApplicationEntry> =
        ApplicationListCacheCodec.decode(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ENTRIES, null),
        )

    fun save(context: Context, entries: List<CachedApplicationEntry>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, ApplicationListCacheCodec.encode(entries))
            .apply()
    }
}

object ApplicationListCacheCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(entries: List<CachedApplicationEntry>): String = entries.joinToString("\n") { entry ->
        "${encodePart(entry.packageName)}.${encodePart(entry.label)}.${if (entry.isEmailApp) 1 else 0}"
    }

    fun decode(value: String?): List<CachedApplicationEntry> = value.orEmpty()
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split('.', limit = 3)
            if (parts.size != 3 || parts[2] !in setOf("0", "1")) return@mapNotNull null
            runCatching {
                CachedApplicationEntry(
                    packageName = decodePart(parts[0]),
                    label = decodePart(parts[1]),
                    isEmailApp = parts[2] == "1",
                )
            }.getOrNull()?.takeIf { it.packageName.isNotBlank() && it.label.isNotBlank() }
        }
        .distinctBy { it.packageName }
        .toList()

    private fun encodePart(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodePart(value: String): String =
        String(decoder.decode(value), StandardCharsets.UTF_8)
}
