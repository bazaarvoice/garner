package com.bazaarvoice.push

import com.bazaarvoice.push.store.SecureStore
import com.bazaarvoice.push.store.SettingsStore
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSUserDefaults
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Generic-password items per namespace service,
 * `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` (readable in the
 * background, never migrated), no iCloud sync, no access group (app only).
 * Correct Core Foundation dictionary construction.
 */
@OptIn(ExperimentalForeignApi::class)
public class KeychainSecureStore(private val service: String) : SecureStore {

    override fun get(key: String): String? = memScoped {
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 6, null, null)!!
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, cfString(service))
        CFDictionaryAddValue(query, kSecAttrAccount, cfString(key))
        CFDictionaryAddValue(query, kSecReturnData, platform.CoreFoundation.kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CPointerVarOf<CPointer<out CPointed>>>()
        if (SecItemCopyMatching(query, result.ptr) != errSecSuccess) return null
        val data = result.value as CFDataRef
        val length = CFDataGetLength(data).toInt()
        val bytes = CFDataGetBytePtr(data)?.reinterpret<ByteVar>() ?: return null
        bytes.readBytes(length).decodeToString()
    }

    override fun put(key: String, value: String) {
        delete(key)
        memScoped {
            val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 6, null, null)!!
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, cfString(service))
            CFDictionaryAddValue(query, kSecAttrAccount, cfString(key))
            CFDictionaryAddValue(query, kSecValueData, cfData(value))
            CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            CFDictionaryAddValue(query, kSecAttrSynchronizable, platform.CoreFoundation.kCFBooleanFalse)
            SecItemAdd(query, null)
        }
    }

    override fun remove(key: String) {
        delete(key)
    }

    private fun delete(key: String) {
        memScoped {
            val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 4, null, null)!!
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, cfString(service))
            CFDictionaryAddValue(query, kSecAttrAccount, cfString(key))
            SecItemDelete(query as CFDictionaryRef)
        }
    }

    private fun MemScope.cfString(value: String): CFTypeRef? =
        CFStringCreateWithCString(kCFAllocatorDefault, value.cstr.getPointer(this), kCFStringEncodingUTF8)

    private fun MemScope.cfData(value: String): CFTypeRef? {
        val bytes = value.encodeToByteArray()
        return bytes.usePinned {
            CFDataCreate(kCFAllocatorDefault, it.addressOf(0).reinterpret<UByteVar>(), bytes.size.toLong())
        }
    }
}

/** UserDefaults suite for the App Group shared by the app and the NSE. */
public class AppGroupSettingsStore(suiteName: String) : SettingsStore {
    private val defaults = NSUserDefaults(suiteName = suiteName)

    override fun get(key: String): String? = defaults.stringForKey(key)
    override fun put(key: String, value: String) {
        defaults.setObject(value, key)
    }
    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}
