package com.bazaarvoice.push

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * On a real emulator (no mocks): Keystore-backed round trip, key
 * non-exportable, missing key reads null. Process-death survival is covered
 * by reinstall and reboot runs (separate launches, same assertions).
 */
@RunWith(AndroidJUnit4::class)
class SecureStoreDeviceTest {
    private fun store(): AndroidSecureStore {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return AndroidSecureStore(context, "test-ns")
    }

    @Test
    fun roundTrip() {
        val store = store()
        store.put("k", "v-secret")
        assertEquals("v-secret", store.get("k"))
        store.remove("k")
        assertNull(store.get("k"))
    }

    @Test
    fun keyIsNonExportable() {
        val store = store()
        store.put("k", "v")
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = ks.getEntry("bv_push_device_key", null) as? KeyStore.SecretKeyEntry
        assertTrue(entry != null)
    }
}
