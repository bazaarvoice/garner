package com.bazaarvoice.push.sync

import com.bazaarvoice.push.SdkInfo
import com.bazaarvoice.push.crypto.Crypto
import com.bazaarvoice.push.crypto.Hex
import com.bazaarvoice.push.device.AttestationProvider
import com.bazaarvoice.push.device.ConnectivityMonitor
import com.bazaarvoice.push.device.DeviceInfoProvider
import com.bazaarvoice.push.device.PushTokenSource
import com.bazaarvoice.push.net.DeviceRegistrationRequest
import com.bazaarvoice.push.net.ErrorEnvelope
import com.bazaarvoice.push.net.LoginRequest
import com.bazaarvoice.push.net.Outcome
import com.bazaarvoice.push.net.ResponseClassifier
import com.bazaarvoice.push.net.RetryPolicy
import com.bazaarvoice.push.net.PushApiClient
import com.bazaarvoice.push.net.TokenUpdateRequest
import com.bazaarvoice.push.store.LocalState
import com.bazaarvoice.push.store.SecureStore
import com.bazaarvoice.push.store.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlin.time.Clock

/**
 * Desired-state coordinator: the only writer of state. Public
 * calls record desired state and return; [reconcile] compares with known
 * server state and performs the smallest ordered set of calls, persisting
 * before and after every network call (write-ahead) so process death
 * repeats at most one idempotent call. Transport failures park for retry
 * and never throw into the host process.
 */
public class Coordinator(
    private val appId: Int,
    private val client: PushApiClient,
    private val secure: SecureStore,
    private val settings: SettingsStore,
    private val attestation: AttestationProvider,
    private val tokenSource: PushTokenSource,
    private val connectivity: ConnectivityMonitor,
    private val deviceInfo: DeviceInfoProvider,
    private val clock: Clock = Clock.System,
    private val sessionRefreshIntervalHours: Long = 24L,
) {
    private companion object {
        const val STATE_KEY = "local-state"
        const val DEVICE_ID_KEY = "device-id"
        const val DEVICE_KEY_KEY = "device-api-key"
        const val AUTH_TOKEN_KEY = "auth-token"
        const val REFRESH_TOKEN_KEY = "refresh-token"
        const val AUTH_EXPIRES_KEY = "auth-expires-at"
        const val SERVER_LINK_KEY = "server-link"
        const val LAST_REFRESH_KEY = "last-refresh-at"
        /**
         * The digest of the consumer's email that the backend answered at login.
         * Kept with the tokens in the secure store; the plain address is never stored.
         */
        const val EMAIL_HASH_KEY = "identity-email-hash"
        const val MAX_STEPS = 8
    }

    private fun load(): LocalState = LocalState.decode(settings.get(STATE_KEY))
    /**
     * The address the host passed to [setDesiredIdentity], held in memory only until a login
     * succeeds. It never reaches the settings or the secure store, so a process death
     * before that login drops it and the host's next `linkIdentity` supplies it again.
     */
    private var pendingEmail: String? = null

    private fun save(state: LocalState) {
        settings.put(STATE_KEY, state.encode())
    }


    // -- Desired-state writers (called by the public facade, never network) --

    /**
     * Records who should be logged in. [email] is the plain address: it stays in memory until
     * the next login succeeds, and only the backend sees it (it hashes it and answers
     * the digest, which is what the SDK keeps). A different user drops the previous user's digest.
     * The email is the only link key.
     */
    public fun setDesiredIdentity(userId: String, email: String) {
        val state = load()
        pendingEmail = email
        if (state.userId != userId) secure.remove(EMAIL_HASH_KEY)
        save(state.copy(
            userId = userId,
            identityPhase = "signed-in",
            pendingLogout = false,
            // A new user replays the device-level preference.
            serverOptedOut = if (state.userId != userId) null else state.serverOptedOut,
        ))
    }

    public fun clearDesiredIdentity() {
        val state = load()
        pendingEmail = null
        secure.remove(EMAIL_HASH_KEY)
        save(state.copy(
            userId = null,
            identityPhase = "logged-out",
        ))
    }

    public fun setDesiredOptedOut(optedOut: Boolean) {
        save(load().copy(desiredOptedOut = optedOut))
    }

    /**
     * Why the wanted login hasn't run yet ([LoginWait]): `NOT_REGISTERED`,
     * `WAITING_FOR_PUSH_TOKEN`, or null (signed in, no user wanted, or the login ran).
     */
    internal fun loginWaitReason(): String? {
        val state = load()
        val signedIn = secure.get(AUTH_TOKEN_KEY) != null && settings.get(SERVER_LINK_KEY) == state.userId
        return LoginWait.reason(state, signedIn)
    }

    /** Bounded reconcile loop; returns the last executed call. */
    public suspend fun reconcile(foreground: Boolean): PlannedCall {
        var lastAction = PlannedCall.IDLE
        repeat(MAX_STEPS) {
            val result = step(foreground)
            if (result == PlannedCall.IDLE) return lastAction
            lastAction = result
        }
        return lastAction
    }

    private suspend fun step(foreground: Boolean): PlannedCall {
        val state = load()
        val now = clock.now().toEpochMilliseconds()
        if (!connectivity.connected) return PlannedCall.IDLE
        if (now < state.parkUntilMillis) {
            return PlannedCall.IDLE
        }
        val token = runCatching { tokenSource.currentToken() }.getOrNull()
        val tokenChanged = token != null &&
            Hex.encode(Crypto.sha256(token.encodeToByteArray())) != state.syncedTokenHash
        val authExpiresAt = secure.get(AUTH_EXPIRES_KEY)?.toLongOrNull() ?: 0L
        val lastRefresh = settings.get(LAST_REFRESH_KEY)?.toLongOrNull() ?: 0L
        val inputs = PlanInputs(
            state = state,
            nowMillis = now,
            tokenChanged = tokenChanged,
            hasSession = secure.get(AUTH_TOKEN_KEY) != null,
            serverLinkedUser = settings.get(SERVER_LINK_KEY),
            authExpired = authExpiresAt <= now,
            refreshDue = now - lastRefresh > sessionRefreshIntervalHours * 3_600_000L,
            foregroundOrKeepAlive = foreground,
            hasLoginIdentity = pendingEmail != null || secure.get(EMAIL_HASH_KEY) != null,
        )
        return when (val call = Planner.plan(inputs)) {
            PlannedCall.IDLE -> PlannedCall.IDLE
            PlannedCall.REGISTER -> {
                register(state, now)
                PlannedCall.REGISTER
            }
            PlannedCall.SYNC_TOKEN -> {
                syncToken(state, token ?: return PlannedCall.IDLE, now)
                PlannedCall.SYNC_TOKEN
            }
            PlannedCall.LOGOUT -> {
                logout(state, now)
                PlannedCall.LOGOUT
            }
            PlannedCall.LOGIN -> {
                login(state, now)
                PlannedCall.LOGIN
            }
            PlannedCall.REFRESH -> {
                refresh(state, now)
                PlannedCall.REFRESH
            }
            PlannedCall.OPT_OUT -> {
                setPreference(state, true, now)
                PlannedCall.OPT_OUT
            }
            PlannedCall.OPT_IN -> {
                setPreference(state, false, now)
                PlannedCall.OPT_IN
            }
        }
    }

    private suspend fun register(state: LocalState, now: Long) {
        // Attestation must be bounded: a stalled Play/App Attest call would
        // otherwise hang the reconcile (and the facade mutex) forever.
        // Timeout parks for retry — it never fabricates evidence.
        val evidence = runCatching {
            kotlinx.coroutines.withTimeout(30_000L) { attestation.attest("register:$now") }
        }.getOrNull() ?: return parkTransient(state, now, null)
        val token = when (evidence) {
            is AttestationProvider.Evidence.Token -> evidence.token
            AttestationProvider.Evidence.Unsupported -> "sentinel"
        }
        val result = runCatching {
            client.register(
                DeviceRegistrationRequest(
                    appId = appId,
                    platform = deviceInfo.platform,
                    appIdentifier = deviceInfo.appIdentifier,
                    attestationToken = token,
                    model = deviceInfo.model,
                    osVersion = deviceInfo.osVersion,
                    appVersion = deviceInfo.appVersion,
                    sdkVersion = SdkInfo.VERSION,
                )
            )
        }.getOrNull() ?: return parkTransient(state, now, null)
        when (outcomeOf("register", result)) {
            Outcome.SUCCESS -> {
                if (result.status == 201 && result.value != null) {
                    secure.put(DEVICE_ID_KEY, result.value.deviceId)
                    secure.put(DEVICE_KEY_KEY, result.value.deviceApiKey)
                    // A new registration has no push token on the server yet: forget
                    // the synced hash so the token goes up before any login.
                    save(state.copy(
                        deviceId = result.value.deviceId,
                        syncedTokenHash = null,
                        blockedReason = null,
                        blockedUntilMillis = 0L,
                        parkUntilMillis = 0L,
                        rejectedAttempts = 0,
                    ))
                }
            }
            Outcome.PARK_RATE_LIMITED -> parkUntil(state, now, result.retryAfterMillis)
            Outcome.BLOCKED -> {
                save(RejectionPolicy.afterRejection(
                    state, now, result.error?.code, result.retryAfterMillis))
            }
            else -> parkTransient(state, now, result.retryAfterMillis)
        }
    }

    private suspend fun syncToken(state: LocalState, token: String, now: Long) {
        val deviceId = state.deviceId ?: return
        val deviceKey = secure.get(DEVICE_KEY_KEY) ?: return
        // Debounce: re-check the live token is still new.
        delay(2000)
        val fresh = runCatching { tokenSource.currentToken() }.getOrNull() ?: return
        if (fresh != token) return
        val result = runCatching {
            client.updateToken(deviceId, appId, deviceKey,
                TokenUpdateRequest(pushToken = token, platform = deviceInfo.platform))
        }.getOrNull() ?: return parkTransient(state, now, null)
        when (outcomeOf("token", result)) {
            Outcome.SUCCESS -> save(state.copy(
                syncedTokenHash = Hex.encode(Crypto.sha256(token.encodeToByteArray())),
                parkUntilMillis = 0L,
                blockedReason = null,
                rejectedAttempts = 0,
            ))
            Outcome.REREGISTER, Outcome.RETRY_FRESH_SIGNATURE -> wipeRegistration(state)
            Outcome.PARK_RATE_LIMITED -> parkUntil(state, now, result.retryAfterMillis)
            Outcome.BLOCKED -> {
                save(RejectionPolicy.afterRejection(
                    state, now, result.error?.code, result.retryAfterMillis))
            }
            else -> parkTransient(state, now, result.retryAfterMillis)
        }
    }

    private suspend fun logout(state: LocalState, now: Long) {
        // Rule 5: move the session into pending logout first (write-ahead).
        if (!state.pendingLogout) save(state.copy(pendingLogout = true))
        val deviceId = state.deviceId
        val bearer = secure.get(AUTH_TOKEN_KEY)
        if (deviceId == null || bearer == null) {
            clearSession(load().copy(pendingLogout = false))
            return
        }
        val result = runCatching { client.logout(deviceId, appId, bearer) }.getOrNull()
            ?: return parkTransient(state, now, null)
        when (outcomeOf("logout", result)) {
            Outcome.SUCCESS, Outcome.RELOGIN, Outcome.REREGISTER ->
                clearSession(load().copy(pendingLogout = false))
            Outcome.PARK_RATE_LIMITED -> parkUntil(state, now, result.retryAfterMillis)
            else -> parkTransient(state, now, result.retryAfterMillis)
        }
    }

    private suspend fun login(state: LocalState, now: Long) {
        // Never a login without a device ID or before the push token is synced
        // (register → push token → login); the planner already waits for both.
        val deviceId = state.deviceId ?: return
        if (state.syncedTokenHash == null) return
        val deviceKey = secure.get(DEVICE_KEY_KEY) ?: return
        val userId = state.userId ?: return
        // First login names the consumer by the address; later ones by the stored digest.
        val email = pendingEmail
        val emailHash = if (email == null) secure.get(EMAIL_HASH_KEY) ?: return else null
        val result = runCatching {
            client.login(deviceId, appId, deviceKey,
                LoginRequest(userId = userId, email = email, emailAddressHash = emailHash))
        }.getOrNull() ?: return parkTransient(state, now, null)
        when (outcomeOf("login", result)) {
            Outcome.SUCCESS -> {
                val value = result.value
                if (value != null) {
                    secure.put(AUTH_TOKEN_KEY, value.authToken)
                    secure.put(REFRESH_TOKEN_KEY, value.refreshToken)
                    secure.put(EMAIL_HASH_KEY, value.emailAddressHash)
                    pendingEmail = null
                    secure.put(AUTH_EXPIRES_KEY,
                        runCatching {
                            Instant.parse(value.authExpiresAt).toEpochMilliseconds()
                        }.getOrDefault(now + 24L * 3_600_000L).toString())
                    settings.put(SERVER_LINK_KEY, userId)
                    settings.put(LAST_REFRESH_KEY, now.toString())
                    save(state.copy(parkUntilMillis = 0L, blockedReason = null, rejectedAttempts = 0))
                }
            }
            Outcome.DROP_INPUT -> {
                // The backend refused the identity (an address it cannot read, or a digest it
                // does not know for this user): forget it and wait for the next linkIdentity.
                pendingEmail = null
                secure.remove(EMAIL_HASH_KEY)
                save(state.copy(
                userId = null,
                identityPhase = "logged-out",
                ))
            }
            Outcome.REREGISTER -> wipeRegistration(state)
            Outcome.PARK_RATE_LIMITED -> parkUntil(state, now, result.retryAfterMillis)
            Outcome.BLOCKED -> {
                save(RejectionPolicy.afterRejection(
                    state, now, result.error?.code, result.retryAfterMillis))
            }
            else -> parkTransient(state, now, result.retryAfterMillis)
        }
    }

    private suspend fun refresh(state: LocalState, now: Long) {
        val deviceId = state.deviceId ?: return
        val refreshToken = secure.get(REFRESH_TOKEN_KEY) ?: return
        val result = runCatching { client.refresh(deviceId, appId, refreshToken) }.getOrNull() ?: return parkTransient(state, now, null)
        when (outcomeOf("refresh", result)) {
            Outcome.SUCCESS -> {
                val value = result.value
                if (value != null) {
                    secure.put(AUTH_TOKEN_KEY, value.authToken)
                    secure.put(AUTH_EXPIRES_KEY,
                        runCatching {
                            Instant.parse(value.authExpiresAt).toEpochMilliseconds()
                        }.getOrDefault(now + 24L * 3_600_000L).toString())
                    settings.put(LAST_REFRESH_KEY, now.toString())
                    save(state.copy(parkUntilMillis = 0L))
                }
            }
            Outcome.RELOGIN -> {
                // Session erased server-side: drop it; the planner logs in again.
                secure.remove(AUTH_TOKEN_KEY)
                secure.remove(REFRESH_TOKEN_KEY)
                secure.remove(AUTH_EXPIRES_KEY)
                settings.remove(SERVER_LINK_KEY)
            }
            Outcome.REREGISTER -> wipeRegistration(state)
            Outcome.PARK_RATE_LIMITED -> parkUntil(state, now, result.retryAfterMillis)
            else -> parkTransient(state, now, result.retryAfterMillis)
        }
    }

    private suspend fun setPreference(state: LocalState, optedOut: Boolean, now: Long) {
        val deviceId = state.deviceId ?: return
        val bearer = secure.get(AUTH_TOKEN_KEY) ?: return
        val result = runCatching {
            if (optedOut) client.optOut(deviceId, appId, bearer)
            else client.optIn(deviceId, appId, bearer)
        }.getOrNull() ?: return parkTransient(state, now, null)
        when (outcomeOf(if (optedOut) "opt-out" else "opt-in", result)) {
            Outcome.SUCCESS -> save(state.copy(serverOptedOut = optedOut, parkUntilMillis = 0L))
            Outcome.RELOGIN -> {
                secure.remove(AUTH_TOKEN_KEY)
                secure.remove(REFRESH_TOKEN_KEY)
                secure.remove(AUTH_EXPIRES_KEY)
                settings.remove(SERVER_LINK_KEY)
            }
            Outcome.REREGISTER -> wipeRegistration(state)
            Outcome.PARK_RATE_LIMITED -> parkUntil(state, now, result.retryAfterMillis)
            else -> parkTransient(state, now, result.retryAfterMillis)
        }
    }

    private fun wipeRegistration(state: LocalState) {
        secure.remove(DEVICE_ID_KEY)
        secure.remove(DEVICE_KEY_KEY)
        secure.remove(AUTH_TOKEN_KEY)
        secure.remove(REFRESH_TOKEN_KEY)
        secure.remove(AUTH_EXPIRES_KEY)
        settings.remove(SERVER_LINK_KEY)
        save(state.copy(deviceId = null, syncedTokenHash = null, parkUntilMillis = 0L))
    }

    private fun clearSession(state: LocalState) {
        secure.remove(AUTH_TOKEN_KEY)
        secure.remove(REFRESH_TOKEN_KEY)
        secure.remove(AUTH_EXPIRES_KEY)
        settings.remove(SERVER_LINK_KEY)
        save(state)
    }

    private fun parkUntil(state: LocalState, now: Long, retryAfterMillis: Long?) {
        save(state.copy(parkUntilMillis = now + (retryAfterMillis ?: 60_000L)))
    }

    private fun parkTransient(state: LocalState, now: Long, retryAfterMillis: Long?) {
        // Three immediate attempts happen inline in the caller paths via the
        // bounded reconcile loop; anything unresolved parks until a trigger.
        save(state.copy(parkUntilMillis = now + (retryAfterMillis ?: 60_000L)))
    }

    private fun outcomeOf(call: String, result: PushApiClient.CallResult<*>): Outcome =
        ResponseClassifier.classify(call, result.status, result.error?.code)

    private fun outcomeOf(call: String, status: Int, error: ErrorEnvelope?): Outcome =
        ResponseClassifier.classify(call, status, error?.code)
}
