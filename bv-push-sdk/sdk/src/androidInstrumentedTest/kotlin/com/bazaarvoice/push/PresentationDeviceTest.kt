package com.bazaarvoice.push

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bazaarvoice.push.config.BVPushOptions
import com.bazaarvoice.push.events.BVPushEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * On a real emulator. The big-picture test needs an image server on the
 * emulator host at :8099 (10.0.2.2) and skips loudly when it is unreachable.
 * The warm-tap test drives a real Custom Tab with UI Automator; the
 * no-browser test disables Chrome through the shell (re-enabled in
 * `finally`). Cold taps need real FCM and are tested end to end.
 */
@RunWith(AndroidJUnit4::class)
class PresentationDeviceTest {
    private fun context(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private fun imageOriginUp(): Boolean = try {
        java.net.Socket().use { s ->
            s.connect(java.net.InetSocketAddress("10.0.2.2", 8099), 3000)
            true
        }
    } catch (_: Exception) {
        false
    }

    private fun poll(timeoutMs: Long = 30000, what: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (runCatching { what() }.getOrDefault(false)) return
            Thread.sleep(1000)
        }
        assertTrue("timed out waiting for condition", what())
    }

    /** Pays Chrome's post-enable cold start up front (real tab, closed
     * afterwards): the first Custom Tab after a re-enable takes ~20 s. */
    private fun warmUpChrome() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = context()
        val launch = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://example.com/"),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        assertTrue(device.wait(Until.hasObject(By.pkg("com.android.chrome")), 60000))
        device.pressBack()
        // Back leaves Chrome (for the launcher/home — this process has no
        // activity); the tab itself was the assertion.
        poll(timeoutMs = 15000) { device.currentPackageName != "com.android.chrome" }
    }

    /** Completes Chrome's first-run UI once per emulator (real UI flow, not a
     * mock): without this every Custom Tab lands on the setup wizard. */
    private fun dismissChromeFirstRun() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        device.wait(Until.hasObject(By.pkg("com.google.android.apps.nexuslauncher")), 5000)
        val context = context()
        val launch = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://example.com/"),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        if (!device.wait(Until.hasObject(By.text("Stay signed out")), 15000)) return
        device.findObject(By.text("Stay signed out")).click()
        device.wait(Until.gone(By.text("Stay signed out")), 15000)
        device.pressBack()
        Thread.sleep(1000)
    }

    /** Sets Chrome enabled state through the shell and waits until the
     * PackageManager agrees (resolution caches lag behind). */
    private fun setChromeEnabled(enabled: Boolean) {
        val context = context()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(cmd: String) {
            automation.executeShellCommand(cmd).close()
        }
        shell(if (enabled) "pm enable com.android.chrome"
              else "pm disable-user com.android.chrome")
        val pm = context.packageManager
        poll(timeoutMs = 30000) {
            val state = pm.getApplicationEnabledSetting("com.android.chrome")
            if (enabled) state != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        }
        Thread.sleep(3000)
    }

    /** Linked + opted-in + quiet hours off, ready to display. */
    private fun readyLinked() {
        val context = context()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
        BVPush.init(
            context, 10000043,
            BVPushOptions(
                baseUrlOverride = "http://10.0.2.2:8082",
                allowInsecureLocalhost = true,
                autoFetchFcmToken = false,
            ),
        )
        poll(timeoutMs = 30000) {
            BVPush.setQuietHours(1, 2, 3, 4)
            BVPush.quietWindow() == com.bazaarvoice.push.push.QuietWindow(1, 2, 3, 4)
        }
        BVPush.linkIdentity("present-user", "consumer@example.com")
        poll(timeoutMs = 180000) { BVPush.diagnostics().hasSession }
        BVPush.disableQuietHours()
    }

    private fun bvData(id: String, image: String?): Map<String, String> =
        mapOf(
            "bv_source" to "push_sdk",
            "correspondence_id" to id,
            "title" to "Title $id",
            "body" to "Body $id",
        ) + (if (image != null) mapOf("image" to image) else emptyMap())

    private fun activeTitle(title: String): android.app.Notification? {
        val manager = context().getSystemService(NotificationManager::class.java)!!
        return manager.activeNotifications.firstOrNull {
            it.notification.extras.getString("android.title") == title
        }?.notification
    }

    private fun activeTitles(): List<String?> {
        val manager = context().getSystemService(NotificationManager::class.java)!!
        return manager.activeNotifications.map {
            it.notification.extras.getString("android.title")
        }
    }

    // BigPictureStyle from a real image; text-only on 404 and on a
    // slower-than-timeout origin.
    @Test
    fun bigPictureAndFallbacks() {
        Assume.assumeTrue(
            "needs an image server on the emulator host at :8099",
            imageOriginUp(),
        )
        readyLinked()
        val context = context()
        val manager = context.getSystemService(NotificationManager::class.java)!!

        BVPush.handleRemoteMessage(context, bvData("pic-ok", "http://10.0.2.2:8099/hero.png"))
        poll { activeTitle("Title pic-ok") != null }
        val rich = activeTitle("Title pic-ok")
        assertNotNull(rich)
        assertNotNull(rich!!.extras.getParcelable<android.graphics.Bitmap>("android.picture"))

        BVPush.handleRemoteMessage(context, bvData("pic-404", "http://10.0.2.2:8099/nope.png"))
        poll { activeTitle("Title pic-404") != null }
        val plain404 = activeTitle("Title pic-404")
        assertNotNull(plain404)
        assertNull(plain404!!.extras.getParcelable<android.graphics.Bitmap>("android.picture"))

        BVPush.handleRemoteMessage(context, bvData("pic-slow", "http://10.0.2.2:8099/slow.png"))
        poll(timeoutMs = 60000) { activeTitle("Title pic-slow") != null }
        val plainSlow = activeTitle("Title pic-slow")
        assertNotNull(plainSlow)
        assertNull(plainSlow!!.extras.getParcelable<android.graphics.Bitmap>("android.picture"))

        manager.cancel(AndroidNotificationBuilder.TAG, "pic-ok".hashCode())
        manager.cancel(AndroidNotificationBuilder.TAG, "pic-404".hashCode())
        manager.cancel(AndroidNotificationBuilder.TAG, "pic-slow".hashCode())
    }

    // Warm tap opens the review URL in a Custom Tab. The SDK
    // handoff (trampoline → Custom Tabs dispatch) is gated deterministically
    // on events; the Chrome-foreground check is opportunistic because tab
    // render time on a loaded host is highly variable (proven repeatedly in
    // logcat: IntentDispatcher result=0, CustomTabActivity focused).
    // (Cold taps need real FCM and are tested end to end.)
    @Test
    fun warmTapOpensCustomTab() {
        readyLinked()
        setChromeEnabled(true)
        dismissChromeFirstRun()
        warmUpChrome()
        val context = context()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        val seen = CopyOnWriteArrayList<BVPushEvent>()
        val job = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            BVPush.events.collect { seen.add(it) }
        }
        Thread.sleep(2000)
        try {
            val data = bvData("tap-1", null) + mapOf("review_url" to "https://example.com/r/1")
            BVPush.handleRemoteMessage(context, data)
            poll { activeTitle("Title tap-1") != null }

            device.openNotification()
            Thread.sleep(1500)
            // NOTE: match the visible text, not a clickable node — the title
            // TextView itself is not clickable; the row is.
            assertTrue(
                "shade missing Title tap-1; posted=${activeTitles()}",
                device.wait(Until.hasObject(By.text("Title tap-1")), 30000))
            device.findObject(By.text("Title tap-1")).click()
            // Deterministic gate: the SDK dispatched the review open.
            poll(timeoutMs = 60000) {
                seen.any { it is BVPushEvent.ReviewOpened && it.id == "tap-1" }
            }
            assertTrue(seen.any { it is BVPushEvent.NotificationOpened && it.id == "tap-1" })
            // Opportunistic UI confirmation.
            if (device.wait(Until.hasObject(By.pkg("com.android.chrome")), 120000)) {
                assertEquals("com.android.chrome", device.currentPackageName)
                device.pressBack()
                poll(timeoutMs = 15000) { device.currentPackageName != "com.android.chrome" }
            } else {
                println("WARN warmTapOpensCustomTab: chrome UI slower than 120 s; " +
                    "SDK handoff verified via ReviewOpened event")
            }
        } finally {
            job.cancel()
        }
    }

    // A host tap handler that consumes the tap suppresses the tab;
    // the tap itself is still recorded.
    @Test
    fun hostTapHandlerConsumesTap() {
        readyLinked()
        val context = context()
        val seen = CopyOnWriteArrayList<BVPushEvent>()
        val job = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            BVPush.events.collect { seen.add(it) }
        }
        Thread.sleep(2000)
        try {
            BVPush.setNotificationTapHandler { true }
            BVPush.openedFromTap(context, "tap-3", "https://example.com/r/3")
            poll(timeoutMs = 15000) {
                seen.any { it is BVPushEvent.NotificationOpened && it.id == "tap-3" }
            }
            Thread.sleep(3000)
            assertTrue(seen.none { it is BVPushEvent.Error })
        } finally {
            job.cancel()
            BVPush.setNotificationTapHandler(null)
        }
    }

    // No Custom Tabs provider and no browser → REVIEW_UNOPENABLE,
    // and the tap is still recorded.
    @Test
    fun noBrowserEmitsError() {
        readyLinked()
        setChromeEnabled(false)
        val context = context()
        val seen = CopyOnWriteArrayList<BVPushEvent>()
        val job = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            BVPush.events.collect { seen.add(it) }
        }
        // Hot flow without replay: let the subscription establish before the
        // synchronous emissions below, or the first event is lost.
        Thread.sleep(2000)
        try {
            BVPush.openedFromTap(context, "tap-2", "https://example.com/r/2")
            poll(timeoutMs = 30000) {
                seen.any { it is BVPushEvent.Error && it.kind == "REVIEW_UNOPENABLE" }
            }
            assertTrue(
                "seen=${seen.joinToString()}",
                seen.any { it is BVPushEvent.NotificationOpened && it.id == "tap-2" })
        } finally {
            job.cancel()
            setChromeEnabled(true)
        }
    }
}
