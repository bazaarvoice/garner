import BVGarner
import Foundation

/// The last SDK events, newest first, in plain words (`EventText`). Started by the app
/// delegate before `BVPush.configure`, and kept for the process.
final class ActivityLog: ObservableObject {
    struct Entry: Equatable, Identifiable {
        let id = UUID()
        let atMillis: Int64
        let line: ToneText

        static func == (lhs: Entry, rhs: Entry) -> Bool { lhs.id == rhs.id }
    }

    static let shared = ActivityLog()
    static let maxEntries = 30

    @Published private(set) var entries: [Entry] = []
    private var observation: BVPush.Observation?

    /// Main thread.
    func start() {
        guard observation == nil else { return }
        observation = BVPush.observeEvents { [weak self] event in
            self?.add(event, atMillis: TimeText.nowMillis())
        }
    }

    func add(_ event: BVPush.Event, atMillis: Int64) {
        let entry = Entry(atMillis: atMillis, line: EventText.describe(event, .current))
        entries = Array(([entry] + entries).prefix(Self.maxEntries))
    }
}
