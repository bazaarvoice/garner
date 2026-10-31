package bv.garner.test.app

import android.view.View
import android.widget.LinearLayout

/**
 * Home: is this device ready for pushes? A banner (n of 5), the [Checklist]
 * with one action per problem, and the test push steps.
 */
class HomeTab(private val tester: Tester) : Tab {
    override val id = TabId.HOME
    private val ui = tester.ui
    private var deviceId: String? = null
    private var shownSummary: CheckSummary? = null

    private val bannerMark = ui.markBadge(Mark.WAIT, 36)
    private val bannerTitle = ui.text(16f, BvColors.waiting, bold = true)
    private val bannerDetail = ui.text(13f, BvColors.textSecondary)
    private val progress = ui.Progress()
    private val banner = ui.card().apply {
        addView(ui.horizontal().apply {
            addView(bannerMark)
            addView(ui.vertical().apply {
                layoutParams = LinearLayout.LayoutParams(0, BvViews.WRAP, 1f).apply { marginStart = ui.dp(12) }
                addView(bannerTitle)
                addView(bannerDetail)
                addView(progress.view)
            })
        })
    }

    private val rows = linkedMapOf(
        CheckKind.REGISTERED to ui.CheckRow(UiText.COPY) { copyDeviceId() },
        CheckKind.PUSH_TOKEN to ui.CheckRow(),
        CheckKind.SIGNED_IN to ui.CheckRow(UiText.ACTION_SIGN_IN, filledAction = true) {
            tester.navigate(TabId.ACCOUNT)
        },
        CheckKind.NOTIFICATIONS to ui.CheckRow(UiText.ACTION_ALLOW, filledAction = true) {
            tester.openNotificationSettings()
        },
        CheckKind.QUIET_HOURS to ui.CheckRow(UiText.ACTION_CHANGE) { tester.navigate(TabId.ACCOUNT) },
    )

    private val copyButton = ui.primaryButton(UiText.COPY_DEVICE_ID_WAITING) { copyDeviceId() }.apply {
        isEnabled = false
    }

    override val view: View = tester.scroll(ui.vertical().apply {
        addView(banner)
        addView(ui.card(UiText.CHECKLIST).apply { rows.values.forEach { addView(it.view) } })
        addView(ui.card(UiText.TEST_PUSH_TITLE).apply {
            addView(ui.helper(UiText.TEST_PUSH_STEPS))
            addView(copyButton)
        })
    })

    override fun title(snapshot: Snapshot): String = tester.appName

    override fun chips(snapshot: Snapshot): List<ToneText> = listOf(
        ToneText(AppInfo.environment(BuildConfig.BV_BASE_URL), Tone.NORMAL),
        ToneText(UiText.CHIP_APP_ID + snapshot.appId, Tone.NORMAL),
        ToneText(UiText.CHIP_SDK + (snapshot.diag?.sdkVersion ?: UiText.UNKNOWN), Tone.NORMAL),
    )

    override fun render(snapshot: Snapshot) {
        deviceId = snapshot.state.deviceId
        val quiet = snapshot.quiet
        val checks = Checklist.evaluate(
            HealthInput(
                initialized = snapshot.state.initialized,
                deviceId = snapshot.state.deviceId,
                tokenSynced = snapshot.diag?.tokenSynced == true,
                hasSession = snapshot.signedIn,
                notificationsAllowed = snapshot.notificationsAllowed,
                quiet = quiet,
                slow = snapshot.slow,
            )
        )
        checks.forEach { check -> renderRow(check, snapshot, quiet) }
        renderBanner(checks)

        val ready = deviceId != null
        copyButton.isEnabled = ready
        val label = if (ready) UiText.COPY_DEVICE_ID else UiText.COPY_DEVICE_ID_WAITING
        if (copyButton.text.toString() != label) copyButton.text = label
    }

    private fun renderRow(check: Check, snapshot: Snapshot, quiet: QuietStatus) {
        val mark = Mark.of(check.mark)
        val row = rows.getValue(check.kind)
        when (check.kind) {
            CheckKind.REGISTERED -> when (check.mark) {
                CheckMark.OK -> row.set(mark, UiText.REGISTERED_OK,
                    UiText.registeredDetail(snapshot.state.deviceId.orEmpty()), showAction = true)
                CheckMark.WAIT -> row.set(mark, UiText.REGISTERED_WAIT,
                    if (snapshot.slow) UiText.REGISTERED_SLOW_DETAIL else UiText.REGISTERED_WAIT_DETAIL, false)
                CheckMark.BAD -> row.set(mark, UiText.REGISTERED_BAD, UiText.REGISTERED_BAD_DETAIL, false)
            }
            CheckKind.PUSH_TOKEN -> when (check.mark) {
                CheckMark.OK -> row.set(mark, UiText.TOKEN_OK, UiText.TOKEN_OK_DETAIL, false)
                CheckMark.WAIT -> row.set(mark, UiText.TOKEN_WAIT, UiText.TOKEN_WAIT_DETAIL, false)
                CheckMark.BAD -> row.set(mark, UiText.TOKEN_BAD, UiText.TOKEN_BAD_DETAIL, false)
            }
            CheckKind.SIGNED_IN -> when (check.mark) {
                CheckMark.OK -> row.set(mark, UiText.SIGNED_IN_OK,
                    tester.prefs.email.ifBlank { UiText.SIGNED_IN_OK_DETAIL }, false)
                CheckMark.WAIT -> row.set(mark, UiText.SIGNED_IN_WAIT, UiText.SIGNED_IN_WAIT_DETAIL, false)
                CheckMark.BAD -> row.set(mark, UiText.SIGNED_IN_BAD, UiText.SIGNED_IN_BAD_DETAIL, true)
            }
            CheckKind.NOTIFICATIONS ->
                if (check.mark == CheckMark.OK) {
                    row.set(mark, UiText.NOTIFICATIONS_OK, UiText.NOTIFICATIONS_OK_DETAIL, false)
                } else {
                    row.set(mark, UiText.NOTIFICATIONS_BAD, UiText.NOTIFICATIONS_BAD_DETAIL, true)
                }
            CheckKind.QUIET_HOURS -> {
                val window = quiet.window
                when {
                    quiet.mode == QuietMode.UNKNOWN ->
                        row.set(mark, UiText.QUIET_UNKNOWN_TITLE, UiText.QUIET_UNKNOWN_DETAIL, false)
                    quiet.mode == QuietMode.OFF || window == null ->
                        row.set(mark, UiText.QUIET_OFF_TITLE, UiText.QUIET_OFF_DETAIL, true)
                    quiet.mode == QuietMode.ON_NOW ->
                        row.set(mark, UiText.QUIET_NOW_TITLE, UiText.quietNowDetail(QuietText.end(window)), true)
                    else ->
                        row.set(mark, UiText.quietLaterTitle(QuietText.range(window)), UiText.QUIET_LATER_DETAIL, true)
                }
            }
        }
    }

    private fun renderBanner(checks: List<Check>) {
        val passed = Checklist.passed(checks)
        val total = checks.size
        val summary = Checklist.summary(checks)
        val (tone, titleText, detailText) = when (summary) {
            CheckSummary.READY -> Triple(Tone.OK, UiText.bannerReady(total), UiText.BANNER_READY_DETAIL)
            CheckSummary.ACTION -> Triple(Tone.WAIT, UiText.bannerAction(passed, total), UiText.BANNER_ACTION_DETAIL)
            CheckSummary.CHECKING ->
                Triple(Tone.WAIT, UiText.bannerChecking(passed, total), UiText.BANNER_CHECKING_DETAIL)
        }
        if (bannerTitle.text.toString() != titleText) bannerTitle.text = titleText
        if (bannerDetail.text.toString() != detailText) bannerDetail.text = detailText
        progress.set(passed, total, BvColors.strong(tone))
        if (summary == shownSummary) return
        shownSummary = summary
        val mark = when (summary) {
            CheckSummary.READY -> Mark.OK
            CheckSummary.ACTION -> Mark.BAD
            CheckSummary.CHECKING -> Mark.WAIT
        }
        ui.setMark(bannerMark, mark, BvColors.strong(tone))
        bannerTitle.setTextColor(BvColors.strong(tone))
        banner.background = ui.rounded(BvColors.soft(tone), 14, BvColors.cardBorder)
    }

    private fun copyDeviceId() {
        val id = deviceId ?: return
        tester.copy(UiText.DEVICE_ID, id)
    }
}
