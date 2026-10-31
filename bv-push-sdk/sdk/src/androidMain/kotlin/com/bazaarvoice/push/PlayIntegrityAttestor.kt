package com.bazaarvoice.push

import com.bazaarvoice.push.device.AttestationProvider
import com.bazaarvoice.push.device.PlayIntegrityInput
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityServiceException
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.model.IntegrityErrorCode
import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Classic Play Integrity request with the host app's Cloud project number and a
 * request-bound nonce. The nonce is sent URL-safe Base64 encoded,
 * as Play requires.
 *
 * The project number comes from the host app (manifest meta-data
 * [BVPush.CLOUD_PROJECT_NUMBER_KEY]); `null` sends the request without one, and Play
 * then uses the project linked to the app in Play Console.
 *
 * Outcomes: missing Play services/API → [Outcome.Unsupported] (sentinel);
 * transient → [Outcome.Transient], thrown by [attest] so the caller parks and retries;
 * any other Play error → [Outcome.Blocked]. Every failure writes one logcat line with
 * Play's error code (`adb logcat -s BVPush`).
 */
public class PlayIntegrityAttestor(
    private val context: Context,
    private val cloudProjectNumber: Long?,
) : AttestationProvider {

    private companion object {
        // private: a const in a companion becomes a static field with the property's own
        // visibility, so a public const would leak into the API (androidApiCheck).
        private const val TAG = "BVPush"
    }

    public sealed interface Outcome {
        public data class Token(val token: String) : Outcome
        public data object Unsupported : Outcome
        public data class Transient(val cause: Throwable) : Outcome
        public data class Blocked(val reason: String) : Outcome
    }

    public suspend fun attestDetailed(nonce: String): Outcome {
        val manager = try {
            IntegrityManagerFactory.create(context)
        } catch (_: Exception) {
            android.util.Log.w(TAG, "BVPush attestation unsupported: no integrity manager")
            return Outcome.Unsupported
        }
        val builder = IntegrityTokenRequest.builder()
            .setNonce(PlayIntegrityInput.nonce(nonce))
        if (cloudProjectNumber != null) builder.setCloudProjectNumber(cloudProjectNumber)
        val request = builder.build()
        return suspendCancellableCoroutine { cont ->
            manager.requestIntegrityToken(request)
                .addOnSuccessListener { response -> cont.resume(Outcome.Token(response.token())) }
                // Play reports its errors here, not by throwing: map them, never drop them.
                .addOnFailureListener { error -> cont.resume(outcomeOf(error)) }
        }
    }

    override suspend fun attest(nonce: String): AttestationProvider.Evidence =
        when (val outcome = attestDetailed(nonce)) {
            is Outcome.Token -> AttestationProvider.Evidence.Token(outcome.token)
            // Thrown so the coordinator parks and retries; a temporary failure never sends a sentinel.
            is Outcome.Transient -> throw outcome.cause
            is Outcome.Unsupported, is Outcome.Blocked -> AttestationProvider.Evidence.Unsupported
        }

    private fun outcomeOf(error: Exception): Outcome {
        if (error !is IntegrityServiceException) {
            android.util.Log.w(TAG, "BVPush attestation failed: ${error.javaClass.simpleName}")
            return Outcome.Unsupported
        }
        val code = error.errorCode
        val outcome = when (code) {
            IntegrityErrorCode.NETWORK_ERROR,
            IntegrityErrorCode.TOO_MANY_REQUESTS,
            IntegrityErrorCode.GOOGLE_SERVER_UNAVAILABLE,
            IntegrityErrorCode.INTERNAL_ERROR,
            IntegrityErrorCode.CLIENT_TRANSIENT_ERROR,
                -> Outcome.Transient(error)
            IntegrityErrorCode.API_NOT_AVAILABLE,
            IntegrityErrorCode.PLAY_SERVICES_NOT_FOUND,
            IntegrityErrorCode.PLAY_STORE_NOT_FOUND,
                -> Outcome.Unsupported
            else -> Outcome.Blocked(error.message ?: "integrity error $code")
        }
        android.util.Log.w(TAG, "BVPush attestation failed code=$code outcome=${outcome::class.simpleName}")
        return outcome
    }
}
