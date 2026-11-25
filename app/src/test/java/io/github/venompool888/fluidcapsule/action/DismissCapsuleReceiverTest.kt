package io.github.venompool888.fluidcapsule.action

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DismissCapsuleReceiverTest {
    @Test fun aDeleteAtOrAfterTheTimeoutIsATimeout() {
        assertTrue(DismissCapsuleReceiver.isTimeout(timesOutAtMillis = 10_000, nowMillis = 10_000))
        assertTrue(DismissCapsuleReceiver.isTimeout(timesOutAtMillis = 10_000, nowMillis = 60_000))
        // Delivery slightly before the recorded time still counts.
        assertTrue(DismissCapsuleReceiver.isTimeout(timesOutAtMillis = 10_000, nowMillis = 9_000))
    }

    @Test fun anEarlierDeleteIsTheUsersSwipe() {
        assertFalse(DismissCapsuleReceiver.isTimeout(timesOutAtMillis = 10_000, nowMillis = 5_000))
    }

    @Test fun theCloseButtonCarriesNoTimeoutAndAlwaysCountsAsTheUser() {
        assertFalse(DismissCapsuleReceiver.isTimeout(timesOutAtMillis = 0, nowMillis = 60_000))
    }
}
