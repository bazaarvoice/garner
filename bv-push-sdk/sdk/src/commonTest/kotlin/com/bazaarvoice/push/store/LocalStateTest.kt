package com.bazaarvoice.push.store

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalStateTest {
    // Codec round trip; unknown fields tolerated (forward compat).
    @Test
    fun roundTrip() {
        val state = LocalState(deviceId = "d1", userId = "u1", desiredOptedOut = true)
        val decoded = LocalState.decode(state.encode())
        assertEquals(state, decoded)
    }

    @Test
    fun emptyAndUnknown() {
        assertEquals(LocalState(), LocalState.decode(null))
        assertEquals(LocalState(), LocalState.decode(""))
        assertEquals("d9", LocalState.decode("""{"deviceId":"d9","future":"x"}""").deviceId)
        assertEquals(LocalState(), LocalState.decode("{not json"))
    }
}
