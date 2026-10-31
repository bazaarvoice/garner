package com.bazaarvoice.push

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bazaarvoice.push.events.BVPushEvent
import com.bazaarvoice.push.push.ParsedPush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * On a real emulator: restore purge, quiet-hours hold and release through
 * the real WorkManager, manifest auto-init. Slow by design (the quiet-hours
 * test waits out a real ~2-minute worker delay). Needs a Garner backend on
 * the emulator host at :8082 (10.0.2.2).
 */
@RunWith(AndroidJUnit4::class)
class LifecycleDeviceTest {
    private fun context(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private fun poll(timeoutMs: Long = 30000, what: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (runCatching { what() }.getOrDefault(false)) return
            Thread.sleep(1000)
        }
        assertTrue("timed out waiting for condition", what())
    }

    /** BVPush.init is async and the singleton is shared with the process-start
     * auto-init: only proceed once a write through the facade's own stores
     * round-trips, proving the fields belong to a completed init. */
    private fun awaitStores(timeoutMs: Long = 30000) {
        // Re-issue the write each round: early calls land before init assigns
        // the stores and are silently dropped.
        poll(timeoutMs) {
            BVPush.setQuietHours(1, 2, 3, 4)
            BVPush.quietWindow() == com.bazaarvoice.push.push.QuietWindow(1, 2, 3, 4)
        }
    }

    // The App Startup initializer attached the app and auto-inited
    // from the test APK's APP_ID meta-data (10000002) at process start.
    @Test
    fun manifestAutoInit() {
        poll { BVPush.state.value.initialized }
        assertTrue(BVPush.state.value.initialized)
    }

    // Credentials without the install marker (an Auto Backup
    // restore) are purged on the next init; the marker is re-created and no
    // stale device-id survives.
    @Test
    fun restorePurge() {
        val context = context()
        val namespace = "PRODUCTION_10000044"
        val marker = File(context.noBackupFilesDir, "bv_push_install")
        // Arrange a "restored" state: credentials present, marker absent.
        AndroidSecureStore(context, namespace).put("device-id", "stale-device")
        marker.delete()
        assertTrue(!marker.exists())

        BVPush.init(context, 10000044)
        // Fresh store handles each read: the pre-purge objects still see the
        // deleted files through their in-memory cache.
        poll(timeoutMs = 30000) {
            marker.exists() &&
                AndroidSecureStore(context, namespace).get("device-id") == null
        }
        assertTrue(marker.exists())
        assertNull(AndroidSecureStore(context, namespace).get("device-id"))
    }

    // A push inside the quiet window is held (encrypted) and shown
    // by the real WorkManager at the window end. The device links first:
    // policy suppresses logged-out devices before quiet evaluation.
    @Test
    fun heldNotificationReleasedByWorker() {
        val context = context()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
        Thread.sleep(1000)

        BVPush.init(
            context, 10000043,
            com.bazaarvoice.push.config.BVPushOptions(
                baseUrlOverride = "http://10.0.2.2:8082",
                allowInsecureLocalhost = true,
                autoFetchFcmToken = false,
            ),
        )
        awaitStores()
        // No Firebase in the test APK: hand the SDK a token, because it logs in
        // only after register → push token.
        BVPush.setPushToken("quiet-test-fcm-token")
        BVPush.linkIdentity("quiet-user", "consumer@example.com")
        poll(timeoutMs = 180000) { BVPush.diagnostics().hasSession }

        val cal = java.util.Calendar.getInstance()
        val nowMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
            cal.get(java.util.Calendar.MINUTE)
        // Window from an hour ago until ~2 min out: covers now at every hour,
        // including across midnight (overnight branch).
        val startMinutes = (nowMinutes - 60 + 1440) % 1440
        val endMinutes = (nowMinutes + 2) % 1440
        BVPush.setQuietHours(
            startMinutes / 60, startMinutes % 60, endMinutes / 60, endMinutes % 60)

        val seen = CopyOnWriteArrayList<BVPushEvent>()
        val job = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            BVPush.events.collect { seen.add(it) }
        }
        // Hot flow without replay: let the subscription establish first.
        Thread.sleep(2000)
        try {
            val data = mapOf(
                "bv_source" to "push_sdk",
                "correspondence_id" to "held-1",
                "title" to "Held title",
                "body" to "Held body",
            )
            BVPush.handleRemoteMessage(context, data)
            poll(timeoutMs = 30000) {
                seen.any { it is BVPushEvent.NotificationHeld && it.id == "held-1" }
            }
            val manager = context.getSystemService(NotificationManager::class.java)!!
            assertTrue(manager.activeNotifications.none {
                it.notification.extras.getString("android.title") == "Held title"
            })
            // Real worker delay (~2 min) plus scheduling slack.
            poll(timeoutMs = 240000) {
                manager.activeNotifications.any {
                    it.notification.extras.getString("android.title") == "Held title"
                }
            }
            manager.cancel(AndroidNotificationBuilder.TAG, "held-1".hashCode())
        } finally {
            job.cancel()
            BVPush.disableQuietHours()
        }
    }
}
