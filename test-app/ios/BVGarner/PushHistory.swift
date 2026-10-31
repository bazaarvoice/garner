import BVGarner
import Foundation
import UserNotifications

/// Every push the SDK reports, kept on the device for the Inbox until the tester taps Clear
/// (newest `PushRecords.maxRecords`). Started by the app delegate before `BVPush.configure`,
/// so pushes that arrive while the app runs in the background are kept too.
///
/// Storage: append-only `push-history.log` in Application Support, format `PushLogCodec` (the
/// same as Android). Titles come from the delivered notification (identifier `bv-<id>` or
/// `correspondence_id` in its payload); hidden pushes are never shown, so they keep only their id.
final class PushHistory: ObservableObject {
    static let shared = PushHistory()

    private static let fileName = "push-history.log"
    /// Rewrite the file once this many records past the limit piled up.
    private static let compactSlack = 50
    private static let lookupAttempts = 5
    private static let lookupDelay: TimeInterval = 0.3

    @Published private(set) var records: [PushRecord] = []

    private let io = DispatchQueue(label: "bv.garner.push-history")
    /// Touched only on `io`.
    private var lines: [PushLine] = []
    private var file: URL?
    private var observation: BVPush.Observation?

    /// Main thread. Subscribes to SDK events before returning.
    func start() {
        guard observation == nil else { return }
        io.async { self.load() }
        observation = BVPush.observeEvents { [weak self] event in
            guard let self, let entry = PushEvents.toEntry(event, nowMillis: TimeText.nowMillis()) else { return }
            self.io.async { self.append([.entry(entry)]) }
            if entry.step == .shown { self.readContent(id: entry.id, attempt: 0) }
        }
    }

    /// Deletes every record and the file.
    func clear() {
        io.async {
            self.lines.removeAll()
            if let file = self.file { try? FileManager.default.removeItem(at: file) }
            self.publish()
        }
    }

    private func load() {
        let folder = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let target = folder.appendingPathComponent(Self.fileName)
        file = target
        let stored = ((try? String(contentsOf: target, encoding: .utf8)) ?? "")
            .split(separator: "\n", omittingEmptySubsequences: true)
            .map(String.init)
        lines.append(contentsOf: stored.compactMap(PushLogCodec.decode))
        compactIfNeeded(force: stored.count != lines.count)
        publish()
    }

    private func append(_ added: [PushLine]) {
        lines.append(contentsOf: added)
        if let file {
            let text = added.map { PushLogCodec.encode($0) + "\n" }.joined()
            if let handle = try? FileHandle(forWritingTo: file) {
                handle.seekToEndOfFile()
                handle.write(Data(text.utf8))
                try? handle.close()
            } else {
                try? Data(text.utf8).write(to: file, options: .atomic)
            }
        }
        compactIfNeeded(force: false)
        publish()
    }

    /// Keeps the newest records; `force` rewrites the file (for example after broken lines).
    private func compactIfNeeded(force: Bool) {
        let count = Set(lines.map(\.id)).count
        if !force, count <= PushRecords.maxRecords + Self.compactSlack { return }
        lines = PushRecords.limit(lines)
        if let file {
            try? Data(lines.map { PushLogCodec.encode($0) + "\n" }.joined().utf8).write(to: file, options: .atomic)
        }
    }

    private func publish() {
        let grouped = Array(PushRecords.group(lines).prefix(PushRecords.maxRecords))
        DispatchQueue.main.async { self.records = grouped }
    }

    /// Reads title and body of the delivered notification; it may take a moment to appear.
    private func readContent(id: String, attempt: Int) {
        guard attempt < Self.lookupAttempts else { return }
        let delay = attempt == 0 ? 0 : Self.lookupDelay
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
            UNUserNotificationCenter.current().getDeliveredNotifications { delivered in
                let match = delivered.first {
                    $0.request.identifier == IOSNotificationHandler.requestPrefix + id ||
                        ($0.request.content.userInfo["correspondence_id"] as? String) == id
                }
                guard let content = match?.request.content else {
                    self.readContent(id: id, attempt: attempt + 1)
                    return
                }
                let line = PushLine.content(PushContent(id: id, title: content.title, body: content.body))
                self.io.async { self.append([line]) }
            }
        }
    }
}
