import BVGarner
import Foundation

/// The Inbox model: every push the SDK reported, kept on the device.
enum PushStep: String, CaseIterable {
    case received = "received"
    case shown = "shown"
    case held = "held"
    case hidden = "hidden"
    case tapped = "tapped"
    case reviewOpened = "review"

    var code: String { rawValue }

    static func fromCode(_ code: String) -> PushStep? { PushStep(rawValue: code) }
}

/// One line of the push history file.
enum PushLine: Equatable {
    case entry(PushEntry)
    case content(PushContent)

    var id: String {
        switch self {
        case let .entry(entry): return entry.id
        case let .content(content): return content.id
        }
    }
}

/// One SDK event for a push. `detail` is the hidden reason, the held-until epoch millis or the
/// review presenter; empty for the other steps.
struct PushEntry: Equatable {
    let id: String
    let step: PushStep
    let atMillis: Int64
    var detail: String = ""
}

/// Title and body read back from the delivered notification.
struct PushContent: Equatable {
    let id: String
    let title: String
    let body: String
}

/// Where a push ended up; the Inbox filters on it.
enum PushStatus { case received, shown, held, hidden }

/// All the steps of one notification id, oldest step first.
struct PushRecord: Equatable, Identifiable {
    let id: String
    let firstAtMillis: Int64
    let steps: [PushEntry]
    let content: PushContent?

    private static let outcomes: Set<PushStep> = [.shown, .held, .hidden]
    private static let taps: Set<PushStep> = [.tapped, .reviewOpened]

    /// The latest outcome wins: a push held at night and shown at 08:00 is shown.
    var status: PushStatus {
        switch steps.last(where: { Self.outcomes.contains($0.step) })?.step {
        case .shown?: return .shown
        case .held?: return .held
        case .hidden?: return .hidden
        default: return steps.contains(where: { Self.taps.contains($0.step) }) ? .shown : .received
        }
    }
}

/// Inbox filter chips. `.all` also lists pushes that have no outcome yet.
enum PushFilter: CaseIterable {
    case all, shown, held, hidden

    private var status: PushStatus? {
        switch self {
        case .all: return nil
        case .shown: return .shown
        case .held: return .held
        case .hidden: return .hidden
        }
    }

    var label: String {
        switch self {
        case .all: return UiText.FILTER_ALL
        case .shown: return UiText.FILTER_SHOWN
        case .held: return UiText.FILTER_HELD
        case .hidden: return UiText.FILTER_HIDDEN
        }
    }

    func matches(_ record: PushRecord) -> Bool { status == nil || record.status == status }
}

enum PushRecords {
    /// Records kept on the device until the tester taps Clear.
    static let maxRecords = 500

    /// One record per notification id, newest first. Content without any event is dropped.
    static func group(_ lines: [PushLine]) -> [PushRecord] {
        var order: [String] = []
        var steps: [String: [PushEntry]] = [:]
        var content: [String: PushContent] = [:]
        for line in lines {
            switch line {
            case let .entry(entry):
                if steps[entry.id] == nil { order.append(entry.id) }
                steps[entry.id, default: []].append(entry)
            case let .content(value):
                content[value.id] = value
            }
        }
        let records = order.map { id -> PushRecord in
            let entries = steps[id] ?? []
            let sorted = entries.enumerated()
                .sorted { $0.element.atMillis == $1.element.atMillis ? $0.offset < $1.offset : $0.element.atMillis < $1.element.atMillis }
                .map(\.element)
            return PushRecord(id: id, firstAtMillis: entries.map(\.atMillis).min() ?? 0, steps: sorted, content: content[id])
        }
        // Stable: equal times keep the order the ids were first seen.
        return records.enumerated()
            .sorted { $0.element.firstAtMillis == $1.element.firstAtMillis ? $0.offset < $1.offset : $0.element.firstAtMillis > $1.element.firstAtMillis }
            .map(\.element)
    }

    /// The lines of the newest `max` records, in their original order.
    static func limit(_ lines: [PushLine], max: Int = maxRecords) -> [PushLine] {
        let keep = Set(group(lines).prefix(max).map(\.id))
        return lines.filter { keep.contains($0.id) }
    }

    /// Records first received after the tester last looked at the Inbox.
    static func unseen(_ records: [PushRecord], seenAtMillis: Int64) -> Int {
        records.filter { $0.firstAtMillis > seenAtMillis }.count
    }

    static func count(_ records: [PushRecord], _ filter: PushFilter) -> Int {
        records.filter { filter.matches($0) }.count
    }
}

/// Maps SDK notification events to history entries; other events give nil.
enum PushEvents {
    static func toEntry(_ event: BVPush.Event, nowMillis: Int64) -> PushEntry? {
        switch event {
        case let .notificationReceived(id): return PushEntry(id: id, step: .received, atMillis: nowMillis)
        case let .notificationDisplayed(id): return PushEntry(id: id, step: .shown, atMillis: nowMillis)
        case let .notificationHeld(id, untilMillis):
            return PushEntry(id: id, step: .held, atMillis: nowMillis, detail: String(untilMillis))
        case let .notificationSuppressed(id, reason):
            return PushEntry(id: id, step: .hidden, atMillis: nowMillis, detail: reason)
        case let .notificationOpened(id): return PushEntry(id: id, step: .tapped, atMillis: nowMillis)
        case let .reviewOpened(id, presenter):
            return PushEntry(id: id, step: .reviewOpened, atMillis: nowMillis, detail: presenter)
        default: return nil
        }
    }
}

/// History file format v1: one line per `PushLine`, tab-separated fields, backslash escapes for
/// backslash, tab, newline and carriage return. The same format as the Android test app.
///
///     v1  E  <id>  <step code>  <epoch millis>  <detail>
///     v1  C  <id>  <title>  <body>
///
/// Unknown versions and broken lines decode to nil and are skipped.
enum PushLogCodec {
    private static let version = "v1"
    private static let kindEntry = "E"
    private static let kindContent = "C"
    private static let separator: Character = "\t"
    private static let escapeChar: Character = "\\"
    private static let entryFields = 6
    private static let contentFields = 5

    static func encode(_ line: PushLine) -> String {
        switch line {
        case let .entry(entry):
            return join([version, kindEntry, entry.id, entry.step.code, String(entry.atMillis), entry.detail])
        case let .content(content):
            return join([version, kindContent, content.id, content.title, content.body])
        }
    }

    static func decode(_ text: String) -> PushLine? {
        let fields = split(text)
        guard fields.count >= 3, fields[0] == version, !fields[2].isEmpty else { return nil }
        switch fields[1] {
        case kindEntry:
            guard fields.count == entryFields,
                  let step = PushStep.fromCode(fields[3]),
                  let at = Int64(fields[4]) else { return nil }
            return .entry(PushEntry(id: fields[2], step: step, atMillis: at, detail: fields[5]))
        case kindContent:
            guard fields.count == contentFields else { return nil }
            return .content(PushContent(id: fields[2], title: fields[3], body: fields[4]))
        default:
            return nil
        }
    }

    private static func join(_ fields: [String]) -> String {
        fields.map(escape).joined(separator: String(separator))
    }

    private static func escape(_ value: String) -> String {
        var out = ""
        for c in value {
            switch c {
            case escapeChar: out += "\\\\"
            case "\t": out += "\\t"
            case "\n": out += "\\n"
            case "\r": out += "\\r"
            default: out.append(c)
            }
        }
        return out
    }

    /// Splits on unescaped tabs and unescapes each field.
    private static func split(_ text: String) -> [String] {
        var fields: [String] = []
        var current = ""
        let chars = Array(text)
        var i = 0
        while i < chars.count {
            let c = chars[i]
            if c == separator {
                fields.append(current)
                current = ""
            } else if c == escapeChar, i + 1 < chars.count {
                i += 1
                switch chars[i] {
                case "t": current.append("\t")
                case "n": current.append("\n")
                case "r": current.append("\r")
                default: current.append(chars[i])
                }
            } else {
                current.append(c)
            }
            i += 1
        }
        fields.append(current)
        return fields
    }
}
