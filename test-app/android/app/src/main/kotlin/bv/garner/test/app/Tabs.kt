package bv.garner.test.app

import android.view.View
import androidx.annotation.DrawableRes
import com.bazaarvoice.push.BVPush
import com.bazaarvoice.push.events.Diagnostics

/** The four tabs of the bottom navigation bar, in order, each with its outlined Material icon. */
enum class TabId(val label: String, @DrawableRes val icon: Int) {
    HOME(UiText.TAB_HOME, R.drawable.ic_tab_home),
    ACCOUNT(UiText.TAB_ACCOUNT, R.drawable.ic_tab_account),
    INBOX(UiText.TAB_INBOX, R.drawable.ic_tab_inbox),
    DETAILS(UiText.TAB_DETAILS, R.drawable.ic_tab_details),
    ;

    companion object {
        /** The tab with this enum name (saved state, `--es tab INBOX`), or null. */
        fun fromName(name: String?): TabId? = entries.firstOrNull { it.name == name }
    }
}

/** One read of everything the screens show; built by MainActivity on every refresh. */
data class Snapshot(
    val state: BVPush.PublicState,
    /** Null until the SDK answers diagnostics(). */
    val diag: Diagnostics?,
    val notificationsAllowed: Boolean,
    val fcmToken: String?,
    /** Registration has taken longer than expected since the screen opened. */
    val slow: Boolean,
    val nowMillis: Long,
    /** Device wall-clock minutes after midnight, for quiet hours. */
    val nowMinutes: Int,
) {
    val signedIn: Boolean get() = diag?.hasSession == true
    val quiet: QuietStatus get() = QuietText.status(diag != null, diag?.quietWindow, nowMinutes)
    val appId: Int get() = diag?.appId ?: BuildConfig.BV_APP_ID
}

/** A tab: its content view, its top bar title and chips, and a cheap repaint. */
interface Tab {
    val id: TabId
    val view: View
    fun title(snapshot: Snapshot): String
    fun chips(snapshot: Snapshot): List<ToneText>

    /** Called on every refresh; only changed values touch the views. */
    fun render(snapshot: Snapshot)
}
