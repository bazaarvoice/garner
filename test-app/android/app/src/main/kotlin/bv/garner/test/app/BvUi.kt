package bv.garner.test.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import kotlin.math.roundToInt

/** Every colour of the app, in one place: Bazaarvoice blue on white. */
object BvColors {
    val primary = Color.parseColor("#0B5FAE")        // buttons, links, selected tab
    val topBar = Color.parseColor("#06355F")         // top bar (also behind the status bar)
    val onPrimary = Color.WHITE
    val topBarSubtitle = Color.parseColor("#C5D9F0")
    val background = Color.parseColor("#F6F8FB")
    val card = Color.WHITE
    val cardBorder = Color.parseColor("#E1E6ED")
    val inputBorder = Color.parseColor("#C5CEDA")
    val text = Color.parseColor("#1B2430")
    val textSecondary = Color.parseColor("#5A6676")
    val ok = Color.parseColor("#1E7F3C")
    val okSoft = Color.parseColor("#E3F3E8")
    val bad = Color.parseColor("#C62828")
    val badSoft = Color.parseColor("#FDE7E7")
    val waiting = Color.parseColor("#9A5B00")
    val waitingSoft = Color.parseColor("#FFF1DB")
    val neutralSoft = Color.parseColor("#EAEFF5")
    val selectedSoft = Color.parseColor("#DCE9F7")
    val navBar = Color.WHITE
    val disabled = Color.parseColor("#B8C4D2")
    val ripplePrimary = Color.parseColor("#33FFFFFF")
    val rippleOutlined = Color.parseColor("#220B5FAE")
    val code = Color.parseColor("#F3F5F8")

    fun strong(tone: Tone): Int = when (tone) {
        Tone.NORMAL -> text
        Tone.OK -> ok
        Tone.BAD -> bad
        Tone.WAIT -> waiting
    }

    fun soft(tone: Tone): Int = when (tone) {
        Tone.NORMAL -> neutralSoft
        Tone.OK -> okSoft
        Tone.BAD -> badSoft
        Tone.WAIT -> waitingSoft
    }

    /** Top bar chip fill on the dark bar. */
    fun onBar(tone: Tone): Int = when (tone) {
        Tone.NORMAL -> primary
        Tone.OK -> ok
        Tone.BAD -> bad
        Tone.WAIT -> waiting
    }
}

/** Marker of a checklist row: a check, an exclamation mark or three dots (Material icons). */
enum class Mark(@DrawableRes val icon: Int, val tone: Tone) {
    OK(R.drawable.ic_mark_ok, Tone.OK),
    BAD(R.drawable.ic_mark_bad, Tone.BAD),
    WAIT(R.drawable.ic_mark_wait, Tone.WAIT),
    ;

    companion object {
        fun of(mark: CheckMark): Mark = when (mark) {
            CheckMark.OK -> OK
            CheckMark.BAD -> BAD
            CheckMark.WAIT -> WAIT
        }
    }
}

/** Small factory for the plain Android Views the screens are built from. */
class BvViews(private val context: Context) {

    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    fun text(sizeSp: Float, color: Int, value: CharSequence = "", bold: Boolean = false) =
        TextView(context).apply {
            textSize = sizeSp
            setTextColor(color)
            text = value
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun mono(sizeSp: Float, color: Int, value: CharSequence = "") = text(sizeSp, color, value).apply {
        typeface = Typeface.MONOSPACE
    }

    fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null, strokeDp: Int = 1) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(dp(strokeDp), stroke)
    }

    fun vertical(): LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun horizontal(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun card(title: String? = null): LinearLayout = vertical().apply {
        background = rounded(BvColors.card, 14, BvColors.cardBorder)
        setPadding(dp(16), dp(14), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
        if (title != null) addView(text(17f, BvColors.text, title, bold = true))
    }

    fun helper(value: CharSequence) = text(13f, BvColors.textSecondary, value).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) }
    }

    fun sectionLabel(value: String) = text(12f, BvColors.textSecondary, value, bold = true).apply {
        letterSpacing = 0.06f
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(14) }
    }

    fun fieldLabel(label: String) = text(13f, BvColors.text, label, bold = true).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
    }

    fun divider() = View(context).apply {
        setBackgroundColor(BvColors.cardBorder)
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply {
            topMargin = dp(10)
            bottomMargin = dp(10)
        }
    }

    /** Inline feedback line under a button; hidden until it has something to say. */
    fun result() = text(14f, BvColors.textSecondary).apply {
        visibility = View.GONE
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
    }

    /** Shows [message] in the tone's colour; no-op when already shown. */
    fun show(view: TextView, tone: Tone, message: String) {
        if (view.visibility == View.VISIBLE && view.text.toString() == message) return
        view.text = message
        view.setTextColor(BvColors.strong(tone).takeIf { tone != Tone.NORMAL } ?: BvColors.textSecondary)
        view.visibility = View.VISIBLE
    }

    fun input(hint: String, type: Int) = EditText(context).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or type
        setSingleLine()
        textSize = 15f
        setTextColor(BvColors.text)
        setHintTextColor(BvColors.textSecondary)
        background = rounded(BvColors.card, 8, BvColors.inputBorder)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) }
    }

    fun primaryButton(label: String, onClick: () -> Unit) =
        button(label, BvColors.primary, filled = true, small = false, onClick)

    fun outlinedButton(label: String, onClick: () -> Unit) =
        button(label, BvColors.primary, filled = false, small = false, onClick)

    fun dangerButton(label: String, onClick: () -> Unit) =
        button(label, BvColors.bad, filled = false, small = false, onClick)

    /** Compact button at the end of a row. */
    fun smallButton(label: String, filled: Boolean, onClick: () -> Unit) =
        button(label, BvColors.primary, filled, small = true, onClick).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) }
        }

    private fun button(label: String, color: Int, filled: Boolean, small: Boolean, onClick: () -> Unit) =
        Button(context).apply {
            text = label
            isAllCaps = false
            textSize = if (small) 13f else 15f
            setTypeface(typeface, Typeface.BOLD)
            stateListAnimator = null
            val height = dp(if (small) 34 else 48)
            minHeight = height
            minimumHeight = height
            minWidth = 0
            minimumWidth = 0
            if (small) setPadding(dp(12), 0, dp(12), 0) else setPadding(dp(16), dp(10), dp(16), dp(10))
            val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
            val accent = ColorStateList(states, intArrayOf(BvColors.disabled, color))
            val shape = GradientDrawable().apply {
                cornerRadius = dp(if (small) 8 else 10).toFloat()
                if (filled) {
                    setColor(accent)
                } else {
                    setColor(BvColors.card)
                    setStroke(dp(if (small) 1 else 2), accent)
                }
            }
            val ripple = if (filled) BvColors.ripplePrimary else BvColors.rippleOutlined
            background = RippleDrawable(ColorStateList.valueOf(ripple), shape, null)
            setTextColor(if (filled) ColorStateList.valueOf(BvColors.onPrimary) else accent)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
            setOnClickListener { onClick() }
        }

    /** Small rounded label: soft fill with strong text, or a strong fill on the top bar. */
    fun chip(value: String, tone: Tone, onBar: Boolean = false) =
        text(12f, if (onBar) BvColors.onPrimary else BvColors.strong(tone), value, bold = true).apply {
            background = rounded(if (onBar) BvColors.onBar(tone) else BvColors.soft(tone), 12)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(6) }
        }

    /** Selectable filter chip (Inbox). */
    fun filterChip(value: String, selected: Boolean, color: Int, onClick: () -> Unit) =
        text(13f, if (selected) BvColors.onPrimary else color, value, bold = selected).apply {
            background = if (selected) rounded(color, 16) else rounded(BvColors.card, 16, BvColors.inputBorder)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(6) }
            setOnClickListener { onClick() }
        }

    /** A Material icon from res/drawable, tinted [color]. Decorative: TalkBack reads the label next to it. */
    fun icon(@DrawableRes res: Int, color: Int) = ImageView(context).apply {
        setImageResource(res)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageTintList = ColorStateList.valueOf(color)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Coloured circle with the white icon of [mark] (checklist rows, Home banner). */
    fun markBadge(mark: Mark, sizeDp: Int) = icon(mark.icon, BvColors.onPrimary).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(BvColors.strong(mark.tone))
        }
        val inset = dp(sizeDp) / 5
        setPadding(inset, inset, inset, inset)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    /** Shows [mark] on a [markBadge], filled with [fill]. */
    fun setMark(badge: ImageView, mark: Mark, fill: Int = BvColors.strong(mark.tone)) {
        badge.setImageResource(mark.icon)
        (badge.background as GradientDrawable).setColor(fill)
    }

    /** Coloured circle with initials. */
    fun badge(value: String, fill: Int, sizeDp: Int, textSp: Float) =
        text(textSp, BvColors.onPrimary, value, bold = true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(fill)
            }
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }

    /** One checklist line: mark, title and detail, then an optional action button. */
    inner class CheckRow(actionLabel: String? = null, filledAction: Boolean = false, onAction: () -> Unit = {}) {
        private val mark = markBadge(Mark.WAIT, 26)
        private val title = text(15f, BvColors.text, bold = true)
        private val detail = text(13f, BvColors.textSecondary).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        private val action = actionLabel?.let { smallButton(it, filledAction, onAction) }
        private var shown: List<Any>? = null

        val view = horizontal().apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
            addView(mark)
            addView(vertical().apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(12) }
                addView(title)
                addView(detail)
            })
            if (action != null) addView(action)
        }

        fun set(value: Mark, titleText: String, detailText: String, showAction: Boolean) {
            val next = listOf(value, titleText, detailText, showAction)
            if (next == shown) return
            shown = next
            setMark(mark, value)
            title.text = titleText
            detail.text = detailText
            action?.visibility = if (showAction) View.VISIBLE else View.GONE
        }
    }

    /** Label on the left, value on the right (monospace), optional Copy. */
    inner class KeyValueRow(label: String, onCopy: (() -> Unit)? = null) {
        private val value = mono(13f, BvColors.text).apply {
            gravity = Gravity.END
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(12) }
        }

        val view = horizontal().apply {
            minimumHeight = dp(36)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2) }
            addView(text(13f, BvColors.textSecondary, label))
            addView(value)
            if (onCopy != null) {
                addView(text(13f, BvColors.primary, UiText.COPY, bold = true).apply {
                    setPadding(dp(12), dp(8), 0, dp(8))
                    setOnClickListener { onCopy() }
                })
            }
        }

        fun set(text: String) {
            if (value.text.toString() != text) value.text = text
        }
    }

    /** Thin progress bar: [done] of [total] filled in [color]. */
    inner class Progress {
        private val fill = View(context)
        private val rest = View(context)

        val view = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(BvColors.neutralSoft, 3)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(6)).apply { topMargin = dp(10) }
            addView(fill)
            addView(rest)
        }

        private var shown: Triple<Int, Int, Int>? = null

        fun set(done: Int, total: Int, color: Int) {
            val next = Triple(done, total, color)
            if (next == shown) return
            shown = next
            fill.background = rounded(color, 3)
            fill.layoutParams = LinearLayout.LayoutParams(0, MATCH, done.toFloat())
            rest.layoutParams = LinearLayout.LayoutParams(0, MATCH, (total - done).coerceAtLeast(0).toFloat())
        }
    }

    companion object {
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
