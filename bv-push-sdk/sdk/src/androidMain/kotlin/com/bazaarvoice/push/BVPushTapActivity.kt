package com.bazaarvoice.push

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Invisible trampoline (`exported=false`, translucent, no history, excluded
 * from recents). Target of an immutable explicit PendingIntent built from two
 * intents: the host's launch intent, then this activity — so a cold-start tap
 * lands in the app, and closing the browser tab returns to the app.
 */
public class BVPushTapActivity : Activity() {
    public companion object {
        public const val EXTRA_NOTIFICATION_ID: String = "bv_push_notification_id"
        public const val EXTRA_REVIEW_URL: String = "bv_push_review_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_NOTIFICATION_ID)
        val url = intent.getStringExtra(EXTRA_REVIEW_URL)
        if (id != null) BVPush.openedFromTap(this, id, url)
        finish()
    }
}
