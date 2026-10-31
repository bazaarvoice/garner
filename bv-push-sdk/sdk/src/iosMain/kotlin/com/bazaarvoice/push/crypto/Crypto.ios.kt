package com.bazaarvoice.push.crypto

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256

/** CommonCrypto through the Kotlin/Native platform library `platform.CoreCrypto`. */
@OptIn(ExperimentalForeignApi::class)
public actual object Crypto {
    public actual fun sha256(data: ByteArray): ByteArray {
        val out = ByteArray(CC_SHA256_DIGEST_LENGTH.toInt())
        data.usePinned { dataPinned ->
            out.usePinned { outPinned ->
                CC_SHA256(
                    dataPinned.start(),
                    data.size.convert(),
                    outPinned.addressOf(0).reinterpret<UByteVar>(),
                )
            }
        }
        return out
    }

    public actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArray(CC_SHA256_DIGEST_LENGTH.toInt())
        key.usePinned { keyPinned ->
            data.usePinned { dataPinned ->
                out.usePinned { outPinned ->
                    CCHmac(
                        kCCHmacAlgSHA256,
                        keyPinned.start(), key.size.convert(),
                        dataPinned.start(), data.size.convert(),
                        outPinned.addressOf(0),
                    )
                }
            }
        }
        return out
    }

    /** First byte, or null for an empty array (addressOf(0) needs an element). */
    private fun Pinned<ByteArray>.start(): CPointer<ByteVar>? = if (get().isEmpty()) null else addressOf(0)
}
