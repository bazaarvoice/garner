package com.bazaarvoice.push

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Resolves which service receives `com.google.firebase.MESSAGING_EVENT`.
 * Warns when it is not BV's and the host has never forwarded a message.
 */
public object FcmServiceConflictDetector {
    public fun hostingService(context: Context): String? {
        val intent = Intent("com.google.firebase.MESSAGING_EVENT").setPackage(context.packageName)
        val services = context.packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA)
        return services.firstOrNull()?.serviceInfo?.name
    }

    public fun isOurs(context: Context): Boolean =
        hostingService(context)?.endsWith("BVFirebaseMessagingService") == true
}
