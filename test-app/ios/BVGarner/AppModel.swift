import BVGarner
import Combine
import SwiftUI
import UIKit

/// Everything the four tabs read and do. Re-reads the SDK on every state change and every
/// 1.5 s (session, quiet hours and the permission change without a state emission), like the
/// Android `MainActivity`.
@MainActor
final class AppModel: ObservableObject {
    enum Pending { case none, signIn, signOut }

    static let pollInterval: TimeInterval = 1.5
    static let slowAfter: TimeInterval = 30
    static let pendingTimeout: TimeInterval = 30
    static let badgeLimit = 99

    @Published private(set) var snapshot = Snapshot.empty
    @Published var selected: TabId = .HOME {
        didSet { refresh() }
    }
    @Published private(set) var toast: String?
    @Published private(set) var inspector = ""
    @Published private(set) var badge = 0
    @Published private(set) var records: [PushRecord] = []
    @Published private(set) var events: [ActivityLog.Entry] = []

    // Account tab state.
    @Published private(set) var pending = Pending.none
    @Published var formError: ResultLine?
    @Published var signInResult: ResultLine?
    @Published var signOutResult: ResultLine?
    @Published var quietResult: ResultLine?

    let prefs = TesterPrefs()
    let appName = AppConfig.appName
    let bundleId = AppConfig.bundleId
    let baseUrl = AppConfig.baseUrl

    private let openedAt = Date()
    private var pendingSince = Date()
    /// The step the SDK said a sign-in waits for; no timeout while set.
    private var loginWait: String?
    private var timer: Timer?
    private var stateObservation: BVPush.Observation?
    private var subscriptions = Set<AnyCancellable>()
    private var loggedDeviceId: String?
    private var toastWork: DispatchWorkItem?

    init() {
        #if DEBUG
        if let tab = Driver.tab { selected = tab }
        #endif
    }

    /// Main thread, once the first screen is up.
    func start() {
        guard timer == nil else { return }
        PushHistory.shared.$records
            .receive(on: RunLoop.main)
            .sink { [weak self] records in
                self?.records = records
                self?.refresh()
            }
            .store(in: &subscriptions)
        ActivityLog.shared.$entries
            .receive(on: RunLoop.main)
            .sink { [weak self] entries in self?.events = entries }
            .store(in: &subscriptions)
        stateObservation = BVPush.observeState { [weak self] _ in
            Task { @MainActor in self?.refresh() }
        }
        timer = Timer.scheduledTimer(withTimeInterval: Self.pollInterval, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.poll() }
        }
        refresh()
    }

    private func poll() {
        BVPush.refreshNotificationPermission { [weak self] _ in
            Task { @MainActor in self?.refresh() }
        }
    }

    /// Re-reads the SDK and repaints. Main thread.
    func refresh() {
        let state = BVPush.state
        let diag = BVPush.diagnostics()
        let now = Date()
        logDeviceId(state.deviceId)
        snapshot = Snapshot(
            state: state,
            diag: diag,
            notificationsAllowed: BVPush.notificationPermission == BVPush.permissionGranted,
            fcmToken: BVPush.fcmToken,
            apnsToken: BVPush.apnsToken,
            slow: now.timeIntervalSince(openedAt) > Self.slowAfter,
            nowMillis: TimeText.nowMillis(now),
            nowMinutes: TimeText.minutesOfDay(now),
            configuredAppId: AppConfig.appId
        )
        inspector = Self.inspectorText(state, diag)
        resolvePending(signedIn: snapshot.signedIn)
        if selected == .INBOX {
            if let newest = records.map(\.firstAtMillis).max() { prefs.markInboxSeen(atMillis: newest) }
            badge = 0
        } else {
            badge = PushRecords.unseen(records, seenAtMillis: prefs.inboxSeenAt)
        }
    }

    var badgeText: String? {
        guard badge > 0 else { return nil }
        return badge > Self.badgeLimit ? UiText.BADGE_MAX : String(badge)
    }

    /// The inspector block support and UI tests read; keep the format (same as Android).
    nonisolated static func inspectorText(_ state: BVPush.State, _ diag: BVPush.Diagnostics?) -> String {
        "BVPush initialized=\(state.initialized)\n" +
            "deviceId=\(state.deviceId ?? UiText.NONE)\n" +
            "session=\(diag.map { String($0.hasSession) } ?? "null")\n" +
            "optedOut=\(diag.map { String($0.desiredOptedOut) } ?? "null")\n" +
            "error=\(state.lastError ?? diag?.lastError ?? UiText.NONE)"
    }

    /// Logs the device id once, for Xcode console users and the harness.
    private func logDeviceId(_ deviceId: String?) {
        guard let deviceId, deviceId != loggedDeviceId else { return }
        loggedDeviceId = deviceId
        NSLog("BVPushDriver DEVICEID %@", deviceId)
    }

    // MARK: - Top bar

    func title(for tab: TabId) -> String {
        tab == .HOME ? appName : tab.label
    }

    func chips(for tab: TabId) -> [ToneText] {
        switch tab {
        case .HOME:
            return [
                ToneText(text: AppInfo.environment(baseUrl), tone: .normal),
                ToneText(text: UiText.CHIP_APP_ID + String(snapshot.appId), tone: .normal),
                ToneText(text: UiText.CHIP_SDK + (snapshot.diag?.sdkVersion ?? UiText.UNKNOWN), tone: .normal),
            ]
        case .ACCOUNT:
            switch pending {
            case .signIn: return [ToneText(text: UiText.SIGNING_IN, tone: .wait)]
            case .signOut: return [ToneText(text: UiText.SIGNING_OUT, tone: .wait)]
            case .none:
                return [snapshot.signedIn
                    ? ToneText(text: UiText.SIGNED_IN, tone: .ok)
                    : ToneText(text: UiText.SIGNED_OUT, tone: .bad)]
            }
        case .INBOX:
            return [ToneText(text: UiText.inboxChip(records.count), tone: .normal)]
        case .DETAILS:
            return [ToneText(text: UiText.DETAILS_CHIP, tone: .normal)]
        }
    }

    // MARK: - Shared actions

    func copy(_ label: String, _ value: String) {
        UIPasteboard.general.string = value
        show(UiText.copied(label))
    }

    func show(_ message: String) {
        toastWork?.cancel()
        toast = message
        let work = DispatchWorkItem { [weak self] in self?.toast = nil }
        toastWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 2, execute: work)
    }

    /// Not asked yet: ask. Else open this app's notification settings.
    func allowNotifications() {
        if BVPush.notificationPermission == BVPush.permissionNotDetermined {
            BVPush.requestNotificationAuthorization { [weak self] _ in
                Task { @MainActor in self?.refresh() }
            }
            return
        }
        guard let url = URL(string: UIApplication.openNotificationSettingsURLString) else {
            show(UiText.SETTINGS_MANUAL)
            return
        }
        UIApplication.shared.open(url) { [weak self] opened in
            Task { @MainActor in self?.show(opened ? UiText.SETTINGS_OPENED : UiText.SETTINGS_MANUAL) }
        }
    }

    // MARK: - Account

    func signIn(userId: String, email: String) {
        let problems = SignInForm.problems(userId: userId, email: email)
        guard problems.isEmpty else {
            formError = ResultLine(tone: .bad, message: problems.map(\.message).joined(separator: "\n"))
            return
        }
        formError = nil
        signOutResult = nil
        let user = SignInForm.clean(userId)
        let address = SignInForm.clean(email)
        prefs.saveIdentity(userId: user, email: address)
        do {
            try BVPush.linkIdentity(userId: user, email: address) { [weak self] reason in
                guard let reason else { return }
                Task { @MainActor in self?.signInAnswered(reason) }
            }
            startPending(.signIn)
            let later = BVPush.state.initialized ? "" : UiText.SIGNING_IN_LATER
            signInResult = ResultLine(tone: .wait, message: UiText.signingIn(address) + later)
        } catch {
            signInResult = ResultLine(tone: .bad, message: UiText.signInFailed(error.localizedDescription))
        }
        refresh()
    }

    func signOut() {
        BVPush.unlinkIdentity(userId: prefs.userId)
        startPending(.signOut)
        signInResult = nil
        signOutResult = ResultLine(tone: .wait, message: UiText.SIGNING_OUT)
        refresh()
    }

    /// A wait reason keeps the sign-in pending without a timeout (the SDK signs in by itself
    /// later); any other reason means it failed.
    private func signInAnswered(_ reason: String) {
        guard EventText.isLoginWait(reason) else {
            pending = .none
            signInResult = ResultLine(tone: .bad, message: UiText.signInFailed(reason))
            return
        }
        guard pending == .signIn else { return }
        loginWait = reason
        signInResult = ResultLine(tone: .wait, message: EventText.loginWait(reason))
    }

    private func startPending(_ kind: Pending) {
        pending = kind
        pendingSince = Date()
        loginWait = nil
    }

    private func resolvePending(signedIn: Bool) {
        let waited = Date().timeIntervalSince(pendingSince)
        switch pending {
        case .none:
            // The session ended outside this screen (driver, expiry).
            if !signedIn, snapshot.diag != nil, prefs.signedInAt != 0 { prefs.markSignedOut() }
        case .signIn:
            if signedIn {
                prefs.markSignedIn(atMillis: snapshot.nowMillis)
                signInResult = nil
                loginWait = nil
                pending = .none
            } else if loginWait == nil, waited > Self.pendingTimeout {
                signInResult = ResultLine(tone: .bad, message: UiText.SIGN_IN_TIMEOUT)
                pending = .none
            }
        case .signOut:
            if !signedIn {
                prefs.markSignedOut()
                signOutResult = nil
                signInResult = ResultLine(tone: .ok, message: UiText.SIGN_OUT_DONE)
                pending = .none
            } else if waited > Self.pendingTimeout {
                signOutResult = ResultLine(tone: .bad, message: UiText.SIGN_OUT_TIMEOUT)
                pending = .none
            }
        }
    }

    var receivePushes: Bool { snapshot.diag?.desiredOptedOut != true }

    func setReceivePushes(_ receive: Bool) {
        if receive { BVPush.optIn() } else { BVPush.optOut() }
        refresh()
    }

    func quietOff() {
        applyQuiet(expected: nil) { BVPush.disableQuietHours() }
    }

    func quietDefault() {
        applyQuiet(expected: .default) { BVPush.resetQuietHours() }
    }

    func setQuiet(_ window: BVPush.QuietWindow) {
        applyQuiet(expected: window) { try BVPush.setQuietHours(window) }
    }

    /// The SDK ignores quiet hours calls until its stores exist; read the window back to be sure.
    private func applyQuiet(expected: BVPush.QuietWindow?, change: () throws -> Void) {
        do {
            try change()
            let diag = BVPush.diagnostics()
            if let diag, diag.quietWindow == expected.map(QuietText.sdkText) {
                quietResult = ResultLine(tone: .ok, message: UiText.QUIET_SAVED)
            } else {
                quietResult = ResultLine(tone: .bad, message: UiText.QUIET_NOT_READY)
            }
        } catch {
            quietResult = ResultLine(tone: .bad, message: error.localizedDescription)
        }
        refresh()
    }

    // MARK: - Details

    func reportValues() -> [ReportValue] {
        DetailsReport.values(ReportInput(
            diag: snapshot.diag,
            deviceId: snapshot.state.deviceId,
            stateError: snapshot.state.lastError,
            fcmToken: snapshot.fcmToken,
            apnsToken: snapshot.apnsToken,
            appName: appName,
            bundleId: bundleId,
            appId: snapshot.appId,
            baseUrl: baseUrl,
            userId: prefs.userId,
            email: prefs.email,
            quiet: snapshot.quiet,
            zone: .current
        ))
    }

    func clearInbox() {
        PushHistory.shared.clear()
    }
}
