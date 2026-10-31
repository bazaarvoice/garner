import SwiftUI

/// The Android test app palette, so both apps look the same in screenshots.
enum BvColors {
    static let primary = Color(hex: 0x0B5FAE) // buttons, links, selected tab
    static let topBar = Color(hex: 0x06355F) // top bar (also behind the status bar)
    static let onPrimary = Color.white
    static let topBarSubtitle = Color(hex: 0xC5D9F0)
    static let background = Color(hex: 0xF6F8FB)
    static let card = Color.white
    static let cardBorder = Color(hex: 0xE1E6ED)
    static let inputBorder = Color(hex: 0xC5CEDA)
    static let text = Color(hex: 0x1B2430)
    static let textSecondary = Color(hex: 0x5A6676)
    static let ok = Color(hex: 0x1E7F3C)
    static let okSoft = Color(hex: 0xE3F3E8)
    static let bad = Color(hex: 0xC62828)
    static let badSoft = Color(hex: 0xFDE7E7)
    static let waiting = Color(hex: 0x9A5B00)
    static let waitingSoft = Color(hex: 0xFFF1DB)
    static let neutralSoft = Color(hex: 0xEAEFF5)
    static let selectedSoft = Color(hex: 0xDCE9F7)
    static let navBar = Color.white
    static let disabled = Color(hex: 0xB8C4D2)
    static let code = Color(hex: 0xF3F5F8)

    static func strong(_ tone: Tone) -> Color {
        switch tone {
        case .normal: return text
        case .ok: return ok
        case .bad: return bad
        case .wait: return waiting
        }
    }

    static func soft(_ tone: Tone) -> Color {
        switch tone {
        case .normal: return neutralSoft
        case .ok: return okSoft
        case .bad: return badSoft
        case .wait: return waitingSoft
        }
    }

    /// Top bar chip fill on the dark bar.
    static func onBar(_ tone: Tone) -> Color {
        switch tone {
        case .normal: return primary
        case .ok: return ok
        case .bad: return bad
        case .wait: return waiting
        }
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

/// ✓ / ! / … marker of a checklist row.
enum Mark {
    case ok, bad, wait

    var symbol: String {
        switch self {
        case .ok: return "✓"
        case .bad: return "!"
        case .wait: return "…"
        }
    }

    var tone: Tone {
        switch self {
        case .ok: return .ok
        case .bad: return .bad
        case .wait: return .wait
        }
    }

    static func of(_ mark: CheckMark) -> Mark {
        switch mark {
        case .ok: return .ok
        case .bad: return .bad
        case .wait: return .wait
        }
    }
}

/// White rounded card with a border and an optional bold title.
struct Card<Content: View>: View {
    var title: String?
    var fill: Color = BvColors.card
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let title {
                Text(title).font(.system(size: 17, weight: .bold)).foregroundColor(BvColors.text)
            }
            content
        }
        .padding(EdgeInsets(top: 14, leading: 16, bottom: 16, trailing: 16))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).fill(fill))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(BvColors.cardBorder, lineWidth: 1))
        .padding(.top, 12)
    }
}

struct Helper: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text)
            .font(.system(size: 13))
            .foregroundColor(BvColors.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 4)
    }
}

struct SectionLabel: View {
    let text: String
    var first = false

    var body: some View {
        Text(text)
            .font(.system(size: 12, weight: .bold))
            .tracking(0.6)
            .foregroundColor(BvColors.textSecondary)
            .padding(.top, first ? 0 : 14)
    }
}

struct FieldLabel: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text).font(.system(size: 13, weight: .bold)).foregroundColor(BvColors.text).padding(.top, 12)
    }
}

struct BvDivider: View {
    var body: some View {
        Rectangle().fill(BvColors.cardBorder).frame(height: 1).padding(.vertical, 10)
    }
}

/// A one-off result line under a button, coloured by tone.
struct ResultLine: Equatable {
    let tone: Tone
    let message: String
}

struct ResultText: View {
    let result: ResultLine?

    var body: some View {
        if let result {
            Text(result.message)
                .font(.system(size: 14))
                .foregroundColor(BvColors.strong(result.tone))
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 8)
        }
    }
}

struct BvInput: View {
    let hint: String
    @Binding var text: String
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType?

    var body: some View {
        TextField(hint, text: $text)
            .keyboardType(keyboard)
            .textContentType(contentType)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .font(.system(size: 15))
            .foregroundColor(BvColors.text)
            .padding(EdgeInsets(top: 10, leading: 12, bottom: 10, trailing: 12))
            .background(RoundedRectangle(cornerRadius: 8).fill(BvColors.card))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(BvColors.inputBorder, lineWidth: 1))
            .padding(.top, 4)
    }
}

enum BvButtonKind { case primary, outlined, danger }

/// Full-width 48 pt button (filled primary, outlined, or danger outline).
struct BvButton: View {
    let label: String
    var kind: BvButtonKind = .primary
    var enabled = true
    let action: () -> Void

    var body: some View {
        let accent = enabled ? (kind == .danger ? BvColors.bad : BvColors.primary) : BvColors.disabled
        Button(action: action) {
            Text(label)
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(kind == .primary ? BvColors.onPrimary : accent)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(
                    RoundedRectangle(cornerRadius: 10).fill(kind == .primary ? accent : BvColors.card)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 10).stroke(accent, lineWidth: kind == .primary ? 0 : 2)
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .padding(.top, 12)
    }
}

/// 34 pt button next to a checklist row or in a button row.
struct SmallButton: View {
    let label: String
    var filled = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(filled ? BvColors.onPrimary : BvColors.primary)
                .padding(.horizontal, 12)
                .frame(minHeight: 34)
                .background(RoundedRectangle(cornerRadius: 8).fill(filled ? BvColors.primary : BvColors.card))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(BvColors.primary, lineWidth: filled ? 0 : 1))
        }
        .buttonStyle(.plain)
    }
}

/// Small rounded label: soft fill with strong text, or a strong fill on the top bar.
struct Chip: View {
    let text: String
    let tone: Tone
    var onBar = false

    var body: some View {
        Text(text)
            .font(.system(size: 12, weight: .bold))
            .lineLimit(1)
            .truncationMode(.tail)
            .foregroundColor(onBar ? BvColors.onPrimary : BvColors.strong(tone))
            .padding(EdgeInsets(top: 3, leading: 8, bottom: 3, trailing: 8))
            .background(RoundedRectangle(cornerRadius: 12).fill(onBar ? BvColors.onBar(tone) : BvColors.soft(tone)))
    }
}

/// Selectable filter chip (Inbox).
struct FilterChip: View {
    let text: String
    let selected: Bool
    var color: Color = BvColors.primary
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.system(size: 13, weight: selected ? .bold : .regular))
                .foregroundColor(selected ? BvColors.onPrimary : color)
                .padding(EdgeInsets(top: 6, leading: 12, bottom: 6, trailing: 12))
                .background(RoundedRectangle(cornerRadius: 16).fill(selected ? color : BvColors.card))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(selected ? color : BvColors.inputBorder, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// Coloured circle with a symbol or initials.
struct Badge: View {
    let text: String
    let fill: Color
    var size: CGFloat = 26
    var textSize: CGFloat = 14

    var body: some View {
        Text(text)
            .font(.system(size: textSize, weight: .bold))
            .foregroundColor(BvColors.onPrimary)
            .frame(width: size, height: size)
            .background(Circle().fill(fill))
    }
}

/// One checklist line: mark, title and detail, then an optional action button.
struct CheckRow: View {
    let mark: Mark
    let title: String
    let detail: String
    var actionLabel: String?
    var filledAction = false
    var showAction = false
    var action: () -> Void = {}

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            Badge(text: mark.symbol, fill: BvColors.strong(mark.tone))
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(.system(size: 15, weight: .bold)).foregroundColor(BvColors.text)
                Text(detail).font(.system(size: 13)).foregroundColor(BvColors.textSecondary).lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let actionLabel, showAction {
                SmallButton(label: actionLabel, filled: filledAction, action: action)
            }
        }
        .padding(.top, 12)
    }
}

/// Label on the left, value on the right (monospace), optional Copy.
struct KeyValueRow: View {
    let label: String
    let value: String
    var onCopy: (() -> Void)?

    var body: some View {
        HStack(spacing: 12) {
            Text(label).font(.system(size: 13)).foregroundColor(BvColors.textSecondary)
            Text(value)
                .font(.system(size: 13, design: .monospaced))
                .foregroundColor(BvColors.text)
                .lineLimit(1)
                .truncationMode(.middle)
                .frame(maxWidth: .infinity, alignment: .trailing)
            if let onCopy {
                Button(action: onCopy) {
                    Text(UiText.COPY).font(.system(size: 13, weight: .bold)).foregroundColor(BvColors.primary)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(minHeight: 36)
        .padding(.top, 2)
    }
}

/// Thin progress bar: `done` of `total` filled in `color`.
struct ProgressLine: View {
    let done: Int
    let total: Int
    let color: Color

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 3).fill(BvColors.neutralSoft)
                RoundedRectangle(cornerRadius: 3).fill(color)
                    .frame(width: total > 0 ? proxy.size.width * CGFloat(done) / CGFloat(total) : 0)
            }
        }
        .frame(height: 6)
        .padding(.top, 10)
    }
}

/// Scrolling tab body with the standard padding.
struct TabScroll<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) { content }
                .padding(EdgeInsets(top: 4, leading: 16, bottom: 24, trailing: 16))
        }
        .background(BvColors.background)
    }
}
