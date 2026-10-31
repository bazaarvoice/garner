package com.bazaarvoice.push

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * androidx.browser Custom Tabs; fallback ACTION_VIEW; no browser → false.
 * `<queries>` entries for the Custom Tabs service and
 * HTTPS browsing live in the library manifest.
 */
public object CustomTabsReviewPresenter {
    public fun present(context: Context, url: String): Boolean {
        return try {
            val intent = CustomTabsIntent.Builder().setShowTitle(true).build()
            intent.launchUrl(context, Uri.parse(url))
            true
        } catch (_: Exception) {
            try {
                val view = android.content.Intent(
                    android.content.Intent.ACTION_VIEW, Uri.parse(url),
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(view)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
