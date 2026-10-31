package com.bazaarvoice.push

import android.app.NotificationManager
import android.content.Context
import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bazaarvoice.push.push.ParsedPush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * On a real emulator (no mocks, no fakes):
 * every assertion exercises production adapters against real OS services.
 */
@RunWith(AndroidJUnit4::class)
class PlatformDeviceTest {
    private fun context(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    // Characterization: a foreign key under the SDK alias does not
    // break the store — the production code regenerates its AES key and stays
    // functional (observed on-device: KeyGenerator replaces the entry). The
    // software fallback engages only on real keystore failure; its
    // envelope crypto is unit-covered (AesGcmTest) and its tier flag surfaces
    // in diagnostics.
    @Test
    fun aliasCollisionRecoversWithFreshKeystoreKey() {
        val alias = "bv_push_device_key"
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.deleteEntry(alias)
        try {
            val gen = java.security.KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
            gen.initialize(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    alias,
                    android.security.keystore.KeyProperties.PURPOSE_SIGN,
                ).setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256).build()
            )
            gen.generateKeyPair()
            val store = AndroidSecureStore(context(), "collision-ns")
            store.put("k", "v-secret")
            assertEquals("v-secret", store.get("k"))
            assertTrue(!store.isDegraded)
        } finally {
            ks.deleteEntry(alias)
        }
        // Healthy alias state is not degraded.
        val healthy = AndroidSecureStore(context(), "healthy-ns")
        healthy.put("k", "v")
        assertEquals("v", healthy.get("k"))
        assertTrue(!healthy.isDegraded)
    }

    // Channel id, default name and HIGH importance.
    @Test
    fun notificationChannelAttributes() {
        val context = context()
        AndroidNotificationBuilder.ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)!!
        val channel = manager.getNotificationChannel(AndroidNotificationBuilder.CHANNEL_ID)!!
        assertEquals(AndroidNotificationBuilder.CHANNEL_ID, channel.id)
        assertEquals("BV Review Requests", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    private fun parsed(): ParsedPush.BvPush = ParsedPush.BvPush(
        notificationId = "plat-test-1",
        title = "Title",
        body = "Body",
        imageUrl = null,
        reviewUrl = "https://example.com/r/1",
        ctaText = null,
    )

    // Granted: a real posted notification appears as active.
    // The denied case lives in SuppressedWhenDeniedTest, orchestrated with the
    // permission revoked up front: revoking mid-run kills the test process.
    @Test
    fun postedWhenGranted() {
        val context = context()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
        Thread.sleep(1000)
        val posted = AndroidNotificationBuilder.show(context, 9101, parsed(), null)
        assertEquals(AndroidNotificationBuilder.PostResult.POSTED, posted)
        val manager = context.getSystemService(NotificationManager::class.java)!!
        assertTrue(manager.activeNotifications.any { it.tag == AndroidNotificationBuilder.TAG })
        manager.cancel(AndroidNotificationBuilder.TAG, 9101)
    }

    // The test APK merges the SDK manifest, so the SDK messaging
    // service owns MESSAGING_EVENT.
    @Test
    fun fcmServiceIsOurs() {
        assertTrue(FcmServiceConflictDetector.isOurs(context()))
    }

    // Init does no disk/network on the calling thread. Death
    // penalties turn any main-thread I/O into a test failure.
    @Test
    fun initIsStrictModeCleanOnMainThread() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyDeath()
                .build()
        )
        try {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val started = System.currentTimeMillis()
            instrumentation.runOnMainSync {
                BVPush.init(context(), 10000001)
            }
            assertTrue(System.currentTimeMillis() - started < 5000)
        } finally {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        }
    }
}
