import BVGarner
import XCTest
@testable import BVGarnerApp

/// The same rules as the Android test app's JVM tests (TabId, AppInfo, QuietText, Checklist,
/// SignInForm, TimeText).
final class LogicTests: XCTestCase {
    private let utc = TimeZone(identifier: "UTC")!

    func testTabIdFromName() {
        XCTAssertEqual(TabId.fromName("INBOX"), .INBOX)
        XCTAssertEqual(TabId.fromName("DETAILS"), .DETAILS)
        XCTAssertNil(TabId.fromName("inbox"))
        XCTAssertNil(TabId.fromName(nil))
        XCTAssertEqual(TabId.allCases.map(\.label), ["Home", "Account", "Inbox", "Details"])
    }

    func testEnvironment() {
        XCTAssertEqual(AppInfo.environment("http://localhost:8082"), "Local")
        XCTAssertEqual(AppInfo.environment("https://127.0.0.1:8443"), "Local")
        XCTAssertEqual(AppInfo.environment("https://push.qa.example.com"), "QA")
        XCTAssertEqual(AppInfo.environment("https://garner-qa-cert.example.com/api"), "QA")
        XCTAssertEqual(AppInfo.environment("https://push-stg.example.com"), "Staging")
        XCTAssertEqual(AppInfo.environment("https://push.example.com"), "Production")
    }

    func testInitials() {
        XCTAssertEqual(AppInfo.initials(email: "tester.one@example.com", userId: nil), "TO")
        XCTAssertEqual(AppInfo.initials(email: "ab+cd@example.com", userId: nil), "AC")
        XCTAssertEqual(AppInfo.initials(email: "rohit@example.com", userId: nil), "RO")
        XCTAssertEqual(AppInfo.initials(email: "", userId: "tester-001"), "T0")
        XCTAssertEqual(AppInfo.initials(email: nil, userId: " "), "?")
    }

    func testTimeText() {
        let at: Int64 = 1_700_000_000_000 // 2023-11-14 22:13 UTC
        XCTAssertEqual(TimeText.clock(at, utc), "22:13")
        XCTAssertEqual(TimeText.dateTime(at, utc), "14 Nov 22:13")
        XCTAssertEqual(TimeText.inbox(at, now: at + 60_000, utc), "22:13")
        XCTAssertEqual(TimeText.inbox(at, now: at + 3_600_000, utc), "22:13")
        XCTAssertEqual(TimeText.inbox(at, now: at + 7_200_000, utc), "Yesterday 22:13")
        XCTAssertEqual(TimeText.inbox(at, now: at + 3 * 86_400_000, utc), "14 Nov 22:13")
        XCTAssertEqual(TimeText.epochMillis(" 1700000000000 "), at)
        XCTAssertNil(TimeText.epochMillis("0"))
        XCTAssertNil(TimeText.epochMillis("soon"))
        XCTAssertNil(TimeText.epochMillis(nil))
    }

    func testQuietParseAndText() {
        let raw = "QuietWindow(startHour=20, startMinute=0, endHour=8, endMinute=0)"
        XCTAssertEqual(QuietText.parse(raw), .default)
        XCTAssertEqual(QuietText.sdkText(.default), raw)
        XCTAssertNil(QuietText.parse("QuietWindow(startHour=20)"))
        XCTAssertEqual(QuietText.range(.default), "20:00–08:00")
        XCTAssertEqual(QuietText.end(.default), "08:00")
    }

    func testQuietStatus() {
        let raw = QuietText.sdkText(.default)
        XCTAssertEqual(QuietText.status(sdkReady: false, raw: raw, nowMinutes: 0).mode, .unknown)
        XCTAssertEqual(QuietText.status(sdkReady: true, raw: nil, nowMinutes: 0).mode, .off)
        XCTAssertEqual(QuietText.status(sdkReady: true, raw: raw, nowMinutes: 21 * 60).mode, .onNow)
        XCTAssertEqual(QuietText.status(sdkReady: true, raw: raw, nowMinutes: 7 * 60 + 59).mode, .onNow)
        XCTAssertEqual(QuietText.status(sdkReady: true, raw: raw, nowMinutes: 8 * 60).mode, .onLater)
        let same = QuietText.sdkText(BVPush.QuietWindow(startHour: 9, startMinute: 0, endHour: 9, endMinute: 0))
        XCTAssertEqual(QuietText.status(sdkReady: true, raw: same, nowMinutes: 9 * 60).mode, .off)
        XCTAssertEqual(QuietText.status(sdkReady: true, raw: "junk", nowMinutes: 0).mode, .unknown)
    }

    func testQuietSummaryAndValue() {
        let later = QuietText.status(sdkReady: true, raw: QuietText.sdkText(.default), nowMinutes: 12 * 60)
        XCTAssertEqual(QuietText.summary(later), "20:00–08:00: pushes in that window are held (default)")
        XCTAssertEqual(QuietText.value(later), "20:00–08:00")
        let custom = BVPush.QuietWindow(startHour: 22, startMinute: 30, endHour: 6, endMinute: 15)
        let now = QuietText.status(sdkReady: true, raw: QuietText.sdkText(custom), nowMinutes: 23 * 60)
        XCTAssertEqual(QuietText.summary(now), "22:30–06:15, in effect now: pushes are held")
        let off = QuietText.status(sdkReady: true, raw: nil, nowMinutes: 0)
        XCTAssertEqual(QuietText.summary(off), UiText.QUIET_SUMMARY_OFF)
        XCTAssertEqual(QuietText.value(off), "off")
        XCTAssertEqual(QuietText.value(QuietStatus(mode: .unknown, window: nil)), "unknown")
    }

    private func input(
        initialized: Bool = true, deviceId: String? = "dev-1", tokenSynced: Bool = true, hasSession: Bool = true,
        allowed: Bool = true, quiet: QuietMode = .off, slow: Bool = false
    ) -> HealthInput {
        HealthInput(
            initialized: initialized, deviceId: deviceId, tokenSynced: tokenSynced, hasSession: hasSession,
            notificationsAllowed: allowed, quiet: QuietStatus(mode: quiet, window: nil), slow: slow
        )
    }

    func testChecklistReady() {
        let checks = Checklist.evaluate(input())
        XCTAssertEqual(checks.map(\.kind), CheckKind.allCases)
        XCTAssertEqual(Checklist.passed(checks), 5)
        XCTAssertEqual(Checklist.summary(checks), .ready)
    }

    func testChecklistStarting() {
        let checks = Checklist.evaluate(input(initialized: false, deviceId: nil, tokenSynced: false, hasSession: false, quiet: .unknown))
        XCTAssertEqual(checks.map(\.mark), [.wait, .wait, .wait, .ok, .wait])
        XCTAssertEqual(Checklist.summary(checks), .checking)
    }

    func testChecklistProblems() {
        let notRegistered = Checklist.evaluate(input(deviceId: nil, tokenSynced: false, hasSession: false))
        XCTAssertEqual(notRegistered.map(\.mark), [.bad, .bad, .bad, .ok, .ok])
        XCTAssertEqual(Checklist.summary(notRegistered), .action)
        let tokenWaiting = Checklist.evaluate(input(tokenSynced: false))
        XCTAssertEqual(tokenWaiting[1].mark, .wait)
        XCTAssertEqual(Checklist.evaluate(input(tokenSynced: false, slow: true))[1].mark, .bad)
        XCTAssertEqual(Checklist.evaluate(input(allowed: false))[3].mark, .bad)
        XCTAssertEqual(Checklist.evaluate(input(quiet: .onNow))[4].mark, .bad)
        XCTAssertEqual(Checklist.evaluate(input(quiet: .onLater))[4].mark, .ok)
    }

    func testSignInForm() {
        XCTAssertEqual(SignInForm.problems(userId: " tester-001 ", email: " a@b.co "), [])
        XCTAssertEqual(SignInForm.problems(userId: "", email: ""), [.userIdMissing, .emailMissing])
        XCTAssertEqual(SignInForm.problems(userId: "u", email: "no-at"), [.emailInvalid])
        XCTAssertEqual(SignInForm.problems(userId: "u", email: "a b@c"), [.emailInvalid])
        XCTAssertEqual(SignInForm.problems(userId: String(repeating: "u", count: 256), email: "a@b"), [.userIdTooLong])
        XCTAssertEqual(SignInForm.problems(userId: "u", email: String(repeating: "a", count: 250) + "@b.co"), [.emailTooLong])
        XCTAssertEqual(SignInForm.clean("  x \n"), "x")
        XCTAssertEqual(SignInProblem.emailInvalid.message, "Enter a valid email, like name@example.com.")
    }
}
