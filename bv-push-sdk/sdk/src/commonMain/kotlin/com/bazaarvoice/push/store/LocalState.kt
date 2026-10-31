package com.bazaarvoice.push.store

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Versioned local state. Namespace = appId.
 * Secrets live in [SecureStore]; the synced FCM token hash in settings.
 *
 * The consumer's email is not here: the SDK holds the plain address in memory only and keeps
 * the backend's digest of it in [SecureStore]. State written by older builds still holds
 * `emailAddressHash`; it decodes fine (unknown keys are ignored) and that field is dropped.
 */
@Serializable
public data class LocalState(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val deviceId: String? = null,
    val syncedTokenHash: String? = null,
    val userId: String? = null,
    val identityPhase: String = "anonymous",
    val desiredOptedOut: Boolean = false,
    val serverOptedOut: Boolean? = null,
    val pendingLogout: Boolean = false,
    val parkUntilMillis: Long = 0L,
    val blockedReason: String? = null,
    val blockedUntilMillis: Long = 0L,
    /** Refusals in a row since the last success; see [com.bazaarvoice.push.sync.RejectionPolicy]. */
    val rejectedAttempts: Int = 0,
) {
    public companion object {
        public const val CURRENT_SCHEMA: Int = 1

        private val json = Json { ignoreUnknownKeys = true }

        public fun decode(raw: String?): LocalState {
            if (raw.isNullOrBlank()) return LocalState()
            return try {
                json.decodeFromString<LocalState>(raw)
            } catch (_: Exception) {
                LocalState()
            }
        }
    }

    public fun encode(): String = LocalState.json.encodeToString(serializer(), this)
}
