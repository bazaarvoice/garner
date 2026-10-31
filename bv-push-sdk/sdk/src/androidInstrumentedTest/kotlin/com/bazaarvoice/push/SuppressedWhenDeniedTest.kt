package com.bazaarvoice.push

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bazaarvoice.push.push.ParsedPush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Notifications denied. The host revokes POST_NOTIFICATIONS with adb BEFORE
 * this class runs, so the process starts denied and no mid-run revoke kills
 * it; the host re-grants after. Asserts the production guard posts nothing.
 */
@RunWith(AndroidJUnit4::class)
class SuppressedWhenDeniedTest {
    @Test
    fun revokedPermissionSuppressesPost() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)!!
        // Precondition comes from host orchestration; report skipped (not
        // passed) when it is unmet.
        org.junit.Assume.assumeFalse(
            "needs POST_NOTIFICATIONS revoked from the host (adb) before this class runs",
            manager.areNotificationsEnabled(),
        )
        val parsed = ParsedPush.BvPush(
            notificationId = "plat-test-denied",
            title = "Title",
            body = "Body",
            imageUrl = null,
            reviewUrl = "https://example.com/r/1",
            ctaText = null,
        )
        val result = AndroidNotificationBuilder.show(context, 9102, parsed, null)
        assertEquals(AndroidNotificationBuilder.PostResult.SUPPRESSED_DENIED, result)
        assertTrue(manager.activeNotifications.none { it.id == 9102 })
    }
}
