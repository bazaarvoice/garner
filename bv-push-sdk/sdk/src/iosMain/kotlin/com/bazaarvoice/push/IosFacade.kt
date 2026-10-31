package com.bazaarvoice.push

import com.bazaarvoice.push.config.BVPushConfig
import com.bazaarvoice.push.config.BVPushOptions
import com.bazaarvoice.push.device.AttestationProvider
import com.bazaarvoice.push.device.ConnectivityMonitor
import com.bazaarvoice.push.device.DeviceInfoProvider
import com.bazaarvoice.push.device.PushTokenSource
import com.bazaarvoice.push.events.BVPushEvent
import com.bazaarvoice.push.events.Diagnostics
import com.bazaarvoice.push.identity.IdentityInput
import com.bazaarvoice.push.net.PushApiClient
import com.bazaarvoice.push.push.DisplayDecision
import com.bazaarvoice.push.push.DisplayPolicy
import com.bazaarvoice.push.push.ParsedPush
import com.bazaarvoice.push.push.PushPayloadParser
import com.bazaarvoice.push.push.QuietHours
import com.bazaarvoice.push.push.QuietWindow
import com.bazaarvoice.push.review.ReviewLinkValidator
import com.bazaarvoice.push.store.LocalState
import com.bazaarvoice.push.store.SecureStore
import com.bazaarvoice.push.store.SettingsStore
import com.bazaarvoice.push.sync.Coordinator
import com.bazaarvoice.push.sync.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One SDK event for Swift (the sealed [BVPushEvent] does not bridge well). [kind] names the
 * event; [notificationId] and [detail] carry its values: the hidden reason, the held-until epoch
 * millis, the review presenter, the opted-out flag, the blocked or pending-login reason, or the
 * error kind.
 */
public class IosEvent(
    public val kind: String,
    public val notificationId: String?,
    public val detail: String?,
) {
    override fun toString(): String = "IosEvent(kind=$kind, notificationId=$notificationId, detail=$detail)"
}

/**
 * What the SDK decided for one remote push. [decision] is `display`, `hold`, `suppress`,
 * `drop` or `not-handled`; Swift posts the local notification for `display` (now) and `hold`
 * (after [holdSeconds]).
 */
public class IosPushResult internal constructor(
    public val decision: String,
    public val notificationId: String?,
    public val title: String?,
    public val body: String?,
    public val imageUrl: String?,
    public val reviewUrl: String?,
    public val holdSeconds: Long,
)

/**
 * iOS entry point. Owns the coordinator and the Keychain /
 * App Group stores; Darwin APIs (lifecycle, NWPathMonitor, FCM token, the
 * notification centre) live in Swift and forward here. Callbacks (not suspend
 * funs) keep the Swift bridge simple. No call throws into Swift: problems come
 * back as strings or as [IosEvent]s with kind `error`.
 */
public class IosFacade(
    private val appId: Int,
    private val options: BVPushOptions,
    private val secure: SecureStore,
    private val settings: SettingsStore,
    private val attestation: AttestationProvider,
    private val deviceInfo: DeviceInfoProvider,
) {
    /** Production adapters: App Attest and UIKit device facts. */
    public constructor(
        appId: Int,
        options: BVPushOptions,
        secure: SecureStore,
        settings: SettingsStore,
    ) : this(appId, options, secure, settings, AppAttestAttestor(), IosDeviceInfo())

    public constructor(
        appId: Int,
        secure: SecureStore,
        settings: SettingsStore,
    ) : this(appId, BVPushOptions(), secure, settings)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var fcmToken: String? = null
    private var connected = true
    private var permission = PERMISSION_UNKNOWN
    private var initialized = false
    private var cachedDeviceId: String? = null
    private var cachedError: String? = null
    private var stateListener: ((String?, String?) -> Unit)? = null
    private var eventListener: ((IosEvent) -> Unit)? = null

    private val coordinator = Coordinator(
        appId = appId,
        client = PushApiClient(BVPushConfig.resolveBaseUrl(options)),
        secure = secure,
        settings = settings,
        attestation = attestation,
        tokenSource = object : PushTokenSource {
            override suspend fun currentToken(): String? = fcmToken
        },
        connectivity = object : ConnectivityMonitor {
            override val connected: Boolean get() = this@IosFacade.connected
        },
        deviceInfo = deviceInfo,
        sessionRefreshIntervalHours = options.sessionRefreshIntervalHours,
    )

    /** Store facts compared before and after a reconcile ([Lifecycle.events]). */
    private fun facts(): Lifecycle.Facts = Lifecycle.facts(
        LocalState.decode(settings.get(STATE_KEY)),
        deviceId = secure.get(DEVICE_ID_KEY),
        session = secure.get(AUTH_TOKEN_KEY) != null,
        lastRefresh = settings.get(LAST_REFRESH_KEY),
    )

    private suspend fun reconciled(foreground: Boolean) {
        val before = runCatching { facts() }.getOrNull()
        runCatching { coordinator.reconcile(foreground) }
            .onSuccess { cachedError = null }
            .onFailure {
                cachedError = it.message ?: it::class.simpleName ?: ERROR_RECONCILE
                emit(BVPushEvent.Error(ERROR_RECONCILE, null))
            }
        val after = runCatching { facts() }.getOrNull()
        if (before != null && after != null) Lifecycle.events(before, after).forEach { emit(it) }
        cachedDeviceId = after?.deviceId
        initialized = true
        notifyState()
    }

    private fun notifyState() {
        val listener = stateListener ?: return
        runCatching { listener(cachedDeviceId, lastError()) }
    }

    private fun emit(event: BVPushEvent) {
        val listener = eventListener ?: return
        runCatching { listener(event.toIosEvent()) }
    }

    /**
     * Keychain items survive an uninstall, standard UserDefaults do not: credentials
     * without the install marker belong to an earlier install and are purged, so a reinstall
     * registers as a new device and never reuses the old identity.
     */
    private fun purgeAfterReinstall() {
        val marker = IosInstallMarker()
        if (marker.present()) return
        if (secure.get(DEVICE_ID_KEY) != null) {
            SECURE_KEYS.forEach { secure.remove(it) }
            SETTINGS_KEYS.forEach { settings.remove(it) }
            emit(BVPushEvent.IdentityUnlinked)
        }
        marker.mark()
    }

    // -- Lifecycle (Swift forwards UIKit and Network events) --

    public fun initialize() {
        scope.launch {
            mutex.withLock {
                runCatching { purgeAfterReinstall() }
                reconciled(false)
            }
        }
    }

    public fun isInitialized(): Boolean = initialized

    public fun setFcmToken(token: String?) {
        fcmToken = token
        scope.launch { mutex.withLock { reconciled(false) } }
    }

    public fun setConnected(value: Boolean) {
        connected = value
        if (value) scope.launch { mutex.withLock { reconciled(false) } }
    }

    public fun onForeground() {
        scope.launch { mutex.withLock { reconciled(true) } }
    }

    /** `granted`, `denied`, `not-determined` or `unknown`, as Swift last read it. */
    public fun setNotificationPermission(value: String) {
        permission = value
    }

    // -- Listeners (one each; the Swift facade fans out) --

    /** Called with (deviceId, lastError) after every reconcile, and once right away. */
    public fun setStateListener(listener: (String?, String?) -> Unit) {
        stateListener = listener
        notifyState()
    }

    public fun setEventListener(listener: (IosEvent) -> Unit) {
        eventListener = listener
    }

    // -- Identity and preferences --

    /** Null when [userId] and [email] can be linked, else the reason. */
    public fun identityProblem(userId: String, email: String): String? = when {
        !IdentityInput.isValidUserId(userId) -> IdentityInput.INVALID_USER_ID
        !IdentityInput.isValidEmail(email) -> IdentityInput.INVALID_EMAIL
        else -> null
    }

    /**
     * Records desired state and reconciles. The completion receives the reason the input was
     * refused; or, when the SDK kept the user but can't log in yet,
     * [BVPushEvent.IdentityPending.NOT_REGISTERED] or
     * [BVPushEvent.IdentityPending.WAITING_FOR_PUSH_TOKEN] (an `identity-pending` event says the
     * same, and the SDK logs in by itself once that step is done); else null: signed in, or the
     * login ran. The email is sent as is: the backend normalises and hashes it.
     * Memory only until the first login succeeds; then the SDK keeps the backend's
     * digest, not the address.
     */
    public fun linkIdentity(userId: String, email: String, completion: (String?) -> Unit) {
        val problem = identityProblem(userId, email)
        if (problem != null) {
            completion(problem)
            return
        }
        scope.launch {
            val waiting = mutex.withLock {
                coordinator.setDesiredIdentity(userId, email)
                reconciled(false)
                runCatching { coordinator.loginWaitReason() }.getOrNull()
            }
            if (waiting != null) emit(BVPushEvent.IdentityPending(waiting))
            completion(waiting)
        }
    }

    public fun unlinkIdentity(completion: (String?) -> Unit) {
        scope.launch {
            mutex.withLock {
                coordinator.clearDesiredIdentity()
                reconciled(false)
            }
            completion(null)
        }
    }

    public fun setOptedOut(optedOut: Boolean, completion: (String?) -> Unit) {
        scope.launch {
            mutex.withLock {
                coordinator.setDesiredOptedOut(optedOut)
                reconciled(false)
            }
            completion(null)
        }
    }

    // -- Quiet hours (device-local wall clock) --

    /** Null when saved, else the reason (hours 0..23, minutes 0..59). */
    public fun setQuietHours(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): String? {
        if (startHour !in 0..23 || endHour !in 0..23) return "hour 0..23"
        if (startMinute !in 0..59 || endMinute !in 0..59) return "minute 0..59"
        settings.put(QUIET_KEY, "$startHour,$startMinute,$endHour,$endMinute")
        return null
    }

    public fun disableQuietHours() {
        settings.put(QUIET_KEY, QUIET_OFF)
    }

    public fun resetQuietHours() {
        settings.remove(QUIET_KEY)
    }

    private fun quietWindow(): QuietWindow? = when (val raw = settings.get(QUIET_KEY)) {
        null -> QuietWindow.DEFAULT
        QUIET_OFF -> null
        else -> {
            val parts = raw.split(",").mapNotNull { it.toIntOrNull() }
            if (parts.size == 4) QuietWindow(parts[0], parts[1], parts[2], parts[3]) else QuietWindow.DEFAULT
        }
    }

    // -- Pushes --

    /**
     * Display policy for one remote push, the same rules as Android. [data] is the
     * payload's string values; [nowMillis] and [nowMinutes] (minutes after local midnight) come
     * from the device clock.
     */
    public fun handlePush(data: Map<String, String>, nowMillis: Long, nowMinutes: Int): IosPushResult {
        return when (val parsed = PushPayloadParser.parse(data)) {
            is ParsedPush.NotBv -> result(DECISION_NOT_HANDLED, null)
            is ParsedPush.Invalid -> {
                emit(BVPushEvent.Error(ERROR_INVALID_PUSH, null))
                result(DECISION_DROP, null)
            }
            is ParsedPush.BvPush -> decide(parsed, data, nowMillis, nowMinutes)
        }
    }

    private fun decide(parsed: ParsedPush.BvPush, data: Map<String, String>, nowMillis: Long, nowMinutes: Int): IosPushResult {
        val id = parsed.notificationId
        emit(BVPushEvent.NotificationReceived(id))
        val state = LocalState.decode(settings.get(STATE_KEY))
        val window = quietWindow()
        val decision = DisplayPolicy.decide(
            parsed,
            optedOut = state.desiredOptedOut,
            loggedOut = state.userId == null && secure.get(AUTH_TOKEN_KEY) == null,
            isTestPush = data[KEY_IS_TEST] == "true",
            inQuietWindow = window != null && QuietHours.isQuiet(window, nowMinutes),
        )
        return when (decision) {
            DisplayDecision.NOT_HANDLED, DisplayDecision.DROP -> result(DECISION_DROP, parsed)
            DisplayDecision.SUPPRESS_OPTED_OUT -> {
                emit(BVPushEvent.NotificationSuppressed(id, REASON_OPTED_OUT))
                result(DECISION_SUPPRESS, parsed)
            }
            DisplayDecision.SUPPRESS_LOGGED_OUT -> {
                emit(BVPushEvent.NotificationSuppressed(id, REASON_LOGGED_OUT))
                result(DECISION_SUPPRESS, parsed)
            }
            DisplayDecision.HOLD -> {
                val minutes = if (window != null) QuietHours.releaseInMinutes(window, nowMinutes) else 60
                val releaseIn = maxOf(1, minutes)
                emit(BVPushEvent.NotificationHeld(id, nowMillis + releaseIn * 60_000L))
                result(DECISION_HOLD, parsed, releaseIn * 60L)
            }
            DisplayDecision.DISPLAY -> result(DECISION_DISPLAY, parsed)
        }
    }

    private fun result(decision: String, parsed: ParsedPush.BvPush?, holdSeconds: Long = 0L) = IosPushResult(
        decision = decision,
        notificationId = parsed?.notificationId,
        title = parsed?.title,
        body = parsed?.body,
        imageUrl = parsed?.imageUrl,
        reviewUrl = parsed?.reviewUrl,
        holdSeconds = holdSeconds,
    )

    /**
     * The push's id, content and review link without a display decision and without events:
     * for a tapped push, or an alert push the system showed itself. Decision `display` for a
     * valid BV push, `drop` for an invalid one, `not-handled` for anything else.
     */
    public fun inspectPush(data: Map<String, String>): IosPushResult = when (val parsed = PushPayloadParser.parse(data)) {
        is ParsedPush.NotBv -> result(DECISION_NOT_HANDLED, null)
        is ParsedPush.Invalid -> result(DECISION_DROP, null)
        is ParsedPush.BvPush -> result(DECISION_DISPLAY, parsed)
    }

    /** True for a review link the SDK may open (https, no credentials). */
    public fun isOpenableReviewLink(url: String): Boolean = ReviewLinkValidator.isOpenable(url)

    /** An alert push the system showed without asking the SDK (no display decision ran). */
    public fun notificationReceived(notificationId: String) {
        emit(BVPushEvent.NotificationReceived(notificationId))
    }

    /** Swift posted the notification. */
    public fun notificationDisplayed(notificationId: String) {
        emit(BVPushEvent.NotificationDisplayed(notificationId))
    }

    /** Swift could not post it, for example `permission-denied`. */
    public fun notificationSuppressed(notificationId: String, reason: String) {
        emit(BVPushEvent.NotificationSuppressed(notificationId, reason))
    }

    public fun notificationOpened(notificationId: String) {
        emit(BVPushEvent.NotificationOpened(notificationId))
    }

    public fun reviewOpened(notificationId: String, presenter: String) {
        emit(BVPushEvent.ReviewOpened(notificationId, presenter))
    }

    public fun reportError(kind: String) {
        emit(BVPushEvent.Error(kind, null))
    }

    // -- State --

    public fun currentDeviceId(): String? = cachedDeviceId

    /** The registration block reason while it holds, else the last reconcile failure. */
    public fun lastError(): String? {
        val state = runCatching { LocalState.decode(settings.get(STATE_KEY)) }.getOrNull() ?: return cachedError
        return Lifecycle.lastError(state, cachedError)
    }

    public fun diagnostics(): Diagnostics {
        val state = LocalState.decode(settings.get(STATE_KEY))
        val deviceId = secure.get(DEVICE_ID_KEY)
        return Diagnostics(
            sdkVersion = SdkInfo.VERSION,
            appId = appId,
            registered = deviceId != null,
            deviceId = deviceId,
            tokenSynced = state.syncedTokenHash != null,
            identityPhase = state.identityPhase,
            hasSession = secure.get(AUTH_TOKEN_KEY) != null,
            sessionExpiry = secure.get(AUTH_EXPIRES_KEY),
            desiredOptedOut = state.desiredOptedOut,
            serverOptedOut = state.serverOptedOut,
            quietWindow = quietWindow()?.toString(),
            notificationPermission = permission,
            fcmConflict = false,
            lastError = lastError(),
            lastCorrelationId = null,
        )
    }

    private companion object {
        // Store keys shared with the Coordinator.
        const val STATE_KEY = "local-state"
        const val DEVICE_ID_KEY = "device-id"
        const val AUTH_TOKEN_KEY = "auth-token"
        const val AUTH_EXPIRES_KEY = "auth-expires-at"
        const val LAST_REFRESH_KEY = "last-refresh-at"
        const val QUIET_KEY = "quiet-window"
        const val QUIET_OFF = "off"
        val SECURE_KEYS = listOf(
            "device-id", "device-api-key", "auth-token", "refresh-token", "auth-expires-at",
            "identity-email-hash",
        )
        val SETTINGS_KEYS = listOf("local-state", "server-link", "last-refresh-at")

        const val PERMISSION_UNKNOWN = "unknown"
        const val KEY_IS_TEST = "is_test"
        const val REASON_OPTED_OUT = "opted-out"
        const val REASON_LOGGED_OUT = "logged-out"
        const val ERROR_INVALID_PUSH = "INVALID_PUSH"
        const val ERROR_RECONCILE = "RECONCILE_FAILED"
        const val DECISION_DISPLAY = "display"
        const val DECISION_HOLD = "hold"
        const val DECISION_SUPPRESS = "suppress"
        const val DECISION_DROP = "drop"
        const val DECISION_NOT_HANDLED = "not-handled"
    }
}

internal fun BVPushEvent.toIosEvent(): IosEvent = when (this) {
    BVPushEvent.Registered -> IosEvent("registered", null, null)
    is BVPushEvent.RegistrationBlocked -> IosEvent("registration-blocked", null, reason)
    BVPushEvent.PushTokenSynced -> IosEvent("push-token-synced", null, null)
    BVPushEvent.IdentityLinked -> IosEvent("identity-linked", null, null)
    is BVPushEvent.IdentityPending -> IosEvent("identity-pending", null, reason)
    BVPushEvent.SignedIn -> IosEvent("signed-in", null, null)
    BVPushEvent.SessionRefreshed -> IosEvent("session-refreshed", null, null)
    BVPushEvent.SignedOut -> IosEvent("signed-out", null, null)
    BVPushEvent.IdentityUnlinked -> IosEvent("identity-unlinked", null, null)
    is BVPushEvent.PreferenceSynced -> IosEvent("preference-synced", null, optedOut.toString())
    is BVPushEvent.NotificationReceived -> IosEvent("notification-received", id, null)
    is BVPushEvent.NotificationDisplayed -> IosEvent("notification-displayed", id, null)
    is BVPushEvent.NotificationHeld -> IosEvent("notification-held", id, untilMillis.toString())
    is BVPushEvent.NotificationSuppressed -> IosEvent("notification-suppressed", id, reason)
    is BVPushEvent.NotificationOpened -> IosEvent("notification-opened", id, null)
    is BVPushEvent.ReviewOpened -> IosEvent("review-opened", id, presenter)
    is BVPushEvent.Error -> IosEvent("error", null, kind)
}
