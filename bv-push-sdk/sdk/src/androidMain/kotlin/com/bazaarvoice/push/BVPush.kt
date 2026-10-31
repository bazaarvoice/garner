package com.bazaarvoice.push

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import com.bazaarvoice.push.config.BVPushConfig
import com.bazaarvoice.push.config.BVPushOptions
import com.bazaarvoice.push.crypto.Hex
import com.bazaarvoice.push.crypto.Crypto
import com.bazaarvoice.push.device.AttestationProvider
import com.bazaarvoice.push.device.PushTokenSource
import com.bazaarvoice.push.events.BVPushEvent
import com.bazaarvoice.push.events.Diagnostics
import com.bazaarvoice.push.identity.IdentityInput
import com.bazaarvoice.push.log.Level
import com.bazaarvoice.push.log.Logger
import com.bazaarvoice.push.net.PushApiClient
import com.bazaarvoice.push.push.DisplayDecision
import com.bazaarvoice.push.push.DisplayPolicy
import com.bazaarvoice.push.push.ParsedPush
import com.bazaarvoice.push.push.PushPayloadParser
import com.bazaarvoice.push.push.QuietHours
import com.bazaarvoice.push.push.QuietWindow
import com.bazaarvoice.push.review.ReviewLinkValidator
import com.bazaarvoice.push.store.LocalState
import com.bazaarvoice.push.sync.Coordinator
import com.bazaarvoice.push.sync.Lifecycle
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Public Android facade. Validates arguments and delegates;
 * holds no business logic. Every call is non-blocking and idempotent.
 */
public object BVPush {

    private var app: Application? = null
    private var appId: Int = 0
    private var options: BVPushOptions = BVPushOptions()
    private var coordinator: Coordinator? = null
    private var secureStore: AndroidSecureStore? = null
    private var settingsStore: AndroidSettingsStore? = null
    private var tapHandler: ((String) -> Boolean)? = null
    /** Last token the host passed to [onNewToken] or [setPushToken]; used when Firebase has none. */
    private var hostPushToken: String? = null
    /** Why the last reconcile failed; null after one that completed. */
    private var reconcileError: String? = null

    private const val STATE_KEY = "local-state"
    private const val DEVICE_ID_KEY = "device-id"
    private const val AUTH_TOKEN_KEY = "auth-token"
    private const val LAST_REFRESH_KEY = "last-refresh-at"
    private const val ERROR_RECONCILE = "RECONCILE_FAILED"
    private const val QUIET_KEY = "quiet-window"
    private const val HELD_PREFIX = "held-"
    private const val TAG = "BVPush"

    /**
     * Manifest meta-data with the host app's Google Cloud project number: the project linked
     * to the app in Play Console. Give it as a string resource
     * (`android:value="@string/..."`): a plain number this long is stored as a float and loses
     * digits. Absent → the Play Integrity request goes without a number.
     */
    internal const val CLOUD_PROJECT_NUMBER_KEY = "com.bazaarvoice.push.CLOUD_PROJECT_NUMBER"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Serializes full init bodies and intent+reconcile pairs: concurrent
    // inits (auto-init vs host init) and intents otherwise interleave on the
    // singleton fields and the wrong coordinator wins.
    private val initMutex = Mutex()
    private val _state = MutableStateFlow(PublicState())
    public val state: StateFlow<PublicState> = _state
    private val _events = MutableSharedFlow<BVPushEvent>(extraBufferCapacity = 64)
    public val events: SharedFlow<BVPushEvent> = _events

    public data class PublicState(
        val initialized: Boolean = false,
        val deviceId: String? = null,
        val lastError: String? = null,
    )

    /** Called by the App Startup initializer; hosts may also call [init] directly. */
    public fun attach(application: Application) {
        app = application
    }

    @JvmOverloads
    public fun init(appId: Int, options: BVPushOptions = BVPushOptions()) {
        val context = app ?: return
        init(context, appId, options)
    }

    public fun init(context: Context, appId: Int, options: BVPushOptions = BVPushOptions()) {
        if (!BVPushConfig.isValidAppId(appId)) {
            emit(BVPushEvent.Error("INVALID_APP_ID", null))
            return
        }
        // Plain HTTP only when the host app is debuggable AND the
        // host opted in via allowInsecureLocalhost (a local test backend);
        // otherwise dormant with an error event, never an exception.
        val baseUrl = BVPushConfig.resolveBaseUrl(options)
        if (baseUrl.startsWith("http://")) {
            val debuggable = (context.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (!debuggable || !options.allowInsecureLocalhost) {
                emit(BVPushEvent.Error("INSECURE_BASE_URL", null))
                return
            }
        }
        // The calling (usually main) thread only validates and
        // enqueues; stores, binder calls and token fetch run off-thread.
        this.appId = appId
        this.options = options
        val appContext = context.applicationContext
        scope.launch {
            initMutex.withLock {
                initLocked(appContext, appId, options)
            }
        }
    }

    private suspend fun initLocked(appContext: Context, appId: Int, options: BVPushOptions) {
            val namespace = storeNamespace(appId)
            var secure = AndroidSecureStore(appContext, namespace)
            var settings = AndroidSettingsStore(appContext, namespace)
            secureStore = secure
            settingsStore = settings
            // Restore detection: credentials without the
            // install marker mean an Auto Backup restore — purge and start
            // fresh (a new deviceId), never reuse another install's identity.
            // Stores are re-created: the old objects still see deleted files.
            val marker = InstallMarker(appContext.noBackupFilesDir)
            if (!marker.present()) {
                if (secure.get("device-id") != null) {
                    appContext.deleteSharedPreferences("bv_push_secure_$namespace")
                    appContext.deleteSharedPreferences("bv_push_settings_$namespace")
                    secure = AndroidSecureStore(appContext, namespace)
                    settings = AndroidSettingsStore(appContext, namespace)
                    secureStore = secure
                    settingsStore = settings
                    emit(BVPushEvent.IdentityUnlinked)
                }
                marker.mark()
            }
            val tokenSource = object : PushTokenSource {
                override suspend fun currentToken(): String? {
                    val fromFirebase = try {
                        FirebaseMessaging.getInstance().token.awaitNullable()
                    } catch (_: Exception) {
                        null
                    }
                    return fromFirebase ?: hostPushToken
                }
            }
            val monitor = ConnectivityMonitor(appContext)
            var connectedHolder = true
            monitor.start { connectedHolder = it }
            val attestor = PlayIntegrityAttestor(
                appContext,
                cloudProjectNumber = readManifestCloudProjectNumber(appContext),
            )
            val client = PushApiClient(BVPushConfig.resolveBaseUrl(options))
            coordinator = Coordinator(appId, client, secure, settings,
                object : AttestationProvider by attestor {}, tokenSource,
                object : com.bazaarvoice.push.device.ConnectivityMonitor {
                    override val connected: Boolean get() = connectedHolder
                }, AndroidDeviceInfo(appContext))
            AndroidNotificationBuilder.ensureChannel(appContext)
            if (!FcmServiceConflictDetector.isOurs(appContext)) {
                Logger.log(Level.WARN, "BVPush", "host messaging service does not forward to BVPush")
            }
            fetchToken(appContext)
            reconciledLocked(false)
            _state.value = _state.value.copy(initialized = true)
    }

    /** [reconciledLocked] off the calling thread, after any init or intent in progress. */
    private fun reconcileLater(foreground: Boolean) {
        scope.launch { initMutex.withLock { reconciledLocked(foreground) } }
    }

    /**
     * One reconcile, then what changed, as on iOS: lifecycle events on [events] (registered,
     * registration blocked, push token synced, signed in or out, session refreshed, preference
     * synced; `RECONCILE_FAILED` when it threw) and the device ID and last error in [state].
     * Never throws. The caller holds [initMutex].
     */
    private suspend fun reconciledLocked(foreground: Boolean) {
        val sync = coordinator ?: return
        val before = runCatching { facts() }.getOrNull()
        runCatching { sync.reconcile(foreground) }
            .onSuccess { reconcileError = null }
            .onFailure {
                reconcileError = it.message ?: it::class.simpleName ?: ERROR_RECONCILE
                emit(BVPushEvent.Error(ERROR_RECONCILE, null))
            }
        val after = runCatching { facts() }.getOrNull() ?: return
        val state = runCatching { LocalState.decode(settingsStore?.get(STATE_KEY)) }.getOrNull() ?: return
        if (before != null) Lifecycle.events(before, after).forEach { emit(it) }
        _state.value = _state.value.copy(
            deviceId = after.deviceId,
            lastError = Lifecycle.lastError(state, reconcileError),
        )
    }

    private fun facts(): Lifecycle.Facts? {
        val secure = secureStore ?: return null
        val settings = settingsStore ?: return null
        return Lifecycle.facts(
            LocalState.decode(settings.get(STATE_KEY)),
            deviceId = secure.get(DEVICE_ID_KEY),
            session = secure.get(AUTH_TOKEN_KEY) != null,
            lastRefresh = settings.get(LAST_REFRESH_KEY),
        )
    }

    /**
     * Links the consumer to this device. Pass [email] as the app knows it: the Garner backend
     * normalises and hashes it. The SDK holds it in memory
     * only until the first login succeeds, never stores it and never logs it; afterwards it keeps
     * the backend's digest (`emailAddressHash`) in the encrypted store. The email is the only link
     * key. The login runs only after register and push token sync: when it can't run yet,
     * [events] gets [BVPushEvent.IdentityPending] with the step it waits for
     * ([BVPushEvent.IdentityPending.NOT_REGISTERED] or
     * [BVPushEvent.IdentityPending.WAITING_FOR_PUSH_TOKEN]); the SDK keeps the user and logs in by
     * itself once that step is done.
     */
    public fun linkIdentity(userId: String, email: String) {
        require(IdentityInput.isValidUserId(userId)) { IdentityInput.INVALID_USER_ID }
        require(IdentityInput.isValidEmail(email)) { IdentityInput.INVALID_EMAIL }
        scope.launch {
            initMutex.withLock {
                coordinator?.setDesiredIdentity(userId, email)
                reconciledLocked(false)
                val waiting = runCatching { coordinator?.loginWaitReason() }.getOrNull()
                if (waiting != null) emit(BVPushEvent.IdentityPending(waiting))
            }
        }
    }

    public fun unlinkIdentity(userId: String) {
        scope.launch {
            initMutex.withLock {
                coordinator?.clearDesiredIdentity()
                reconciledLocked(false)
            }
        }
    }

    public fun optOut() {
        scope.launch {
            initMutex.withLock {
                coordinator?.setDesiredOptedOut(true)
                reconciledLocked(false)
            }
        }
    }

    public fun optIn() {
        scope.launch {
            initMutex.withLock {
                coordinator?.setDesiredOptedOut(false)
                reconciledLocked(false)
            }
        }
    }

    public fun setQuietHours(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        require(startHour in 0..23 && endHour in 0..23) { "hour 0..23" }
        require(startMinute in 0..59 && endMinute in 0..59) { "minute 0..59" }
        settingsStore?.put(QUIET_KEY, "$startHour,$startMinute,$endHour,$endMinute")
    }

    public fun disableQuietHours() {
        settingsStore?.put(QUIET_KEY, "off")
    }

    public fun resetQuietHours() {
        settingsStore?.remove(QUIET_KEY)
    }

    internal fun quietWindow(): QuietWindow? {        return when (val raw = settingsStore?.get(QUIET_KEY)) {
            null -> QuietWindow.DEFAULT
            "off" -> null
            else -> {
                val parts = raw.split(",").mapNotNull { it.toIntOrNull() }
                if (parts.size == 4) QuietWindow(parts[0], parts[1], parts[2], parts[3])
                else QuietWindow.DEFAULT
            }
        }
    }

    internal fun wallMinutesNow(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }

    /**
     * Synchronous store bootstrap for entry points that can run before any
     * init completed (FCM service in a fresh process). Same files the async
     * init uses; a later init reuses them. Last init wins on namespace.
     */
    private fun ensureStores(appContext: Context) {
        if (secureStore != null && settingsStore != null) return
        val id = appId.takeIf { it != 0 } ?: readManifestAppId(appContext) ?: return
        val namespace = storeNamespace(id)
        if (secureStore == null) secureStore = AndroidSecureStore(appContext, namespace)
        if (settingsStore == null) settingsStore = AndroidSettingsStore(appContext, namespace)
    }

    /** Stores are keyed by App ID only; the SDK has no environments. */
    private fun storeNamespace(appId: Int): String = appId.toString()

    /**
     * The host app's Cloud project number from [CLOUD_PROJECT_NUMBER_KEY], or `null` when it is
     * absent. A value that is not a string of digits is ignored with a warning.
     */
    private fun readManifestCloudProjectNumber(appContext: Context): Long? {
        val metaData = try {
            appContext.packageManager.getApplicationInfo(
                appContext.packageName, android.content.pm.PackageManager.GET_META_DATA,
            ).metaData
        } catch (_: Exception) {
            null
        } ?: return null
        if (!metaData.containsKey(CLOUD_PROJECT_NUMBER_KEY)) return null
        // A non-string value (a plain number in the manifest) reads as null and warns below.
        val raw = metaData.getString(CLOUD_PROJECT_NUMBER_KEY)
        if (raw != null && raw.isBlank()) return null
        val number = com.bazaarvoice.push.device.PlayIntegrityInput.cloudProjectNumber(raw)
        if (number == null) {
            Logger.log(Level.WARN, TAG,
                "$CLOUD_PROJECT_NUMBER_KEY must be a string resource of digits; sending no project number")
        }
        return number
    }

    private fun readManifestAppId(appContext: Context): Int? {
        return try {
            appContext.packageManager.getApplicationInfo(
                appContext.packageName, android.content.pm.PackageManager.GET_META_DATA,
            ).metaData?.getInt("com.bazaarvoice.push.APP_ID", 0)?.takeIf { it != 0 }
        } catch (_: Exception) {
            null
        }
    }

    public fun handleRemoteMessage(context: Context, data: Map<String, String>) {
        // Cold-start race (swiped-away/killed app): FCM starts only our
        // service, and auto-init may not have built the stores yet. Build
        // them synchronously (cheap local I/O, no network) so policy reads
        // real persisted state instead of judging on empty stores.
        ensureStores(context.applicationContext)
        when (val parsed = PushPayloadParser.parse(data)) {
            is ParsedPush.NotBv -> return
            is ParsedPush.Invalid -> {
                android.util.Log.i(TAG, "BVPush invalid id=${data["correspondence_id"]}")
                emit(BVPushEvent.Error("INVALID_PUSH", null))
                return
            }
            is ParsedPush.BvPush -> {
                android.util.Log.i(TAG, "BVPush handling id=${parsed.notificationId}")
                emit(BVPushEvent.NotificationReceived(parsed.notificationId))
                val ls = settingsStore?.get(STATE_KEY)?.let { LocalState.decode(it) }
                val window = quietWindow()
                val quiet = window != null &&
                    QuietHours.isQuiet(window, wallMinutesNow())
                val decision = DisplayPolicy.decide(
                    parsed,
                    optedOut = ls?.desiredOptedOut == true,
                    loggedOut = ls?.userId == null &&
                        secureStore?.get("auth-token") == null,
                    isTestPush = data["is_test"] == "true",
                    inQuietWindow = quiet,
                )
                when (decision) {
                    DisplayDecision.NOT_HANDLED, DisplayDecision.DROP -> return
                    DisplayDecision.SUPPRESS_OPTED_OUT -> {
                        android.util.Log.i(TAG, "BVPush suppressed id=${parsed.notificationId} reason=opted-out")
                        emit(BVPushEvent.NotificationSuppressed(parsed.notificationId, "opted-out"))
                    }
                    DisplayDecision.SUPPRESS_LOGGED_OUT -> {
                        android.util.Log.i(TAG, "BVPush suppressed id=${parsed.notificationId} reason=logged-out")
                        emit(BVPushEvent.NotificationSuppressed(parsed.notificationId, "logged-out"))
                    }
                    DisplayDecision.HOLD -> hold(context, parsed, window)
                    DisplayDecision.DISPLAY -> display(context, parsed)
                }
            }
        }
    }

    private fun display(context: Context, parsed: ParsedPush.BvPush) {
        val result = AndroidNotificationBuilder.show(
            context, parsed.notificationId.hashCode(), parsed,
            parsed.ctaText, tapHandler,
        )
        when (result) {
            AndroidNotificationBuilder.PostResult.POSTED -> {
                android.util.Log.i(TAG, "BVPush displayed id=${parsed.notificationId}")
                emit(BVPushEvent.NotificationDisplayed(parsed.notificationId))
            }
            AndroidNotificationBuilder.PostResult.SUPPRESSED_BLOCKED ->
                emit(BVPushEvent.NotificationSuppressed(parsed.notificationId, "channel-blocked"))
            AndroidNotificationBuilder.PostResult.SUPPRESSED_DENIED ->
                emit(BVPushEvent.NotificationSuppressed(parsed.notificationId, "permission-denied"))
        }
    }

    /** Encrypted held payload + WorkManager release at window end. */
    private fun hold(context: Context, parsed: ParsedPush.BvPush, window: QuietWindow?) {
        val secure = secureStore ?: return
        val nowMinutes = wallMinutesNow()
        val releaseIn = if (window != null) QuietHours.releaseInMinutes(window, nowMinutes) else 60
        val untilMillis = System.currentTimeMillis() + releaseIn * 60_000L
        secure.put(HELD_PREFIX + parsed.notificationId, encodeHeld(parsed))
        QuietHoursReleaseWorker.enqueueAt(context, parsed.notificationId, releaseIn.toLong())
        android.util.Log.i(TAG, "BVPush held id=${parsed.notificationId} releaseMin=$releaseIn")
        emit(BVPushEvent.NotificationHeld(parsed.notificationId, untilMillis))
    }

    internal fun releaseHeld(context: Context, notificationId: String?) {
        if (notificationId == null) return
        val secure = secureStore ?: return
        val blob = secure.get(HELD_PREFIX + notificationId) ?: return
        secure.remove(HELD_PREFIX + notificationId)
        val parsed = decodeHeld(blob) ?: return
        display(context, parsed)
        emit(BVPushEvent.NotificationOpened(notificationId))
    }

    private fun encodeHeld(parsed: ParsedPush.BvPush): String =
        listOf(
            parsed.notificationId, parsed.title, parsed.body,
            parsed.imageUrl ?: "", parsed.reviewUrl ?: "", parsed.ctaText ?: "",
        ).joinToString("\u0001") { it.replace("\\", "\\\\").replace("\u0001", "\\u0001") }

    private fun decodeHeld(blob: String): ParsedPush.BvPush? {
        val parts = blob.split("\u0001").map { it.replace("\\u0001", "\u0001").replace("\\\\", "\\") }
        if (parts.size != 6) return null
        return ParsedPush.BvPush(
            notificationId = parts[0], title = parts[1], body = parts[2],
            imageUrl = parts[3].ifBlank { null },
            reviewUrl = parts[4].ifBlank { null },
            ctaText = parts[5].ifBlank { null },
        )
    }

    public fun onNewToken(context: Context, token: String) {
        hostPushToken = token
        reconcileLater(false)
    }

    public fun setPushToken(token: String) {
        hostPushToken = token
        reconcileLater(false)
    }

    public fun setNotificationTapHandler(handler: ((String) -> Boolean)?) {
        tapHandler = handler
    }

    public fun openReview(notificationId: String): Boolean = false

    public val deviceId: String? get() = _state.value.deviceId

    public fun diagnostics(): Diagnostics {
        val ls = settingsStore?.get(STATE_KEY)?.let { LocalState.decode(it) }
        val manager = app?.getSystemService(NotificationManager::class.java)
        return Diagnostics(
            sdkVersion = SdkInfo.VERSION,
            appId = appId.takeIf { it != 0 },
            registered = _state.value.deviceId != null,
            deviceId = _state.value.deviceId,
            tokenSynced = ls?.syncedTokenHash != null,
            identityPhase = ls?.identityPhase ?: "anonymous",
            hasSession = secureStore?.get("auth-token") != null,
            sessionExpiry = secureStore?.get("auth-expires-at"),
            desiredOptedOut = ls?.desiredOptedOut == true,
            serverOptedOut = ls?.serverOptedOut,
            quietWindow = quietWindow()?.toString(),
            notificationPermission = when {
                manager == null -> "unknown"
                manager.areNotificationsEnabled() -> "granted"
                else -> "denied"
            },
            fcmConflict = app?.let { !FcmServiceConflictDetector.isOurs(it) } ?: false,
            lastError = _state.value.lastError,
            lastCorrelationId = null,
        )
    }

    public fun reset() {
        reconcileLater(false)
    }

    internal fun onConnectivityRestored() {
        reconcileLater(false)
    }

    internal fun onKeepAlive() {
        reconcileLater(true)
    }

    internal fun openedFromTap(context: Context, notificationId: String, reviewUrl: String?) {
        android.util.Log.i(TAG, "BVPush opened id=$notificationId")
        emit(BVPushEvent.NotificationOpened(notificationId))
        if (reviewUrl != null && tapHandler?.invoke(notificationId) != true) {
            if (ReviewLinkValidator.isOpenable(reviewUrl)) {
                // No Custom Tabs provider and no browser at all →
                // error event instead of a silent nothing.
                if (CustomTabsReviewPresenter.present(context, reviewUrl)) {
                    android.util.Log.i(TAG, "BVPush review id=$notificationId")
                    emit(BVPushEvent.ReviewOpened(notificationId, "custom-tabs"))
                } else {
                    emit(BVPushEvent.Error("REVIEW_UNOPENABLE", null))
                }
            }
        }
    }

    private fun fetchToken(context: Context) {
        if (!options.autoFetchFcmToken) return
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { onNewToken(context, it) }
        } catch (_: Exception) {
            emit(BVPushEvent.Error("NO_TOKEN", null))
        }
    }

    private fun emit(event: BVPushEvent) {
        _events.tryEmit(event)
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitNullable(): T? {
        // Bounded: an uncompleted Firebase task (no connectivity to Google)
        // must read as "unavailable", never hang the reconcile (and the
        // facade mutex) forever.
        return kotlinx.coroutines.withTimeoutOrNull(15_000L) {
            var result: T? = null
            var done = false
            addOnSuccessListener { result = it; done = true }
            addOnFailureListener { done = true }
            while (!done) kotlinx.coroutines.delay(50)
            result
        }
    }
}
