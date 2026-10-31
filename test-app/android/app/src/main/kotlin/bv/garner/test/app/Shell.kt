package bv.garner.test.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** What every tab may ask of the app: navigation, copy, toasts, settings. */
class Tester(
    val activity: ComponentActivity,
    val navigate: (TabId) -> Unit,
    /** Repaint now, for example right after a button changed SDK state. */
    val refresh: () -> Unit,
) {
    val ui = BvViews(activity)
    val prefs = TesterPrefs(activity)
    val appName: String = activity.applicationInfo.loadLabel(activity.packageManager).toString()

    fun copy(label: String, value: String) {
        val clipboard = activity.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        toast(UiText.copied(label))
    }

    fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    fun openNotificationSettings() {
        val pkg = activity.packageName
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts(SCHEME_PACKAGE, pkg, null))
        val candidates = if (Build.VERSION.SDK_INT >= 26) {
            listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg), details)
        } else {
            listOf(details)
        }
        val opened = candidates.any { runCatching { activity.startActivity(it) }.isSuccess }
        toast(if (opened) UiText.SETTINGS_OPENED else UiText.SETTINGS_MANUAL)
    }

    fun hideKeyboard(view: View) {
        activity.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /** Scrolling tab body with the standard padding. */
    fun scroll(content: LinearLayout): ScrollView = ScrollView(activity).apply {
        isFillViewport = true
        setBackgroundColor(BvColors.background)
        addView(content.apply { setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(24)) })
    }

    private companion object {
        const val SCHEME_PACKAGE = "package"
    }
}

/**
 * Top bar (title and chips), the selected tab's view, and the bottom
 * navigation bar with the Inbox badge. Edge-to-edge: the top bar runs under
 * the status bar; the navigation bar sits above the system navigation.
 */
class Shell(tester: Tester, private val tabs: List<Tab>, onSelect: (TabId) -> Unit) {
    private val ui = tester.ui
    private val context = tester.activity
    private var shownChips: List<ToneText>? = null

    private val title = ui.text(22f, BvColors.onPrimary, bold = true)
    private val chipRow = ui.horizontal().apply {
        layoutParams = LinearLayout.LayoutParams(BvViews.MATCH, BvViews.WRAP).apply { topMargin = ui.dp(6) }
    }
    private val topBar = ui.vertical().apply {
        setBackgroundColor(BvColors.topBar)
        addView(title)
        addView(chipRow)
    }
    private val content = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(BvViews.MATCH, 0, 1f)
        tabs.forEach { tab ->
            addView(tab.view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }
    private val items = TabId.entries.associateWith { NavItem(it, onSelect) }
    private val navBar = ui.horizontal().apply {
        setBackgroundColor(BvColors.navBar)
        elevation = ui.dp(8).toFloat()
        TabId.entries.forEach { addView(items.getValue(it).view) }
    }

    val root: LinearLayout = ui.vertical().apply {
        setBackgroundColor(BvColors.background)
        addView(topBar)
        addView(content)
        addView(navBar)
    }

    init {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val typing = ime.bottom > bars.bottom
            topBar.setPadding(ui.dp(16) + bars.left, ui.dp(12) + bars.top, ui.dp(16) + bars.right, ui.dp(14))
            content.setPadding(bars.left, 0, bars.right, 0)
            navBar.setPadding(bars.left, ui.dp(6), bars.right, ui.dp(6) + bars.bottom)
            // While typing the keyboard covers the navigation bar: hide it and stop above the keyboard.
            navBar.visibility = if (typing) View.GONE else View.VISIBLE
            root.setPadding(0, 0, 0, if (typing) ime.bottom else 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    fun select(id: TabId) {
        tabs.forEach { it.view.visibility = if (it.id == id) View.VISIBLE else View.GONE }
        items.forEach { (tabId, item) -> item.setSelected(tabId == id) }
        shownChips = null
    }

    /** Top bar for the selected tab. */
    fun update(tab: Tab, snapshot: Snapshot) {
        val text = tab.title(snapshot)
        if (title.text.toString() != text) title.text = text
        val chips = tab.chips(snapshot)
        if (chips == shownChips) return
        shownChips = chips
        chipRow.removeAllViews()
        chips.forEach { chipRow.addView(ui.chip(it.text, it.tone, onBar = true)) }
    }

    fun setBadge(id: TabId, count: Int) = items.getValue(id).setBadge(count)

    private inner class NavItem(id: TabId, onSelect: (TabId) -> Unit) {
        private val icon = ui.icon(id.icon, BvColors.textSecondary).apply {
            layoutParams = LinearLayout.LayoutParams(ui.dp(56), ui.dp(30))
        }
        private val label = ui.text(12f, BvColors.textSecondary, id.label)
        private val badge = ui.text(10f, BvColors.onPrimary, bold = true).apply {
            gravity = Gravity.CENTER
            minWidth = ui.dp(16)
            background = ui.rounded(BvColors.bad, 8)
            setPadding(ui.dp(4), 0, ui.dp(4), 0)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(BvViews.WRAP, ui.dp(16)).apply { marginStart = ui.dp(4) }
        }

        val view = ui.vertical().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, BvViews.WRAP, 1f)
            addView(icon)
            addView(ui.horizontal().apply {
                gravity = Gravity.CENTER
                addView(label)
                addView(badge)
            })
            setOnClickListener { onSelect(id) }
        }

        fun setSelected(selected: Boolean) {
            val color = if (selected) BvColors.primary else BvColors.textSecondary
            icon.imageTintList = ColorStateList.valueOf(color)
            icon.background = if (selected) ui.rounded(BvColors.selectedSoft, 15) else null
            label.setTextColor(color)
            label.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }

        fun setBadge(count: Int) {
            val text = if (count > BADGE_LIMIT) UiText.BADGE_MAX else count.toString()
            badge.visibility = if (count > 0) View.VISIBLE else View.GONE
            if (badge.text.toString() != text) badge.text = text
        }
    }

    private companion object {
        const val BADGE_LIMIT = 99
    }
}

