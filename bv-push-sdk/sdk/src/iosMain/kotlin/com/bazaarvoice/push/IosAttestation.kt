package com.bazaarvoice.push

import com.bazaarvoice.push.crypto.Crypto
import com.bazaarvoice.push.device.AttestationProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.DeviceCheck.DCAppAttestService
import platform.DeviceCheck.DCErrorDomain
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSUserDefaults
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Security.SecItemCopyMatching
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnAttributes

/** App Attest failed in a way a later retry may fix (network, Apple server, key). */
public class AppAttestException(message: String) : Exception(message)

/**
 * `DCAppAttestService`: a new key per registration, attested with
 * clientDataHash = SHA-256(registration nonce). The evidence is the base64 CBOR
 * attestation object the backend verifies (rpIdHash = SHA-256 of TEAMID.bundleId).
 * Not supported (simulator, or no App Attest entitlement) → sentinel. Other
 * failures throw, so the coordinator parks registration and retries later.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
public class AppAttestAttestor : AttestationProvider {

    override suspend fun attest(nonce: String): AttestationProvider.Evidence {
        val service = DCAppAttestService.sharedService
        if (!service.supported) return AttestationProvider.Evidence.Unsupported
        val keyId = when (val key = generateKey(service)) {
            null -> return AttestationProvider.Evidence.Unsupported
            else -> key
        }
        val clientDataHash = Crypto.sha256(nonce.encodeToByteArray()).toNSData()
        val attestation = attestKey(service, keyId, clientDataHash)
            ?: return AttestationProvider.Evidence.Unsupported
        return AttestationProvider.Evidence.Token(attestation.base64EncodedStringWithOptions(0uL))
    }

    /** Null when App Attest is not available to this app. */
    private suspend fun generateKey(service: DCAppAttestService): String? =
        suspendCancellableCoroutine { cont ->
            service.generateKeyWithCompletionHandler { keyId: String?, error: NSError? ->
                when {
                    keyId != null -> cont.resume(keyId)
                    error.isFeatureUnsupported() -> cont.resume(null)
                    else -> cont.resumeWithException(AppAttestException(error.describe("generateKey")))
                }
            }
        }

    /** Null when App Attest is not available to this app. */
    private suspend fun attestKey(service: DCAppAttestService, keyId: String, clientDataHash: NSData): NSData? =
        suspendCancellableCoroutine { cont ->
            service.attestKey(keyId, clientDataHash) { attestation: NSData?, error: NSError? ->
                when {
                    attestation != null -> cont.resume(attestation)
                    error.isFeatureUnsupported() -> cont.resume(null)
                    else -> cont.resumeWithException(AppAttestException(error.describe("attestKey")))
                }
            }
        }

    private fun NSError?.isFeatureUnsupported(): Boolean =
        this != null && domain == DCErrorDomain && code == DC_ERROR_FEATURE_UNSUPPORTED

    private fun NSError?.describe(step: String): String =
        "App Attest $step failed: ${this?.domain ?: "unknown"} ${this?.code ?: 0L} ${this?.localizedDescription.orEmpty()}"

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

    private companion object {
        /** DCErrorFeatureUnsupported. */
        const val DC_ERROR_FEATURE_UNSUPPORTED = 1L
    }
}

/**
 * `TEAMID.bundleId`: the Info.plist `BVPushAppIdentifier` override
 * when it has that form (10-character Team ID, a dot, a bundle id); otherwise
 * the Team ID of this app's default Keychain access group joined with the main
 * bundle id. Null when neither is known (for example an unsigned build).
 */
public object AppIdentifierResolver {
    private val APP_IDENTIFIER = Regex("^[A-Z0-9]{10}\\..+$")
    private val TEAM_ID = Regex("^[A-Z0-9]{10}$")
    private const val PROBE_SERVICE = "com.bazaarvoice.push.probe"
    private const val PROBE_ACCOUNT = "team-id-probe"
    private const val ACCESS_GROUP = "agrp"

    public fun resolve(): String? {
        val override = (NSBundle.mainBundle.objectForInfoDictionaryKey("BVPushAppIdentifier") as? String)?.trim()
        if (override != null && APP_IDENTIFIER.matches(override)) return override
        val bundleId = NSBundle.mainBundle.bundleIdentifier ?: return null
        return teamId()?.let { "$it.$bundleId" }
    }

    /** The Keychain reports the access group of an item it stored; write a probe item first. */
    private fun teamId(): String? {
        accessGroup()?.let { return it }
        KeychainSecureStore(PROBE_SERVICE).put(PROBE_ACCOUNT, "1")
        return accessGroup()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun accessGroup(): String? = memScoped {
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 5, null, null) ?: return null
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, cfString(PROBE_SERVICE))
        CFDictionaryAddValue(query, kSecAttrAccount, cfString(PROBE_ACCOUNT))
        CFDictionaryAddValue(query, kSecReturnAttributes, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        if (SecItemCopyMatching(query, result.ptr) != errSecSuccess) return null
        val attributes = CFBridgingRelease(result.value) as? Map<*, *> ?: return null
        val group = attributes[ACCESS_GROUP] as? String ?: return null
        group.substringBefore('.').takeIf { TEAM_ID.matches(it) }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun MemScope.cfString(value: String): CFTypeRef? =
        CFStringCreateWithCString(kCFAllocatorDefault, value.cstr.getPointer(this), kCFStringEncodingUTF8)
}

/** Flag in standard UserDefaults (wiped on uninstall); Keychain without it means reinstall. */
public class IosInstallMarker {
    private val defaults = NSUserDefaults.standardUserDefaults
    public fun present(): Boolean = defaults.boolForKey("bv_push_installed")
    public fun mark() {
        defaults.setBool(true, "bv_push_installed")
    }
}
