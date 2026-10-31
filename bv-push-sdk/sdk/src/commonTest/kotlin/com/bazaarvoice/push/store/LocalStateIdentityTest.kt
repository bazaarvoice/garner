package com.bazaarvoice.push.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Settings state carries no email in any form; old state still decodes. */
class LocalStateIdentityTest {
    private companion object {
        const val USER = "user-1"
        const val OLD_FIELD = "emailAddressHash"
        const val OLD_DIGEST = "463dfaa2180c6042e4c0446b930339c2d6f3340aa506228c727872f8cf5ded41"
    }

    @Test
    fun encodedStateHasNoEmailField() {
        val text = LocalState(deviceId = "d1", userId = USER, identityPhase = "signed-in").encode()
        assertFalse(text.contains("email", ignoreCase = true), text)
    }

    @Test
    fun stateFromAnOlderBuildStillDecodes() {
        val old = """{"schemaVersion":1,"deviceId":"d1","userId":"$USER","$OLD_FIELD":"$OLD_DIGEST",""" +
            """"identityPhase":"signed-in"}"""
        val state = LocalState.decode(old)
        assertEquals(USER, state.userId)
        assertEquals("d1", state.deviceId)
        assertFalse(state.encode().contains(OLD_FIELD), "the old digest is dropped on the next save")
    }
}
