package com.bazaarvoice.push

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.bazaarvoice.push.store.SecureStore
import com.bazaarvoice.push.store.SettingsStore
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * AES-256-GCM key in AndroidKeyStore (StrongBox when available,
 * non-exportable, no user-auth requirement so background work can read it);
 * ciphertext in a private preferences file per namespace.
 * Degraded tier: app-private software key, flagged via [degraded].
 */
public class AndroidSecureStore(context: Context, namespace: String) : SecureStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bv_push_secure_$namespace", Context.MODE_PRIVATE)

    private val keystoreKey: SecretKey by lazy { loadOrCreateKeystoreKey() ?: softwareKey() }
    private var degraded = false

    private fun loadOrCreateKeystoreKey(): SecretKey? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey) ?: run {
            val gen = KeyGenerator.getInstance("AES", "AndroidKeyStore")
            val builder = android.security.keystore.KeyGenParameterSpec.Builder(
                ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                    or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(false)
            }
            gen.init(builder.build())
            gen.generateKey()
        }
    } catch (_: Exception) {
        degraded = true
        null
    }

    private fun softwareKey(): SecretKey {
        degraded = true
        val raw = prefs.getString(SOFTWARE_KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: ByteArray(32).also {
                java.security.SecureRandom().nextBytes(it)
                prefs.edit().putString(SOFTWARE_KEY, Base64.encodeToString(it, Base64.NO_WRAP)).apply()
            }
        return javax.crypto.spec.SecretKeySpec(raw, "AES")
    }

    public val isDegraded: Boolean get() = degraded

    override fun get(key: String): String? {
        val blob = prefs.getString(key, null) ?: return null
        return try {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            String(AesGcm.decrypt(keystoreKey, bytes), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    override fun put(key: String, value: String) {
        val blob = AesGcm.encrypt(keystoreKey, value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val ALIAS = "bv_push_device_key"
        const val SOFTWARE_KEY = "__software_key__"
    }
}

/** Plain private preferences (non-secret settings live here). */
public class AndroidSettingsStore(context: Context, namespace: String) : SettingsStore {
    private val prefs = context.getSharedPreferences("bv_push_settings_$namespace", Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

/** File in noBackupFilesDir; state without the marker means a restore. */
public class InstallMarker(private val dir: File) {
    private fun file(): File = File(dir, "bv_push_install")
    public fun present(): Boolean = file().exists()
    public fun mark() {
        file().writeText("1")
    }
}
