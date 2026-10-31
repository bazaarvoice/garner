package bv.garner.test.app

import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bazaarvoice.push.BVPush
import com.bazaarvoice.push.config.BVPushOptions
import com.bazaarvoice.push.events.Diagnostics
import com.google.firebase.messaging.FirebaseMessaging
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BV Garner test app: four tabs (Home, Account, Inbox, Details) over the real
 * SDK. See test-app/android/README.md.
 *
 * The Details tab shows the SDK inspector text ([inspectorText]); keep its format.
 *
 * Test driver (debug builds only, gated by BuildConfig.DEBUG):
 * `adb shell am start` with a `cmd` extra drives the real SDK against the
 * local Garner Backend (emulator 10.0.2.2 → host). Every command logs a
 * `BVPushDriver` line that test scripts can assert on. Release builds ignore `cmd`.
 *
 *   adb shell am start -n bv.garner.test.app/.MainActivity \
 *     --es cmd INIT --ei appId 10000001 --es baseUrl http://10.0.2.2:8081
 */
class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val openedAt = SystemClock.elapsedRealtime()

    // Latest Android rule (API 33+): POST_NOTIFICATIONS must be requested at
    // runtime; fresh installs deny by default and renders silently vanish.
    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    private lateinit var tester: Tester
    private lateinit var shell: Shell
    private lateinit var inbox: InboxTab
    private lateinit var tabs: List<Tab>
    private lateinit var inspector: TextView
    private var selected = TabId.HOME
    private var loggedDeviceId: String? = null
    private var fcmToken: String? = null

    private val backToHome = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = select(TabId.HOME)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // API 35 enforces edge-to-edge; do it on every API so the dark top bar
        // runs under the status bar (light icons). Shell pads for the bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, NAV_BAR_SCRIM),
        )
        super.onCreate(savedInstanceState)
        // GarnerTestApp already started both; repeat calls are no-ops.
        ActivityLog.start()
        PushHistory.start(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionRequest.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        tester = Tester(this, navigate = { select(it) }, refresh = { refresh() })
        inspector = tester.ui.mono(12f, BvColors.text)
        inbox = InboxTab(tester)
        tabs = listOf(HomeTab(tester), AccountTab(tester), inbox, DetailsTab(tester, inspector))
        shell = Shell(tester, tabs) { select(it) }
        setContentView(shell.root)
        onBackPressedDispatcher.addCallback(this, backToHome)
        val saved = savedInstanceState?.getString(KEY_TAB)
        select(TabId.fromName(saved) ?: TabId.HOME)
        if (BuildConfig.DEBUG) selectFromIntent(intent)

        // Live screens: init resolves off the main thread, so they observe state.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                BVPush.state.collect { refresh() }
            }
        }
        // Session, quiet hours and the notification permission change without a
        // state emission: re-read them while the screen is visible. Views are only
        // touched when a value changed.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(POLL_MS)
                    val diag = withContext(Dispatchers.Default) { readDiagnostics() }
                    render(diag)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PushHistory.records.collect { records ->
                    inbox.setRecords(records)
                    refresh()
                }
            }
        }
        readFcmToken()

        // A driver command brings its own init (appId + baseUrl); auto-init
        // would race it with a production coordinator.
        if (BuildConfig.DEBUG && intent?.hasExtra(EXTRA_CMD) == true) {
            handleDriver(intent)
            return
        }
        // An HTTPS Garner Backend (client.properties) is already initialized by the
        // SDK's manifest auto-init with the same app id and URL. The local stack is
        // plain HTTP, which only an explicit allowInsecureLocalhost init accepts; it
        // uses the same namespace as the driver flows, so the screens never show a
        // different (empty) namespace's state after taps and relaunches.
        if (!BuildConfig.BV_BASE_URL.startsWith(HTTPS)) {
            BVPush.init(
                this, BuildConfig.BV_APP_ID,
                BVPushOptions(
                    baseUrlOverride = BuildConfig.BV_BASE_URL,
                    allowInsecureLocalhost = true,
                ),
            )
        }
        if (BuildConfig.DEBUG) handleDriver(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, selected.name)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i(TAG, "onNewIntent cmd=${intent.getStringExtra(EXTRA_CMD)}")
        if (BuildConfig.DEBUG) {
            selectFromIntent(intent)
            handleDriver(intent)
        }
    }

    /** Debug builds: `--es tab DETAILS` opens that tab (screenshots, test scripts). */
    private fun selectFromIntent(intent: Intent?) {
        TabId.fromName(intent?.getStringExtra(EXTRA_TAB))?.let { select(it) }
    }

    private fun select(id: TabId) {
        selected = id
        backToHome.isEnabled = id != TabId.HOME
        shell.select(id)
        refresh()
    }

    private fun refresh() = render(readDiagnostics())

    private fun readDiagnostics(): Diagnostics? = runCatching { BVPush.diagnostics() }.getOrNull()

    /** Repaints every tab (each skips unchanged views) and the top bar. Main thread. */
    private fun render(diag: Diagnostics?) {
        if (!::shell.isInitialized) return
        val state = BVPush.state.value
        logDeviceId(state.deviceId)
        val text = inspectorText(state, diag)
        if (inspector.text.toString() != text) inspector.text = text

        val now = Calendar.getInstance()
        val snapshot = Snapshot(
            state = state,
            diag = diag,
            notificationsAllowed = notificationsAllowed(),
            fcmToken = fcmToken,
            slow = SystemClock.elapsedRealtime() - openedAt > SLOW_MS,
            nowMillis = now.timeInMillis,
            nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
        )
        tabs.forEach { it.render(snapshot) }
        shell.update(tabs.first { it.id == selected }, snapshot)

        val records = PushHistory.records.value
        if (selected == TabId.INBOX) {
            records.maxOfOrNull { it.firstAtMillis }?.let { tester.prefs.markInboxSeen(it) }
            shell.setBadge(TabId.INBOX, 0)
        } else {
            shell.setBadge(TabId.INBOX, PushRecords.unseen(records, tester.prefs.inboxSeenAt))
        }
    }

    /** Logs the device id once for adb users and test scripts. */
    private fun logDeviceId(deviceId: String?) {
        if (deviceId != null && deviceId != loggedDeviceId) {
            loggedDeviceId = deviceId
            Log.i(TAG, "$LOG_DEVICE_ID $deviceId")
        }
    }

    private fun readFcmToken() {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                fcmToken = token
                refresh()
            }
        }
    }

    private fun notificationsAllowed(): Boolean =
        getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: true

    private fun handleDriver(intent: Intent?) {
        val cmd = intent?.getStringExtra(EXTRA_CMD) ?: return
        scope.launch {
            // Every command is self-contained (cold start): the image does
            // not deliver repeat starts to onNewIntent.
            val appId = intent.getIntExtra(EXTRA_APP_ID, DRIVER_APP_ID)
            val baseUrl = intent.getStringExtra(EXTRA_BASE_URL) ?: DRIVER_BASE_URL
            when (cmd) {
                CMD_INIT -> {
                    driverInit(appId, baseUrl)
                    delay(8000)
                    dump(PHASE_INIT)
                }
                CMD_LINK -> {
                    val userId = intent.getStringExtra(EXTRA_USER_ID) ?: return@launch
                    val email = intent.getStringExtra(EXTRA_EMAIL) ?: return@launch
                    driverInit(appId, baseUrl)
                    delay(4000)
                    runCatching { BVPush.linkIdentity(userId, email) }
                        .onSuccess { TesterPrefs(this@MainActivity).saveIdentity(userId, email) }
                        .onFailure { Log.i(TAG, "$LOG_LINK_ERROR ${it.message}") }
                    delay(8000)
                    dump(PHASE_LINK)
                }
                CMD_DUMP -> dump(PHASE_MANUAL)
                CMD_QUIET -> {
                    val quietUrl = intent.getStringExtra(EXTRA_BASE_URL) ?: DRIVER_QUIET_BASE_URL
                    driverInit(appId, quietUrl)
                    delay(4000)
                    val sh = intent.getIntExtra(EXTRA_START_HOUR, 23)
                    val sm = intent.getIntExtra(EXTRA_START_MINUTE, 0)
                    val eh = intent.getIntExtra(EXTRA_END_HOUR, 8)
                    val em = intent.getIntExtra(EXTRA_END_MINUTE, 0)
                    BVPush.setQuietHours(sh, sm, eh, em)
                    delay(1000)
                    dump(PHASE_QUIET)
                }
            }
        }
    }

    private fun driverInit(appId: Int, baseUrl: String) {
        BVPush.init(
            this@MainActivity, appId,
            BVPushOptions(
                baseUrlOverride = baseUrl,
                allowInsecureLocalhost = true,
                autoFetchFcmToken = false,
            ),
        )
    }

    /** The inspector block support and UI tests read; keep the format. */
    private fun inspectorText(state: BVPush.PublicState, diag: Diagnostics?): String =
        "BVPush initialized=${state.initialized}\n" +
            "deviceId=${state.deviceId ?: UiText.NONE}\n" +
            "session=${diag?.hasSession}\n" +
            "optedOut=${diag?.desiredOptedOut}\n" +
            "error=${state.lastError ?: diag?.lastError ?: UiText.NONE}"

    /** Test scripts read these logcat lines; keep the format. */
    private fun dump(phase: String) {
        val state = BVPush.state.value
        val diag = readDiagnostics()
        Log.i(TAG, "$LOG_STATE phase=$phase initialized=${state.initialized} " +
            "deviceId=${state.deviceId} error=${state.lastError} diag=$diag")
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { Log.i(TAG, "$LOG_FCM_TOKEN $it") }
        }
    }

    companion object {
        const val TAG = "BVPushDriver"

        private const val KEY_TAB = "selectedTab"
        private const val HTTPS = "https://"
        private const val POLL_MS = 1500L
        private const val SLOW_MS = 30_000L

        // Driver intent extras and commands.
        private const val EXTRA_CMD = "cmd"
        private const val EXTRA_TAB = "tab"
        private const val EXTRA_APP_ID = "appId"
        private const val EXTRA_BASE_URL = "baseUrl"
        private const val EXTRA_USER_ID = "userId"
        private const val EXTRA_EMAIL = "email"
        private const val EXTRA_START_HOUR = "startHour"
        private const val EXTRA_START_MINUTE = "startMinute"
        private const val EXTRA_END_HOUR = "endHour"
        private const val EXTRA_END_MINUTE = "endMinute"
        private const val CMD_INIT = "INIT"
        private const val CMD_LINK = "LINK"
        private const val CMD_DUMP = "DUMP"
        private const val CMD_QUIET = "QUIET"
        private const val DRIVER_APP_ID = 10000001
        private const val DRIVER_BASE_URL = "http://10.0.2.2:8081"
        private const val DRIVER_QUIET_BASE_URL = "http://10.0.2.2:8082"

        // Logcat lines test scripts assert on.
        private const val LOG_STATE = "STATE"
        private const val LOG_DEVICE_ID = "DEVICEID"
        private const val LOG_FCM_TOKEN = "FCMTOKEN"
        private const val LOG_LINK_ERROR = "LINK-ERROR"
        private const val PHASE_INIT = "after-INIT"
        private const val PHASE_LINK = "after-LINK"
        private const val PHASE_QUIET = "after-QUIET"
        private const val PHASE_MANUAL = "manual"

        // Behind 3-button navigation on APIs without dark navigation icons.
        private val NAV_BAR_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
