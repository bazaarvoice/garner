package com.bazaarvoice.push

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bazaarvoice.push.device.AttestationProvider
import com.bazaarvoice.push.device.ConnectivityMonitor
import com.bazaarvoice.push.device.PushTokenSource
import com.bazaarvoice.push.net.PushApiClient
import com.bazaarvoice.push.store.LocalState
import com.bazaarvoice.push.sync.Coordinator
import com.bazaarvoice.push.sync.PlannedCall
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Coordinator engine against a real local test backend (device leg):
 * production stores (Keystore), sentinel attestation (attestation-off
 * stack), real HTTP. App 10000043 is seeded for the test APK package.
 */
@RunWith(AndroidJUnit4::class)
class CoordinatorDeviceTest {
    private val appId = 10000043
    private val namespace = "PRODUCTION_$appId"
    private val email = "Consumer+Coord@Example.com"
    /** Where older SDK builds kept the plain address: must stay empty now. */
    private val oldEmailKey = "identity-email"
    private val emailHashKey = "identity-email-hash"
    private val sha256Hex = Regex("^[a-f0-9]{64}$")

    private fun coordinator(): Triple<Coordinator, AndroidSecureStore, AndroidSettingsStore> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val secure = AndroidSecureStore(context, namespace)
        val settings = AndroidSettingsStore(context, namespace)
        val client = PushApiClient("http://10.0.2.2:8082")
        val coordinator = Coordinator(
            appId = appId,
            client = client,
            secure = secure,
            settings = settings,
            attestation = object : AttestationProvider {
                override suspend fun attest(nonce: String) =
                    AttestationProvider.Evidence.Unsupported
            },
            // The SDK logs in only after register → push token, so the device needs a token.
            tokenSource = object : PushTokenSource {
                override suspend fun currentToken(): String? = "coord-device-test-fcm-token"
            },
            connectivity = object : ConnectivityMonitor {
                override val connected: Boolean = true
            },
            deviceInfo = AndroidDeviceInfo(context),
        )
        return Triple(coordinator, secure, settings)
    }

    @Test
    fun registerThenLoginThenRefresh() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Deterministic fresh namespace (Keystore AES key persists; entries do not).
        context.deleteSharedPreferences("bv_push_secure_$namespace")
        context.deleteSharedPreferences("bv_push_settings_$namespace")
        val (coordinator, secure, settings) = coordinator()
        assertNull("wipe failed", secure.get("device-id"))
        assertNull("wipe failed", settings.get("local-state"))

        // REGISTER, then SYNC_TOKEN: the token goes up before any login.
        val first = coordinator.reconcile(false)
        assertEquals("first=$first state=${settings.get("local-state")}",
            PlannedCall.SYNC_TOKEN, first)
        val deviceId = secure.get("device-id")
        assertNotNull(deviceId)
        assertNotNull(LocalState.decode(settings.get("local-state")).syncedTokenHash)

        // The plain address goes to the backend, which hashes it.
        coordinator.setDesiredIdentity("coord-user", email)
        // Held in memory only: neither store ever holds the address.
        assertNull(secure.get(oldEmailKey))
        assertFalse(settings.get("local-state").orEmpty().contains(email, ignoreCase = true))
        val second = coordinator.reconcile(false)
        // LOGIN then preference replay (unknown server preference → OPT_IN).
        assertEquals(PlannedCall.OPT_IN, second)
        // Session persisted: bearer + refresh + expiry + server link.
        assertNotNull(secure.get("auth-token"))
        assertNotNull(secure.get("refresh-token"))
        assertNotNull(secure.get("auth-expires-at"))
        assertEquals("coord-user", settings.get("server-link"))
        // The backend's answered digest is kept with the tokens, instead of the address.
        val emailHash = secure.get(emailHashKey)
        assertNotNull(emailHash)
        assertTrue("not a digest: $emailHash", sha256Hex.matches(emailHash!!))
        assertNull(secure.get(oldEmailKey))
        assertEquals("false", LocalState.decode(settings.get("local-state"))
            .serverOptedOut.toString())

        // Steady state: desired == server, nothing due → IDLE.
        val third = coordinator.reconcile(false)
        assertEquals(PlannedCall.IDLE, third)
        val state = LocalState.decode(settings.get("local-state"))
        assertEquals("coord-user", state.userId)

        // Both tokens gone and a new process (no address in memory): the next
        // reconcile logs in again with the stored digest alone.
        secure.remove("auth-token")
        secure.remove("refresh-token")
        secure.remove("auth-expires-at")
        val (restarted, _, _) = coordinator()
        restarted.reconcile(false)
        assertNotNull("no re-login by digest", secure.get("auth-token"))
        assertNotNull(secure.get("refresh-token"))
        assertEquals(emailHash, secure.get(emailHashKey))
        assertEquals("coord-user", settings.get("server-link"))

        // Unlink forgets the digest.
        restarted.clearDesiredIdentity()
        assertNull(secure.get(emailHashKey))
    }
}
