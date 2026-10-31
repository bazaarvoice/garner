package com.bazaarvoice.push

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.bazaarvoice.push.device.DeviceInfoProvider

/** Real device facts from PackageManager/Build. */
public class AndroidDeviceInfo(private val context: Context) : DeviceInfoProvider {
    override val platform: String = "ANDROID"
    override val appIdentifier: String get() = context.packageName
    override val model: String get() = Build.MODEL
    override val osVersion: String get() = Build.VERSION.RELEASE
    override val appVersion: String? get() = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
