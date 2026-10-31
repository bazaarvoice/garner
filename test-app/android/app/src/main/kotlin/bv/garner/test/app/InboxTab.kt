package bv.garner.test.app

import android.app.AlertDialog
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.text.TextUtils
import java.util.TimeZone

/**
 * Inbox: every push the SDK reported on this device ([PushHistory]), newest
 * first, with filters, the SDK steps of each push and Clear. Tapping a card
 * copies its notification id.
 */
class InboxTab(private val tester: Tester) : Tab {
    override val id = TabId.INBOX
    private val ui = tester.ui
    private var filter = PushFilter.ALL
    private var records: List<PushRecord> = emptyList()
    private var shownKey: Any? = null

    private val filters = ui.horizontal()
    private val list = ui.vertical()

    override val view: View = tester.scroll(ui.vertical().apply {
        addView(ui.horizontal().apply {
            layoutParams = LinearLayout.LayoutParams(BvViews.MATCH, BvViews.WRAP).apply { topMargin = ui.dp(12) }
            addView(HorizontalScrollView(tester.activity).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(0, BvViews.WRAP, 1f)
                addView(filters)
            })
            addView(ui.filterChip(UiText.CLEAR, selected = false, color = BvColors.bad) { confirmClear() }.apply {
                (layoutParams as LinearLayout.LayoutParams).apply {
                    marginEnd = 0
                    marginStart = ui.dp(6)
                }
            })
        })
        addView(list)
    })

    /** New history from [PushHistory.records]. */
    fun setRecords(latest: List<PushRecord>) {
        records = latest
    }

    override fun title(snapshot: Snapshot): String = UiText.TAB_INBOX

    override fun chips(snapshot: Snapshot): List<ToneText> =
        listOf(ToneText(UiText.inboxChip(records.size), Tone.NORMAL))

    override fun render(snapshot: Snapshot) {
        if (view.visibility != View.VISIBLE) return
        // Times read "Yesterday" after midnight: repaint once a minute too.
        val key = Triple(records, filter, snapshot.nowMillis / MINUTE_MILLIS)
        if (key == shownKey) return
        shownKey = key
        renderFilters()
        renderList(snapshot.nowMillis)
    }

    private fun renderFilters() {
        filters.removeAllViews()
        PushFilter.entries.forEach { option ->
            val label = UiText.filterChip(label(option), PushRecords.count(records, option))
            filters.addView(ui.filterChip(label, selected = option == filter, color = BvColors.primary) {
                filter = option
                tester.refresh()
            })
        }
    }

    private fun renderList(nowMillis: Long) {
        list.removeAllViews()
        val zone = TimeZone.getDefault()
        val shown = records.filter { filter.matches(it) }
        if (shown.isEmpty()) {
            list.addView(ui.helper(if (records.isEmpty()) UiText.INBOX_EMPTY else UiText.INBOX_EMPTY_FILTER).apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(20)
            })
            return
        }
        shown.forEach { list.addView(card(it, nowMillis, zone)) }
    }

    private fun card(record: PushRecord, nowMillis: Long, zone: TimeZone): View = ui.card().apply {
        addView(ui.horizontal().apply {
            gravity = android.view.Gravity.TOP
            addView(ui.text(15f, BvColors.text, PushLabels.title(record), bold = true).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, BvViews.WRAP, 1f)
            })
            addView(ui.text(12f, BvColors.textSecondary, TimeText.inbox(record.firstAtMillis, nowMillis, zone)).apply {
                setPadding(ui.dp(8), ui.dp(2), 0, 0)
            })
        })
        addView(ui.helper(PushLabels.body(record)))
        addView(HorizontalScrollView(tester.activity).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(BvViews.MATCH, BvViews.WRAP).apply { topMargin = ui.dp(10) }
            addView(ui.horizontal().apply {
                PushLabels.chips(record, zone).forEach { addView(ui.chip(it.text, it.tone)) }
            })
        })
        isClickable = true
        setOnClickListener { tester.copy(UiText.NOTIFICATION_ID, record.id) }
    }

    private fun confirmClear() {
        if (records.isEmpty()) return
        AlertDialog.Builder(tester.activity)
            .setTitle(UiText.CLEAR_TITLE)
            .setMessage(UiText.clearMessage(records.size))
            .setPositiveButton(UiText.CLEAR) { _, _ -> PushHistory.clear() }
            .setNegativeButton(UiText.CANCEL, null)
            .show()
    }

    private fun label(option: PushFilter): String = when (option) {
        PushFilter.ALL -> UiText.FILTER_ALL
        PushFilter.SHOWN -> UiText.FILTER_SHOWN
        PushFilter.HELD -> UiText.FILTER_HELD
        PushFilter.HIDDEN -> UiText.FILTER_HIDDEN
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}
