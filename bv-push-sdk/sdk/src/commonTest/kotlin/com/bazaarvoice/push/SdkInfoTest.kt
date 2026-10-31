package com.bazaarvoice.push

import kotlin.test.Test
import kotlin.test.assertTrue

class SdkInfoTest {
    @Test
    fun versionIsSemVer() {
        // x.y.z or x.y.z-rc.n; the value itself is set only in SdkInfo.
        assertTrue(Regex("""\d+\.\d+\.\d+(-rc\.\d+)?""").matches(SdkInfo.VERSION), SdkInfo.VERSION)
    }
}
