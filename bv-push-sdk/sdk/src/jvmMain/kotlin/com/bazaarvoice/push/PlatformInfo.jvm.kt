package com.bazaarvoice.push

/** JVM actual, so commonTest runs on any JVM (Linux CI included). */
public actual object PlatformInfo {
    public actual val name: String = "jvm"
}
