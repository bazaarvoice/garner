package bv.garner.test.app

import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import com.bazaarvoice.push.BVPush
import com.bazaarvoice.push.push.QuietWindow
import java.util.TimeZone

/**
 * Account: sign in with a user id and the consumer email (both required),
 * the signed-in user and session, receive pushes (opt in / opt out), quiet
 * hours, and sign out.
 */
class AccountTab(private val tester: Tester) : Tab {
    private enum class Pending { NONE, SIGN_IN, SIGN_OUT }

    override val id = TabId.ACCOUNT
    private val ui = tester.ui
    private val prefs = tester.prefs
    private var pending = Pending.NONE
    private var pendingSince = 0L
    private var shownWait: String? = null
    private var settingSwitch = false
    private var quietWindow: QuietWindow? = null

    // Signed out.
    private val userIdInput = ui.input(UiText.HINT_USER_ID, InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS).apply {
        setText(prefs.userId)
    }
    private val emailInput = ui.input(UiText.HINT_EMAIL, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).apply {
        setText(prefs.email)
    }
    private val formError = ui.result()
    private val signInResult = ui.result()
    private val signInCard = ui.card(UiText.SIGN_IN_TITLE).apply {
        addView(ui.helper(UiText.SIGN_IN_HELP))
        addView(ui.fieldLabel(UiText.LABEL_USER_ID))
        addView(userIdInput)
        addView(ui.fieldLabel(UiText.LABEL_EMAIL))
        addView(emailInput)
        addView(formError)
        addView(ui.primaryButton(UiText.SIGN_IN_BUTTON) { signIn() })
        addView(signInResult)
        addView(ui.helper(UiText.SIGN_IN_NOTE).apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(12)
        })
    }
    private val nextCard = ui.card(UiText.NEXT_TITLE).apply { addView(ui.helper(UiText.NEXT_STEPS)) }

    // Signed in.
    private val initials = ui.badge("", BvColors.primary, 48, 18f)
    private val profileEmail = ui.text(16f, BvColors.text, bold = true)
    private val profileUser = ui.text(13f, BvColors.textSecondary)
    private val profileSince = ui.text(13f, BvColors.textSecondary)
    private val profileCard = ui.card().apply {
        addView(ui.horizontal().apply {
            addView(initials)
            addView(ui.vertical().apply {
                layoutParams = LinearLayout.LayoutParams(0, BvViews.WRAP, 1f).apply { marginStart = ui.dp(14) }
                addView(profileEmail)
                addView(profileUser)
                addView(profileSince)
            })
        })
    }
    private val identityRow = ui.KeyValueRow(UiText.ROW_IDENTITY)
    private val untilRow = ui.KeyValueRow(UiText.ROW_SESSION_UNTIL)
    private val sinceRow = ui.KeyValueRow(UiText.ROW_SIGNED_IN_AT)
    private val sessionCard = ui.card(UiText.SESSION_TITLE).apply {
        addView(identityRow.view)
        addView(untilRow.view)
        addView(sinceRow.view)
    }
    private val signOutResult = ui.result()
    private val signOutCard = ui.card().apply {
        addView(ui.helper(UiText.SIGN_OUT_HELP))
        addView(ui.dangerButton(UiText.SIGN_OUT) { signOut() })
        addView(signOutResult)
    }

    // Preferences (signed in or out).
    private val pushSwitch = Switch(tester.activity).apply {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(states, intArrayOf(BvColors.primary, BvColors.card))
        trackTintList = ColorStateList(states, intArrayOf(BvColors.primary, BvColors.disabled))
        setOnCheckedChangeListener { _, checked -> if (!settingSwitch) setReceivePushes(checked) }
    }
    private val serverLine = ui.helper("")
    private val quietSummary = ui.text(14f, BvColors.text)
    private val quietResult = ui.result()
    private val prefsCard = ui.card(UiText.PREFS_TITLE).apply {
        addView(ui.horizontal().apply {
            layoutParams = LinearLayout.LayoutParams(BvViews.MATCH, BvViews.WRAP).apply { topMargin = ui.dp(10) }
            addView(ui.vertical().apply {
                layoutParams = LinearLayout.LayoutParams(0, BvViews.WRAP, 1f)
                addView(ui.text(15f, BvColors.text, UiText.RECEIVE_PUSHES, bold = true))
                addView(ui.helper(UiText.RECEIVE_PUSHES_HELP))
                addView(serverLine)
            })
            addView(pushSwitch)
        })
        addView(ui.divider())
        addView(ui.text(15f, BvColors.text, UiText.QUIET_TITLE, bold = true))
        addView(quietSummary.apply {
            layoutParams = LinearLayout.LayoutParams(BvViews.MATCH, BvViews.WRAP).apply { topMargin = ui.dp(4) }
        })
        addView(ui.helper(UiText.QUIET_HELP))
        addView(ui.horizontal().apply {
            layoutParams = LinearLayout.LayoutParams(BvViews.MATCH, BvViews.WRAP).apply { topMargin = ui.dp(4) }
            addView(ui.smallButton(UiText.QUIET_OFF, filled = false) { quietOff() }.apply {
                (layoutParams as LinearLayout.LayoutParams).marginStart = 0
            })
            addView(ui.smallButton(UiText.QUIET_DEFAULT, filled = false) { quietDefault() })
            addView(ui.smallButton(UiText.QUIET_SET, filled = false) { pickQuietHours() })
        })
        addView(quietResult)
    }

    override val view: View = tester.scroll(ui.vertical().apply {
        addView(signInCard)
        addView(profileCard)
        addView(sessionCard)
        addView(prefsCard)
        addView(signOutCard)
        addView(nextCard)
    })

    override fun title(snapshot: Snapshot): String = UiText.TAB_ACCOUNT

    override fun chips(snapshot: Snapshot): List<ToneText> = listOf(
        when {
            pending == Pending.SIGN_IN -> ToneText(UiText.SIGNING_IN, Tone.WAIT)
            pending == Pending.SIGN_OUT -> ToneText(UiText.SIGNING_OUT, Tone.WAIT)
            snapshot.signedIn -> ToneText(UiText.SIGNED_IN, Tone.OK)
            else -> ToneText(UiText.SIGNED_OUT, Tone.BAD)
        }
    )

    override fun render(snapshot: Snapshot) {
        val session = snapshot.signedIn
        resolvePending(session, snapshot.nowMillis)
        if (pending == Pending.NONE && !session && snapshot.diag != null && prefs.signedInAt != 0L) {
            prefs.markSignedOut() // session ended outside this screen (driver, expiry)
        }

        visible(signInCard, !session)
        visible(nextCard, !session)
        visible(profileCard, session)
        visible(sessionCard, session)
        visible(signOutCard, session)
        if (session) renderSignedIn(snapshot)
        renderPreferences(snapshot)
    }

    private fun renderSignedIn(snapshot: Snapshot) {
        val zone = TimeZone.getDefault()
        val email = prefs.email
        val user = prefs.userId
        val since = prefs.signedInAt.takeIf { it > 0 }
        setText(initials, AppInfo.initials(email, user))
        setText(profileEmail, email.ifBlank { UiText.SIGNED_IN_USER_UNKNOWN })
        setText(profileUser, if (user.isBlank()) "" else UiText.userIdLine(user))
        profileUser.visibility = if (user.isBlank()) View.GONE else View.VISIBLE
        setText(profileSince, since?.let { UiText.signedInLine(TimeText.clock(it, zone)) } ?: UiText.SIGNED_IN_LINE_NO_TIME)
        identityRow.set(snapshot.diag?.identityPhase ?: UiText.UNKNOWN)
        untilRow.set(TimeText.epochMillis(snapshot.diag?.sessionExpiry)?.let { TimeText.dateTime(it, zone) } ?: UiText.NONE)
        sinceRow.set(since?.let { TimeText.dateTime(it, zone) } ?: UiText.NONE)
    }

    private fun renderPreferences(snapshot: Snapshot) {
        val diag = snapshot.diag
        pushSwitch.isEnabled = diag != null
        val receiving = diag?.desiredOptedOut != true
        if (pushSwitch.isChecked != receiving) {
            settingSwitch = true
            pushSwitch.isChecked = receiving
            settingSwitch = false
        }
        setText(serverLine, UiText.serverOptLine(diag?.serverOptedOut))
        val quiet = snapshot.quiet
        quietWindow = quiet.window
        setText(quietSummary, QuietText.summary(quiet))
    }

    private fun signIn() {
        val problems = SignInForm.problems(userIdInput.text.toString(), emailInput.text.toString())
        if (problems.isNotEmpty()) {
            ui.show(formError, Tone.BAD, problems.joinToString(separator = "\n") { it.message })
            return
        }
        formError.visibility = View.GONE
        signOutResult.visibility = View.GONE
        val user = SignInForm.clean(userIdInput.text.toString())
        val address = SignInForm.clean(emailInput.text.toString())
        prefs.saveIdentity(user, address)
        tester.hideKeyboard(view)
        ActivityLog.clearLoginWait()
        runCatching { BVPush.linkIdentity(user, address) }
            .onSuccess {
                startPending(Pending.SIGN_IN)
                val later = if (BVPush.state.value.initialized) "" else UiText.SIGNING_IN_LATER
                ui.show(signInResult, Tone.WAIT, UiText.signingIn(address) + later)
            }
            .onFailure { ui.show(signInResult, Tone.BAD, UiText.signInFailed(it.message)) }
        tester.refresh()
    }

    private fun signOut() {
        BVPush.unlinkIdentity(prefs.userId)
        startPending(Pending.SIGN_OUT)
        signInResult.visibility = View.GONE
        ui.show(signOutResult, Tone.WAIT, UiText.SIGNING_OUT)
        tester.refresh()
    }

    private fun resolvePending(session: Boolean, nowMillis: Long) {
        val waited = SystemClock.elapsedRealtime() - pendingSince
        when (pending) {
            Pending.NONE -> Unit
            Pending.SIGN_IN -> {
                val wait = ActivityLog.loginWait.value
                when {
                    session -> {
                        prefs.markSignedIn(nowMillis)
                        signInResult.visibility = View.GONE
                        pending = Pending.NONE
                    }
                    // The SDK said which step it waits for and signs in by itself: no timeout.
                    wait != null -> showWait(EventText.loginWait(wait))
                    waited > PENDING_TIMEOUT_MS -> {
                        ui.show(signInResult, Tone.BAD, UiText.SIGN_IN_TIMEOUT)
                        pending = Pending.NONE
                    }
                }
            }
            Pending.SIGN_OUT -> when {
                !session -> {
                    prefs.markSignedOut()
                    signOutResult.visibility = View.GONE
                    ui.show(signInResult, Tone.OK, UiText.SIGN_OUT_DONE)
                    pending = Pending.NONE
                }
                waited > PENDING_TIMEOUT_MS -> {
                    ui.show(signOutResult, Tone.BAD, UiText.SIGN_OUT_TIMEOUT)
                    pending = Pending.NONE
                }
            }
        }
    }

    private fun startPending(kind: Pending) {
        pending = kind
        pendingSince = SystemClock.elapsedRealtime()
        shownWait = null
    }

    private fun showWait(message: String) {
        if (message == shownWait) return
        shownWait = message
        ui.show(signInResult, Tone.WAIT, message)
    }

    private fun setReceivePushes(receive: Boolean) {
        if (receive) BVPush.optIn() else BVPush.optOut()
        tester.refresh()
    }

    private fun quietOff() = applyQuiet(expected = null) { BVPush.disableQuietHours() }

    private fun quietDefault() = applyQuiet(expected = QuietWindow.DEFAULT) { BVPush.resetQuietHours() }

    private fun pickQuietHours() {
        val current = quietWindow ?: QuietWindow.DEFAULT
        TimePickerDialog(tester.activity, { _, startHour, startMinute ->
            TimePickerDialog(tester.activity, { _, endHour, endMinute ->
                applyQuiet(QuietWindow(startHour, startMinute, endHour, endMinute)) {
                    BVPush.setQuietHours(startHour, startMinute, endHour, endMinute)
                }
            }, current.endHour, current.endMinute, true).apply { setTitle(UiText.QUIET_END_TITLE) }.show()
        }, current.startHour, current.startMinute, true).apply { setTitle(UiText.QUIET_START_TITLE) }.show()
    }

    /** The SDK ignores quiet hours calls until its stores exist; read the window back to be sure. */
    private fun applyQuiet(expected: QuietWindow?, change: () -> Unit) {
        val failure = runCatching(change).exceptionOrNull()
        val diag = runCatching { BVPush.diagnostics() }.getOrNull()
        when {
            failure != null -> ui.show(quietResult, Tone.BAD, failure.message ?: UiText.QUIET_NOT_READY)
            diag != null && diag.quietWindow == expected?.toString() -> ui.show(quietResult, Tone.OK, UiText.QUIET_SAVED)
            else -> ui.show(quietResult, Tone.BAD, UiText.QUIET_NOT_READY)
        }
        tester.refresh()
    }

    private fun visible(view: View, show: Boolean) {
        val next = if (show) View.VISIBLE else View.GONE
        if (view.visibility != next) view.visibility = next
    }

    private fun setText(view: android.widget.TextView, text: String) {
        if (view.text.toString() != text) view.text = text
    }

    private companion object {
        const val PENDING_TIMEOUT_MS = 30_000L
    }
}
