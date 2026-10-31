package bv.garner.test.app

import android.content.Intent
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.TimeZone

/**
 * Details: every data point an integrator asks for, each with Copy; Copy all
 * as JSON and Share; the recent SDK events; and the raw inspector text.
 */
class DetailsTab(private val tester: Tester, inspector: TextView) : Tab {
    override val id = TabId.DETAILS
    private val ui = tester.ui
    private var values: List<Pair<ReportField, String>> = emptyList()
    private var shownEvents: List<ActivityLog.Entry>? = null

    private val rows: Map<ReportField, BvViews.KeyValueRow> = ReportField.entries.associateWith { field ->
        ui.KeyValueRow(field.label, if (field.copyable) ({ copy(field) }) else null)
    }
    private val events = ui.vertical()

    override val view: View = tester.scroll(ui.vertical().apply {
        addView(ui.card().apply {
            ReportSection.entries.forEach { section ->
                addView(ui.sectionLabel(section.title).apply {
                    if (section == ReportSection.entries.first()) {
                        (layoutParams as LinearLayout.LayoutParams).topMargin = 0
                    }
                })
                ReportField.entries.filter { it.section == section }.forEach { addView(rows.getValue(it).view) }
            }
        })
        // The gap above the buttons is the row's margin: a top margin on the buttons themselves,
        // inside a row that centres its children vertically, pushed their bottom edge out of the row.
        addView(ui.horizontal().apply {
            layoutParams = LinearLayout.LayoutParams(BvViews.MATCH, BvViews.WRAP).apply { topMargin = ui.dp(12) }
            addView(ui.primaryButton(UiText.COPY_ALL) { copyAll() }.apply {
                layoutParams = LinearLayout.LayoutParams(0, BvViews.WRAP, 1f)
            })
            addView(ui.outlinedButton(UiText.SHARE) { share() }.apply {
                layoutParams = LinearLayout.LayoutParams(0, BvViews.WRAP, 1f).apply { marginStart = ui.dp(10) }
            })
        })
        addView(ui.card(UiText.EVENTS_TITLE).apply {
            addView(ui.helper(UiText.EVENTS_HELP))
            addView(events.apply {
                layoutParams = LinearLayout.LayoutParams(BvViews.MATCH, BvViews.WRAP).apply { topMargin = ui.dp(8) }
            })
        })
        addView(rawCard(inspector))
    })

    override fun title(snapshot: Snapshot): String = UiText.TAB_DETAILS

    override fun chips(snapshot: Snapshot): List<ToneText> = listOf(ToneText(UiText.DETAILS_CHIP, Tone.NORMAL))

    override fun render(snapshot: Snapshot) {
        val input = ReportInput(
            diag = snapshot.diag,
            deviceId = snapshot.state.deviceId,
            stateError = snapshot.state.lastError,
            fcmToken = snapshot.fcmToken,
            appName = tester.appName,
            packageName = tester.activity.packageName,
            appId = snapshot.appId,
            baseUrl = BuildConfig.BV_BASE_URL,
            userId = tester.prefs.userId,
            email = tester.prefs.email,
            quiet = snapshot.quiet,
            zone = TimeZone.getDefault(),
        )
        values = DetailsReport.values(input)
        if (view.visibility != View.VISIBLE) return
        values.forEach { (field, value) -> rows.getValue(field).set(value) }
        renderEvents(ActivityLog.entries.value)
    }

    private fun renderEvents(entries: List<ActivityLog.Entry>) {
        if (entries == shownEvents) return
        shownEvents = entries
        events.removeAllViews()
        if (entries.isEmpty()) {
            events.addView(ui.text(14f, BvColors.textSecondary, UiText.EVENTS_EMPTY))
            return
        }
        val zone = TimeZone.getDefault()
        entries.forEach { entry ->
            events.addView(ui.horizontal().apply {
                gravity = android.view.Gravity.TOP
                setPadding(0, ui.dp(5), 0, ui.dp(5))
                addView(ui.mono(13f, BvColors.textSecondary, TimeText.clock(entry.atMillis, zone)))
                addView(ui.text(14f, toneColor(entry.line.tone), entry.line.text).apply {
                    layoutParams = LinearLayout.LayoutParams(0, BvViews.WRAP, 1f).apply { marginStart = ui.dp(12) }
                })
            })
        }
    }

    private fun rawCard(inspector: TextView) = ui.card().apply {
        val toggle = ui.text(14f, BvColors.primary, UiText.HIDE, bold = true).apply {
            setPadding(ui.dp(12), ui.dp(6), 0, ui.dp(6))
        }
        addView(ui.horizontal().apply {
            addView(ui.text(17f, BvColors.text, UiText.RAW_TITLE, bold = true).apply {
                layoutParams = LinearLayout.LayoutParams(0, BvViews.WRAP, 1f)
            })
            addView(toggle)
        })
        addView(ui.helper(UiText.RAW_HELP))
        addView(inspector.apply {
            typeface = Typeface.MONOSPACE
            background = ui.rounded(BvColors.code, 8)
            setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
            layoutParams = LinearLayout.LayoutParams(BvViews.MATCH, BvViews.WRAP).apply { topMargin = ui.dp(8) }
        })
        toggle.setOnClickListener {
            val showing = inspector.visibility == View.VISIBLE
            inspector.visibility = if (showing) View.GONE else View.VISIBLE
            toggle.text = if (showing) UiText.SHOW else UiText.HIDE
        }
    }

    private fun copy(field: ReportField) {
        val value = values.firstOrNull { it.first == field }?.second ?: return
        tester.copy(field.label, value)
    }

    private fun copyAll() {
        if (values.isEmpty()) return
        tester.copy(UiText.CLIP_DETAILS, DetailsReport.toJson(values))
    }

    private fun share() {
        if (values.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME_TEXT)
            .putExtra(Intent.EXTRA_SUBJECT, UiText.CLIP_DETAILS)
            .putExtra(Intent.EXTRA_TEXT, DetailsReport.toJson(values))
        runCatching { tester.activity.startActivity(Intent.createChooser(send, UiText.SHARE_TITLE)) }
    }

    private fun toneColor(tone: Tone): Int = BvColors.strong(tone)

    private companion object {
        const val MIME_TEXT = "text/plain"
    }
}
