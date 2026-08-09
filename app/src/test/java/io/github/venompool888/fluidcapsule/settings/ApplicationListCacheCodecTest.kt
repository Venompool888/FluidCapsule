package io.github.venompool888.fluidcapsule.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationListCacheCodecTest {
    @Test
    fun roundTripsUnicodeLabelsAndEmailClassification() {
        val entries = listOf(
            CachedApplicationEntry("com.google.android.gm", "Gmail", true),
            CachedApplicationEntry("example.app", "换行\n应用 · 测试", false),
        )

        assertEquals(entries, ApplicationListCacheCodec.decode(ApplicationListCacheCodec.encode(entries)))
    }

    @Test
    fun ignoresMalformedAndDuplicateRows() {
        val entry = CachedApplicationEntry("example.app", "应用", false)
        val encoded = ApplicationListCacheCodec.encode(listOf(entry))

        assertEquals(listOf(entry), ApplicationListCacheCodec.decode("broken\n$encoded\n$encoded"))
    }
}
