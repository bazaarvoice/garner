package com.bazaarvoice.push

import com.bazaarvoice.push.device.DeviceInfoProvider
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

/** Real device facts from UIKit/NSBundle. */
public class IosDeviceInfo : DeviceInfoProvider {
    override val platform: String = "IOS"
    override val appIdentifier: String
        get() = AppIdentifierResolver.resolve()
            ?: NSBundle.mainBundle.bundleIdentifier.orEmpty()
    override val model: String
        get() = UIDevice.currentDevice.model
    override val osVersion: String
        get() = UIDevice.currentDevice.systemVersion
    override val appVersion: String?
        get() = NSBundle.mainBundle
            .objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
}
