package com.bazaarvoice.push

import android.content.Context
import androidx.startup.Initializer
import com.bazaarvoice.push.config.BVPushConfig
import com.bazaarvoice.push.config.BVPushOptions

/**
 * Captures the application context at process start without I/O.
 * Auto-initializes the SDK from manifest meta-data when
 * `com.bazaarvoice.push.APP_ID` is present (no-code mode).
 * An optional `com.bazaarvoice.push.BASE_URL` points it at a non-production
 * backend (e.g. QA); blank or absent keeps the production default.
 * An invalid App ID (not an 8-digit number) keeps the SDK dormant.
 */
public class BVPushInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        BVPush.attach(context.applicationContext as android.app.Application)
        val metaData = context.packageManager.getApplicationInfo(
            context.packageName, android.content.pm.PackageManager.GET_META_DATA,
        ).metaData
        val appId = metaData?.getInt("com.bazaarvoice.push.APP_ID", 0) ?: 0
        val baseUrl = metaData?.getString("com.bazaarvoice.push.BASE_URL")?.takeIf { it.isNotBlank() }
        if (BVPushConfig.isValidAppId(appId)) {
            BVPush.init(appId, BVPushOptions(baseUrlOverride = baseUrl))
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
