import BVGarner
import XCTest
@testable import BVGarnerApp

/// Inbox model and texts: PushEvents, PushRecords, PushLabels, PushLogCodec, EventText.
final class PushLogTests: XCTestCase {
    private let utc = TimeZone(identifier: "UTC")!

    private func entry(_ id: String, _ step: PushStep, _ at: Int64, _ detail: String = "") -> PushLine {
        .entry(PushEntry(id: id, step: step, atMillis: at, detail: detail))
    }

    func testPushEventsMapNotificationEventsOnly() {
        XCTAssertEqual(PushEvents.toEntry(.notificationReceived(id: "n1"), nowMillis: 5),
                       PushEntry(id: "n1", step: .received, atMillis: 5))
        XCTAssertEqual(PushEvents.toEntry(.notificationHeld(id: "n1", untilMillis: 99), nowMillis: 5)?.detail, "99")
        XCTAssertEqual(PushEvents.toEntry(.notificationSuppressed(id: "n1", reason: "logged-out"), nowMillis: 5)?.step, .hidden)
        XCTAssertEqual(PushEvents.toEntry(.reviewOpened(id: "n1", presenter: "safari"), nowMillis: 5)?.detail, "safari")
        XCTAssertEqual(PushEvents.toEntry(.notificationOpened(id: "n1"), nowMillis: 5)?.step, .tapped)
        XCTAssertNil(PushEvents.toEntry(.registered, nowMillis: 5))
        XCTAssertNil(PushEvents.toEntry(.error(kind: "X"), nowMillis: 5))
    }

    func testStatusLatestOutcomeWins() {
        let records = PushRecords.group([
            entry("a", .received, 1), entry("a", .held, 2, "100"), entry("a", .shown, 3),
            entry("b", .received, 4), entry("b", .hidden, 5, "opted-out"),
            entry("c", .received, 6),
            entry("d", .tapped, 7),
        ])
        let status = Dictionary(uniqueKeysWithValues: records.map { ($0.id, $0.status) })
        XCTAssertEqual(status["a"], .shown)
        XCTAssertEqual(status["b"], .hidden)
        XCTAssertEqual(status["c"], .received)
        XCTAssertEqual(status["d"], .shown)
    }

    func testGroupNewestFirstAndDropsContentOnly() {
        let records = PushRecords.group([
            entry("old", .received, 10),
            .content(PushContent(id: "orphan", title: "t", body: "b")),
            entry("new", .shown, 20),
            .content(PushContent(id: "new", title: "Rate it", body: "How was it?")),
            entry("old", .shown, 30),
        ])
        XCTAssertEqual(records.map(\.id), ["new", "old"])
        XCTAssertEqual(records[0].content?.title, "Rate it")
        XCTAssertEqual(records[1].firstAtMillis, 10)
        XCTAssertEqual(records[1].steps.map(\.step), [.received, .shown])
    }

    func testLimitUnseenAndCount() {
        let lines = (0 ..< 5).map { entry("n\($0)", .shown, Int64($0)) }
        XCTAssertEqual(PushRecords.limit(lines, max: 2).map(\.id), ["n3", "n4"])
        let records = PushRecords.group(lines)
        XCTAssertEqual(PushRecords.unseen(records, seenAtMillis: 2), 2)
        XCTAssertEqual(PushRecords.count(records, .all), 5)
        XCTAssertEqual(PushRecords.count(records, .shown), 5)
        XCTAssertEqual(PushRecords.count(records, .hidden), 0)
    }

    func testCodecRoundTripWithEscapes() {
        let lines: [PushLine] = [
            entry("id\t1", .hidden, 1_700_000_000_000, "logged-out"),
            .content(PushContent(id: "id\t1", title: "Line\none", body: "back\\slash\r")),
            entry("id2", .reviewOpened, 2, "safari"),
        ]
        for line in lines {
            XCTAssertEqual(PushLogCodec.decode(PushLogCodec.encode(line)), line)
        }
        XCTAssertEqual(PushLogCodec.encode(entry("a", .shown, 3)), "v1\tE\ta\tshown\t3\t")
    }

    func testCodecRejectsBrokenLines() {
        XCTAssertNil(PushLogCodec.decode(""))
        XCTAssertNil(PushLogCodec.decode("v2\tE\ta\tshown\t3\t"))
        XCTAssertNil(PushLogCodec.decode("v1\tE\t\tshown\t3\t"))
        XCTAssertNil(PushLogCodec.decode("v1\tE\ta\tlost\t3\t"))
        XCTAssertNil(PushLogCodec.decode("v1\tE\ta\tshown\tsoon\t"))
        XCTAssertNil(PushLogCodec.decode("v1\tC\ta\tonly-title"))
        XCTAssertNil(PushLogCodec.decode("v1\tX\ta\tb\tc"))
    }

    func testLabels() {
        XCTAssertEqual(PushLabels.shortId("short-id"), "id short-id")
        XCTAssertEqual(PushLabels.shortId("7c41aaaabbbbe2a0"), "id 7c41…e2a0")
        let hidden = PushRecords.group([entry("n1", .received, 1), entry("n1", .hidden, 2, "logged-out")])[0]
        XCTAssertEqual(PushLabels.title(hidden), "id n1")
        XCTAssertEqual(PushLabels.body(hidden), UiText.BODY_HIDDEN)
        XCTAssertEqual(PushLabels.chips(hidden, utc), [ToneText(text: "Hidden: no user signed in", tone: .bad)])
        let received = PushRecords.group([entry("n2", .received, 1)])[0]
        XCTAssertEqual(PushLabels.chips(received, utc), [ToneText(text: UiText.STEP_RECEIVED_ONLY, tone: .wait)])
        XCTAssertEqual(PushLabels.body(received), UiText.BODY_RECEIVED)
    }

    func testChipsHeldShownLateAndTaps() {
        let start: Int64 = 1_700_000_000_000 // 22:13 UTC
        let record = PushRecords.group([
            entry("n", .received, start),
            entry("n", .held, start + 1, String(start + 3_600_000)),
            entry("n", .shown, start + 3_600_000),
            entry("n", .tapped, start + 3_700_000),
            entry("n", .reviewOpened, start + 3_700_001, "safari"),
            .content(PushContent(id: "n", title: "Rate it", body: "Tell us")),
        ])[0]
        XCTAssertEqual(PushLabels.title(record), "Rate it")
        XCTAssertEqual(PushLabels.body(record), "Tell us")
        XCTAssertEqual(PushLabels.chips(record, utc), [
            ToneText(text: "Held until 23:13", tone: .wait),
            ToneText(text: "Shown 23:13", tone: .ok),
            ToneText(text: UiText.STEP_TAPPED_REVIEW, tone: .ok),
        ])
    }

    func testEventText() {
        XCTAssertEqual(EventText.describe(.notificationDisplayed(id: "n"), utc), ToneText(text: "Shown on screen", tone: .ok))
        XCTAssertEqual(
            EventText.describe(.notificationSuppressed(id: "n", reason: "logged-out"), utc).text,
            "Hidden: logged-out (no user signed in: see Account)"
        )
        XCTAssertEqual(EventText.describe(.reviewOpened(id: "n", presenter: "in-app-browser"), utc).text,
                       "Review page opened (in app browser)")
        XCTAssertEqual(EventText.describe(.preferenceSynced(optedOut: true), utc).text, UiText.EVENT_OPTED_OUT)
        XCTAssertEqual(EventText.describe(.notificationHeld(id: "n", untilMillis: 1_700_000_000_000), utc).text,
                       "Held for quiet hours (shows at 22:13)")
        XCTAssertEqual(EventText.hiddenLabel("weird"), "Hidden: weird")
    }
}
