package com.bazaarvoice.push.sync

import com.bazaarvoice.push.store.LocalState

/**
 * What the SDK does when the backend refuses a call with a non-retryable answer (BLOCKED outcome,
 * e.g. 403 `ATTESTATION_REJECTED` or `APP_NOT_ACTIVE`).
 *
 * Such answers usually mean configuration is wrong, which a fix on the server or in the portal can
 * clear without touching the device. So the SDK tries again: the first refusals only park the
 * device for [RETRY_AFTER_MILLIS] and the next trigger (launch, foreground, token change or a
 * public call) retries. Refusal number [ATTEMPTS_BEFORE_BLOCK] in a row blocks calls for
 * [BLOCK_MILLIS], which keeps attestation quotas and the backend safe from a device that cannot
 * succeed. A `Retry-After` from the backend always wins over these defaults. Any success resets the
 * count.
 */
internal object RejectionPolicy {
    const val ATTEMPTS_BEFORE_BLOCK: Int = 3
    const val RETRY_AFTER_MILLIS: Long = 60_000L
    const val BLOCK_MILLIS: Long = 3_600_000L

    fun afterRejection(
        state: LocalState,
        nowMillis: Long,
        code: String?,
        retryAfterMillis: Long?,
    ): LocalState {
        val attempts = state.rejectedAttempts + 1
        return if (attempts >= ATTEMPTS_BEFORE_BLOCK) {
            state.copy(
                blockedReason = code,
                blockedUntilMillis = nowMillis + (retryAfterMillis ?: BLOCK_MILLIS),
                rejectedAttempts = 0,
            )
        } else {
            state.copy(
                blockedReason = code,
                blockedUntilMillis = 0L,
                parkUntilMillis = nowMillis + (retryAfterMillis ?: RETRY_AFTER_MILLIS),
                rejectedAttempts = attempts,
            )
        }
    }
}
