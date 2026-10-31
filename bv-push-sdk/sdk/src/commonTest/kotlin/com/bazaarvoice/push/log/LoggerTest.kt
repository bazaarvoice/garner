package com.bazaarvoice.push.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LoggerTest {
    // Redaction.
    @Test
    fun redactsSecrets() {
        val out = Logger.redact(
            "deviceApiKey=abc123 login authToken=tok refreshToken=r " +
                "https://example.com/r/1?token=abc user@x.com",
        )
        assertFalse(out.contains("abc123"))
        assertFalse(out.contains("tok"))
        assertFalse(out.contains("?token=abc"))
    }
}
