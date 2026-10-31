package com.bazaarvoice.push.review

import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewLinkValidatorTest {
    // HTTPS-only action URL with optional allowlist.
    @Test
    fun routing() {
        assertEquals(ReviewLinkValidator.TapOutcome.OPEN_REVIEW,
            ReviewLinkValidator.route("https://example.com/r/1", hostHandled = false))
        assertEquals(ReviewLinkValidator.TapOutcome.OPEN_APP,
            ReviewLinkValidator.route("http://example.com/r/1", hostHandled = false))
        assertEquals(ReviewLinkValidator.TapOutcome.OPEN_APP,
            ReviewLinkValidator.route(null, hostHandled = false))
        assertEquals(ReviewLinkValidator.TapOutcome.HOST_HANDLED,
            ReviewLinkValidator.route("https://example.com/r/1", hostHandled = true))
        assertEquals(ReviewLinkValidator.TapOutcome.OPEN_APP,
            ReviewLinkValidator.route("https://evil.com/r", hostHandled = false, allowedHosts = setOf("example.com")))
    }
}
