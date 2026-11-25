package io.github.venompool888.fluidcapsule.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDeviceTest {
    private fun device(platform: String) = SyncDevice("id", "name", platform, "key", 0)

    @Test fun onlyFluidCapsuleIsReceiveOnly() {
        assertFalse(device(RECEIVE_ONLY_PLATFORM).canSend)
        assertTrue(device("android-17").canSend)
        assertTrue(device("").canSend)
    }

    @Test fun platformLabelIsReadable() {
        assertEquals("胶囊短信 · Android 17", device("android-17").platformLabel)
        assertEquals("胶囊短信 · Android", device("android-").platformLabel)
        assertEquals("未知平台", device(" ").platformLabel)
        assertEquals("web", device("web").platformLabel)
    }
}
