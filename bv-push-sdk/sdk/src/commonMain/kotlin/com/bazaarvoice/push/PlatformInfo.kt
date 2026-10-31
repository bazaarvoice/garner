package com.bazaarvoice.push

/** Platform identity supplied per target (androidMain / iosMain). */
public expect object PlatformInfo {
    public val name: String
}
