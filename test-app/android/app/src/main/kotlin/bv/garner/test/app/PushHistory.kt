package bv.garner.test.app

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.bazaarvoice.push.AndroidNotificationBuilder
import com.bazaarvoice.push.BVPush
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Every push the SDK reports, kept on the device for the Inbox until the
 * tester taps Clear (newest [PushRecords.MAX_RECORDS]). Started by
 * [GarnerTestApp], so pushes that arrive while no screen is open are kept too.
 *
 * Storage: append-only `push-history.log` in the app files dir, format
 * [PushLogCodec]. Titles come from the posted notification (tag
 * [AndroidNotificationBuilder.TAG], id = notification id hashCode); hidden
 * pushes are never posted, so they keep only their id.
 */
object PushHistory {
    private const val FILE_NAME = "push-history.log"
    private const val TAG = "GarnerPushHistory"

    /** Rewrite the file once this many records past the limit piled up. */
    private const val COMPACT_SLACK = 50
    private const val LOOKUP_ATTEMPTS = 5
    private const val LOOKUP_DELAY_MS = 300L

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + io)

    /** Touched only on [io]. */
    private val lines = mutableListOf<PushLine>()
    private var file: File? = null
    private var started = false

    private val _records = MutableStateFlow<List<PushRecord>>(emptyList())
    val records: StateFlow<List<PushRecord>> = _records

    /** Main thread. Subscribes to SDK events before returning. */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val target = File(app.filesDir, FILE_NAME)
        val loaded = scope.launch { load(target) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            BVPush.events.collect { event ->
                val entry = PushEvents.toEntry(event, System.currentTimeMillis()) ?: return@collect
                withContext(io) {
                    loaded.join()
                    append(listOf(entry))
                }
                if (entry.step == PushStep.SHOWN) scope.launch { readContent(app, entry.id) }
            }
        }
    }

    /** Deletes every record and the file. */
    fun clear() {
        scope.launch {
            lines.clear()
            runCatching { file?.delete() }
            publish()
        }
    }

    private fun load(target: File) {
        file = target
        val stored = runCatching { if (target.isFile) target.readLines() else emptyList() }
            .onFailure { Log.w(TAG, "history not read", it) }
            .getOrDefault(emptyList())
        lines.addAll(stored.mapNotNull { PushLogCodec.decode(it) })
        compactIfNeeded(force = stored.size != lines.size)
        publish()
    }

    private fun append(added: List<PushLine>) {
        lines.addAll(added)
        runCatching {
            file?.appendText(added.joinToString(separator = "") { PushLogCodec.encode(it) + "\n" })
        }.onFailure { Log.w(TAG, "history not written", it) }
        compactIfNeeded(force = false)
        publish()
    }

    /** Keeps the newest records; [force] rewrites the file (for example after broken lines). */
    private fun compactIfNeeded(force: Boolean) {
        val count = lines.mapTo(HashSet()) { it.id }.size
        if (!force && count <= PushRecords.MAX_RECORDS + COMPACT_SLACK) return
        val kept = PushRecords.limit(lines)
        lines.clear()
        lines.addAll(kept)
        runCatching {
            file?.writeText(kept.joinToString(separator = "") { PushLogCodec.encode(it) + "\n" })
        }.onFailure { Log.w(TAG, "history not compacted", it) }
    }

    private fun publish() {
        _records.value = PushRecords.group(lines).take(PushRecords.MAX_RECORDS)
    }

    /** Reads title and body of the posted notification; it may take a moment to appear. */
    private suspend fun readContent(context: Context, id: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        repeat(LOOKUP_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(LOOKUP_DELAY_MS)
            val posted = runCatching { manager.activeNotifications }.getOrNull()
                ?.firstOrNull { it.tag == AndroidNotificationBuilder.TAG && it.id == id.hashCode() }
            if (posted != null) {
                val extras = posted.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                append(listOf(PushContent(id, title, body)))
                return
            }
        }
    }
}
