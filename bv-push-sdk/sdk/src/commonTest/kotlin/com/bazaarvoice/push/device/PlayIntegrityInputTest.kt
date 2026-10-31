package com.bazaarvoice.push.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayIntegrityInputTest {

    private companion object {
        const val REGISTER_NONCE = "register:1729834798100"
        const val REGISTER_NONCE_ENCODED = "cmVnaXN0ZXI6MTcyOTgzNDc5ODEwMA=="
        /** Bytes that need `_` in the URL-safe alphabet (`/` in the standard one). */
        const val URL_SAFE_INPUT = "ab?>"
        const val URL_SAFE_ENCODED = "YWI_Pg=="
        const val PROJECT_NUMBER = 123456789012L
        const val PROJECT_NUMBER_TEXT = "123456789012"
        /** Play Integrity accepts these characters only. */
        val WEB_SAFE = Regex("^[A-Za-z0-9_=-]+$")
    }

    @Test
    fun nonceIsBase64OfTheRawValue() {
        assertEquals(REGISTER_NONCE_ENCODED, PlayIntegrityInput.nonce(REGISTER_NONCE))
    }

    @Test
    fun nonceUsesTheUrlSafeAlphabet() {
        assertEquals(URL_SAFE_ENCODED, PlayIntegrityInput.nonce(URL_SAFE_INPUT))
    }

    @Test
    fun registerNonceHasOnlyWebSafeCharacters() {
        val nonce = PlayIntegrityInput.nonce(REGISTER_NONCE)
        assertTrue(WEB_SAFE.matches(nonce), nonce)
        assertTrue(':' !in nonce, nonce)
    }

    @Test
    fun cloudProjectNumberReadsDigits() {
        assertEquals(PROJECT_NUMBER, PlayIntegrityInput.cloudProjectNumber(PROJECT_NUMBER_TEXT))
        assertEquals(PROJECT_NUMBER, PlayIntegrityInput.cloudProjectNumber(" $PROJECT_NUMBER_TEXT "))
    }

    @Test
    fun cloudProjectNumberAbsentOrBlankIsNone() {
        assertNull(PlayIntegrityInput.cloudProjectNumber(null))
        assertNull(PlayIntegrityInput.cloudProjectNumber(""))
        assertNull(PlayIntegrityInput.cloudProjectNumber("   "))
    }

    @Test
    fun cloudProjectNumberRejectsNonDigits() {
        assertNull(PlayIntegrityInput.cloudProjectNumber("1.23456789E11"))
        assertNull(PlayIntegrityInput.cloudProjectNumber("-123456789012"))
        assertNull(PlayIntegrityInput.cloudProjectNumber("my-firebase-project"))
    }

    @Test
    fun cloudProjectNumberRejectsZeroAndOverflow() {
        assertNull(PlayIntegrityInput.cloudProjectNumber("0"))
        assertNull(PlayIntegrityInput.cloudProjectNumber("99999999999999999999"))
    }
}
