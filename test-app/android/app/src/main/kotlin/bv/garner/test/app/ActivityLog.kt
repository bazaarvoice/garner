package bv.garner.test.app

import com.bazaarvoice.push.BVPush
import com.bazaarvoice.push.events.BVPushEvent
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The last SDK events from [BVPush.events], newest first, in plain words
 * ([EventText]), and the step a sign-in waits for ([loginWait]). Started by
 * [GarnerTestApp] and kept for the process.
 */
object ActivityLog {
    data class Entry(val atMillis: Long, val line: ToneText)

    private const val MAX_ENTRIES = 30
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries
    private val _loginWait = MutableStateFlow<String?>(null)
    /** The reason of the last [BVPushEvent.IdentityPending] since [clearLoginWait], else null. */
    val loginWait: StateFlow<String?> = _loginWait
    private var started = false

    /** Main thread. Subscribes before returning, so events from an init right after are kept. */
    fun start() {
        if (started) return
        started = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            BVPush.events.collect { event ->
                if (event is BVPushEvent.IdentityPending) _loginWait.value = event.reason
                val entry = Entry(System.currentTimeMillis(), EventText.describe(event, TimeZone.getDefault()))
                _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
            }
        }
    }

    /** Before a new sign-in, so only its own wait shows. */
    fun clearLoginWait() {
        _loginWait.value = null
    }
}
