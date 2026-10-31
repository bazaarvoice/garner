import BVGarner
import XCTest
@testable import BVGarnerApp

final class DetailsReportTests: XCTestCase {
    private func input(diag: BVPush.Diagnostics?) -> ReportInput {
        ReportInput(
            diag: diag, deviceId: nil, stateError: nil, fcmToken: "fcm-1", apnsToken: nil,
            appName: "BV Garner", bundleId: "bv.garner.test.app", appId: 20_000_017,
            baseUrl: "https://push.qa.example.com", userId: "tester-001", email: " ",
            quiet: QuietStatus(mode: .unknown, window: nil), zone: TimeZone(identifier: "UTC")!
        )
    }

    private let diag = BVPush.Diagnostics(
        sdkVersion: "1.1.0", appId: 20_000_017, registered: true, deviceId: "dev-9", tokenSynced: false,
        identityPhase: "LINKED", hasSession: true, sessionExpiry: "1700000000000", desiredOptedOut: false,
        serverOptedOut: nil, quietWindow: nil, notificationPermission: "granted", fcmConflict: false,
        lastError: nil, lastCorrelationId: "corr-1"
    )

    private func value(_ values: [ReportValue], _ field: ReportField) -> String? {
        values.first { $0.field == field }?.value
    }

    func testFieldsInSectionOrderWithUniqueKeys() {
        XCTAssertEqual(ReportField.allCases.first?.section, .device)
        XCTAssertEqual(ReportField.allCases.last?.section, .user)
        XCTAssertEqual(Set(ReportField.allCases.map(\.key)).count, ReportField.allCases.count)
        XCTAssertTrue(ReportField.deviceId.copyable)
        XCTAssertFalse(ReportField.permission.copyable)
        XCTAssertEqual(ReportField.user.label, "User ID")
    }

    func testValuesBeforeTheSdkAnswers() {
        let values = DetailsReport.values(input(diag: nil))
        XCTAssertEqual(value(values, .deviceId), "none")
        XCTAssertEqual(value(values, .fcmToken), "fcm-1")
        XCTAssertEqual(value(values, .apnsToken), "none")
        XCTAssertEqual(value(values, .registered), "unknown")
        XCTAssertEqual(value(values, .appId), "20000017")
        XCTAssertEqual(value(values, .environment), "QA")
        XCTAssertEqual(value(values, .email), "none")
        XCTAssertEqual(value(values, .user), "tester-001")
        XCTAssertEqual(value(values, .optedOut), "unknown")
    }

    func testValuesFromDiagnostics() {
        let values = DetailsReport.values(input(diag: diag))
        XCTAssertEqual(value(values, .deviceId), "dev-9")
        XCTAssertEqual(value(values, .registered), "yes")
        XCTAssertEqual(value(values, .tokenSynced), "no")
        XCTAssertEqual(value(values, .sessionUntil), "14 Nov 22:13")
        XCTAssertEqual(value(values, .optedOut), "false / server unknown")
        XCTAssertEqual(value(values, .sdkVersion), "1.1.0")
        XCTAssertEqual(value(values, .correlationId), "corr-1")
    }

    func testJson() {
        let json = DetailsReport.toJson([
            ReportValue(field: .deviceId, value: "a\"b"),
            ReportValue(field: .lastError, value: "line\n\u{1}"),
        ])
        XCTAssertEqual(json, "{\n  \"deviceId\": \"a\\\"b\",\n  \"lastError\": \"line\\n\\u0001\"\n}")
        let parsed = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: String]
        XCTAssertEqual(parsed?["lastError"], "line\n\u{1}")
    }

    func testInspectorFormat() {
        let state = BVPush.State(initialized: true, deviceId: "dev-9", lastError: nil)
        XCTAssertEqual(
            AppModel.inspectorText(state, diag),
            "BVPush initialized=true\ndeviceId=dev-9\nsession=true\noptedOut=false\nerror=none"
        )
    }
}
