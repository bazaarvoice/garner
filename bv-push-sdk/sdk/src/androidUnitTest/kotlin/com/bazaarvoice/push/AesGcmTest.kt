package com.bazaarvoice.push

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.crypto.KeyGenerator

/**
 * On the JVM: the AES-GCM envelope shared by both secure-
 * store tiers runs against real JCA (no test doubles of any kind). The tier
 * selection itself is covered on device (healthy tier) plus the collision
 * characterization in PlatformDeviceTest.
 */
@RunWith(JUnit4::class)
class AesGcmTest {
    private fun key(): javax.crypto.SecretKey {
        val gen = KeyGenerator.getInstance("AES")
        gen.init(256)
        return gen.generateKey()
    }

    @Test
    fun roundTrip() {
        val key = key()
        val plain = "device-secret-value".toByteArray(Charsets.UTF_8)
        assertArrayEquals(plain, AesGcm.decrypt(key, AesGcm.encrypt(key, plain)))
        assertArrayEquals(ByteArray(0), AesGcm.decrypt(key, AesGcm.encrypt(key, ByteArray(0))))
    }

    @Test
    fun wrongKeyFails() {
        val blob = AesGcm.encrypt(key(), "x".toByteArray())
        assertThrows(Exception::class.java) { AesGcm.decrypt(key(), blob) }
    }

    @Test
    fun tamperedBlobFails() {
        val blob = AesGcm.encrypt(key(), "x".toByteArray())
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0xFF).toByte()
        assertThrows(Exception::class.java) { AesGcm.decrypt(key(), blob) }
    }

    @Test
    fun truncatedBlobFails() {
        assertThrows(Exception::class.java) { AesGcm.decrypt(key(), ByteArray(5)) }
    }
}
