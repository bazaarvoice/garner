import BVGarnerKit
import Foundation
import Network
import UIKit
import UserNotifications

/// Public Swift facade. Delegates to the Kotlin shared engine
/// (`IosFacade` in BVGarnerKit, with Keychain and App Group stores). Darwin APIs
/// (app lifecycle, NWPathMonitor, APNs/FCM tokens, the notification centre)
/// live here and forward to the engine. Call from the main thread; observers
/// are called on the main thread.
public enum BVPush {
    // MARK: - Types

    public struct Options {
        /// Test builds only: a Garner Backend other than the public push host.
        public var baseUrlOverride: String?
        /// Allows a plain `http://` base URL (local stack). Never in production.
        public var allowInsecureLocalhost: Bool
        /// App Group shared with a Notification Service Extension; default is app-only storage.
        public var appGroup: String?
        public var sessionRefreshIntervalHours: Int
        public var logLevel: String

        public init(
            baseUrlOverride: String? = nil,
            allowInsecureLocalhost: Bool = false,
            appGroup: String? = nil,
            sessionRefreshIntervalHours: Int = 24,
            logLevel: String = "warn"
        ) {
            self.baseUrlOverride = baseUrlOverride
            self.allowInsecureLocalhost = allowInsecureLocalhost
            self.appGroup = appGroup
            self.sessionRefreshIntervalHours = sessionRefreshIntervalHours
            self.logLevel = logLevel
        }
    }

    /// What the app can show at a glance; `initialized` turns true after the first sync attempt.
    public struct State: Equatable {
        public let initialized: Bool
        public let deviceId: String?
        public let lastError: String?

        public init(initialized: Bool, deviceId: String?, lastError: String?) {
            self.initialized = initialized
            self.deviceId = deviceId
            self.lastError = lastError
        }

        public static let notConfigured = State(initialized: false, deviceId: nil, lastError: nil)
    }

    /// Quiet hours on the device's wall clock; start inclusive, end exclusive, may cross midnight.
    public struct QuietWindow: Equatable {
        public let startHour: Int
        public let startMinute: Int
        public let endHour: Int
        public let endMinute: Int

        public init(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
            self.startHour = startHour
            self.startMinute = startMinute
            self.endHour = endHour
            self.endMinute = endMinute
        }

        /// The SDK default: 20:00–08:00.
        public static let `default` = QuietWindow(startHour: 20, startMinute: 0, endHour: 8, endMinute: 0)
    }

    /// Diagnostics snapshot. Secrets never appear here.
    public struct Diagnostics: Equatable {
        public let sdkVersion: String
        public let appId: Int?
        public let registered: Bool
        public let deviceId: String?
        public let tokenSynced: Bool
        public let identityPhase: String
        public let hasSession: Bool
        public let sessionExpiry: String?
        public let desiredOptedOut: Bool
        public let serverOptedOut: Bool?
        /// `QuietWindow(startHour=20, startMinute=0, endHour=8, endMinute=0)`, or nil when off.
        public let quietWindow: String?
        public let notificationPermission: String
        public let fcmConflict: Bool
        public let lastError: String?
        public let lastCorrelationId: String?

        public init(
            sdkVersion: String,
            appId: Int?,
            registered: Bool,
            deviceId: String?,
            tokenSynced: Bool,
            identityPhase: String,
            hasSession: Bool,
            sessionExpiry: String?,
            desiredOptedOut: Bool,
            serverOptedOut: Bool?,
            quietWindow: String?,
            notificationPermission: String,
            fcmConflict: Bool,
            lastError: String?,
            lastCorrelationId: String?
        ) {
            self.sdkVersion = sdkVersion
            self.appId = appId
            self.registered = registered
            self.deviceId = deviceId
            self.tokenSynced = tokenSynced
            self.identityPhase = identityPhase
            self.hasSession = hasSession
            self.sessionExpiry = sessionExpiry
            self.desiredOptedOut = desiredOptedOut
            self.serverOptedOut = serverOptedOut
            self.quietWindow = quietWindow
            self.notificationPermission = notificationPermission
            self.fcmConflict = fcmConflict
            self.lastError = lastError
            self.lastCorrelationId = lastCorrelationId
        }
    }

    /// Local lifecycle events, the same set as Android's `BVPushEvent`.
    public enum Event: Equatable {
        case registered
        case registrationBlocked(reason: String)
        case pushTokenSynced
        case identityLinked
        /// `linkIdentity` kept the user but can't log in yet; the SDK logs in by itself once
        /// the step is done. `reason` is `loginWaitNotRegistered` or `loginWaitPushToken`.
        case identityPending(reason: String)
        case signedIn
        case sessionRefreshed
        case signedOut
        case identityUnlinked
        case preferenceSynced(optedOut: Bool)
        case notificationReceived(id: String)
        case notificationDisplayed(id: String)
        case notificationHeld(id: String, untilMillis: Int64)
        case notificationSuppressed(id: String, reason: String)
        case notificationOpened(id: String)
        case reviewOpened(id: String, presenter: String)
        case error(kind: String)
    }

    public enum BVPushError: Error, Equatable, LocalizedError {
        case notConfigured
        case invalidArgument(String)

        public var errorDescription: String? {
            switch self {
            case .notConfigured: return "BVPush.configure was not called"
            case let .invalidArgument(reason): return reason
            }
        }
    }

    /// Observer handle; call `cancel()` or drop it to stop.
    public final class Observation {
        private let onCancel: () -> Void
        private var cancelled = false

        init(onCancel: @escaping () -> Void) {
            self.onCancel = onCancel
        }

        public func cancel() {
            guard !cancelled else { return }
            cancelled = true
            onCancel()
        }

        deinit { cancel() }
    }

    // MARK: - Error kinds and permission values

    public static let errorInvalidAppId = "INVALID_APP_ID"
    public static let errorInsecureBaseUrl = "INSECURE_BASE_URL"
    public static let permissionGranted = "granted"
    public static let permissionDenied = "denied"
    public static let permissionNotDetermined = "not-determined"
    public static let permissionUnknown = "unknown"

    // MARK: - Login wait reasons

    /// `linkIdentity` completion and `.identityPending`: no device ID yet. The SDK logs in once
    /// the device is registered (`state.lastError` names a blocked registration).
    public static let loginWaitNotRegistered = "NOT_REGISTERED"
    /// `linkIdentity` completion and `.identityPending`: registered, but the push token isn't
    /// sent yet. The SDK logs in once it is.
    public static let loginWaitPushToken = "WAITING_FOR_PUSH_TOKEN"

    // MARK: - Engine

    private static var engine: IosFacade?
    private static var configuredAppId: Int?
    private static var configuredBaseUrl: String?
    private static var tapHandler: ((String) -> Bool)?
    private static var pendingFcmToken: String?
    private static var cachedState = State.notConfigured
    private static var cachedPermission = permissionUnknown
    private static var stateObservers: [UUID: (State) -> Void] = [:]
    private static var eventObservers: [UUID: (Event) -> Void] = [:]
    private static var pathMonitor: NWPathMonitor?
    private static var lifecycleTokens: [NSObjectProtocol] = []
    private static var reviewLinks: [String: String] = [:]

    /// The Kotlin engine, for the notification handler in this module.
    static var facade: IosFacade? { engine }

    /// Starts the SDK for one Portal app id (an 8-digit number).
    /// Never throws: an invalid app id or an insecure base URL leaves the SDK dormant and
    /// reports `.error`. Returns whether the SDK started. Calling again replaces the engine.
    @discardableResult
    public static func configure(appId: Int, options: Options = Options()) -> Bool {
        guard appId > 0, appId <= Int(Int32.max), BVPushConfig.shared.isValidAppId(appId: Int32(appId)) else {
            dormant(kind: errorInvalidAppId)
            return false
        }
        if let url = options.baseUrlOverride, url.lowercased().hasPrefix("http://"), !options.allowInsecureLocalhost {
            dormant(kind: errorInsecureBaseUrl)
            return false
        }
        let kotlinOptions = BVPushOptions(
            baseUrlOverride: options.baseUrlOverride,
            autoFetchFcmToken: false,
            openReviewAutomatically: true,
            sessionKeepAlive: true,
            sessionRefreshIntervalHours: Int64(options.sessionRefreshIntervalHours),
            allowInsecureLocalhost: options.allowInsecureLocalhost
        )
        let secure = KeychainSecureStore(service: "com.bazaarvoice.push.\(appId)")
        let settings = AppGroupSettingsStore(suiteName: options.appGroup ?? "com.bazaarvoice.push.\(appId)")
        let facade = IosFacade(appId: Int32(appId), options: kotlinOptions, secure: secure, settings: settings)
        engine = facade
        configuredAppId = appId
        configuredBaseUrl = BVPushConfig.shared.resolveBaseUrl(options: kotlinOptions)

        facade.setEventListener { event in
            guard let mapped = Self.event(from: event) else { return }
            DispatchQueue.main.async { publish(mapped) }
        }
        facade.setStateListener { deviceId, lastError in
            let state = State(initialized: facade.isInitialized(), deviceId: deviceId, lastError: lastError)
            DispatchQueue.main.async {
                guard engine === facade else { return }
                publish(state)
            }
        }
        facade.setNotificationPermission(value: cachedPermission)
        if let token = pendingFcmToken {
            facade.setFcmToken(token: token)
        }
        startMonitors()
        refreshNotificationPermission()
        facade.initialize()
        return true
    }

    public static var isConfigured: Bool { engine != nil }

    /// The app id passed to `configure`, when it started the SDK.
    public static var appId: Int? { configuredAppId }

    /// The Garner Backend the SDK talks to.
    public static var baseUrl: String? { configuredBaseUrl }

    public static var state: State { cachedState }

    public static var deviceId: String? { engine?.currentDeviceId() ?? cachedState.deviceId }

    /// The device id to enter in the Portal test-push form.
    public static var testDeviceIdentifier: String? { deviceId }

    /// Null until `configure`; values are read live from the SDK stores.
    public static func diagnostics() -> Diagnostics? {
        guard let d = engine?.diagnostics() else { return nil }
        return Diagnostics(
            sdkVersion: d.sdkVersion,
            appId: d.appId?.intValue,
            registered: d.registered,
            deviceId: d.deviceId,
            tokenSynced: d.tokenSynced,
            identityPhase: d.identityPhase,
            hasSession: d.hasSession,
            sessionExpiry: d.sessionExpiry,
            desiredOptedOut: d.desiredOptedOut,
            serverOptedOut: d.serverOptedOut?.boolValue,
            quietWindow: d.quietWindow,
            notificationPermission: d.notificationPermission,
            fcmConflict: d.fcmConflict,
            lastError: d.lastError,
            lastCorrelationId: d.lastCorrelationId
        )
    }

    // MARK: - Observers

    /// Called on the main thread with the current state right away and after every sync.
    public static func observeState(_ observer: @escaping (State) -> Void) -> Observation {
        let id = UUID()
        stateObservers[id] = observer
        observer(cachedState)
        return Observation { DispatchQueue.main.async { stateObservers[id] = nil } }
    }

    /// Called on the main thread for every SDK event from now on.
    public static func observeEvents(_ observer: @escaping (Event) -> Void) -> Observation {
        let id = UUID()
        eventObservers[id] = observer
        return Observation { DispatchQueue.main.async { eventObservers[id] = nil } }
    }

    // MARK: - Identity and preferences

    /// Links the consumer by email, the only link key. Throws for an empty or
    /// too-long user id or an invalid email. The SDK logs in only after register and push token
    /// sync. The completion gets nil once the user is signed in or the login ran (the session
    /// shows up as `.signedIn` and `diagnostics().hasSession`), or `loginWaitNotRegistered` /
    /// `loginWaitPushToken` when the SDK kept the user and waits for that step (`.identityPending`
    /// says the same); it then logs in by itself.
    public static func linkIdentity(userId: String, email: String, completion: ((String?) -> Void)? = nil) throws {
        guard let engine else { throw BVPushError.notConfigured }
        if let problem = engine.identityProblem(userId: userId, email: email) {
            throw BVPushError.invalidArgument(problem)
        }
        engine.linkIdentity(userId: userId, email: email) { reason in
            DispatchQueue.main.async { completion?(reason) }
        }
    }

    public static func unlinkIdentity(userId: String? = nil, completion: ((String?) -> Void)? = nil) {
        engine?.unlinkIdentity { reason in
            DispatchQueue.main.async { completion?(reason) }
        }
    }

    public static func optOut(completion: ((String?) -> Void)? = nil) {
        setOptedOut(true, completion: completion)
    }

    public static func optIn(completion: ((String?) -> Void)? = nil) {
        setOptedOut(false, completion: completion)
    }

    private static func setOptedOut(_ value: Bool, completion: ((String?) -> Void)?) {
        engine?.setOptedOut(optedOut: value) { reason in
            DispatchQueue.main.async { completion?(reason) }
        }
    }

    // MARK: - Quiet hours

    /// Hours 0...23, minutes 0...59; equal start and end never hold a push.
    public static func setQuietHours(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) throws {
        guard let engine else { throw BVPushError.notConfigured }
        let values = [startHour, startMinute, endHour, endMinute]
        guard values.allSatisfy({ $0 >= 0 && $0 < 60 }) else {
            throw BVPushError.invalidArgument("hour 0..23, minute 0..59")
        }
        if let problem = engine.setQuietHours(
            startHour: Int32(startHour),
            startMinute: Int32(startMinute),
            endHour: Int32(endHour),
            endMinute: Int32(endMinute)
        ) {
            throw BVPushError.invalidArgument(problem)
        }
    }

    public static func setQuietHours(_ window: QuietWindow) throws {
        try setQuietHours(
            startHour: window.startHour,
            startMinute: window.startMinute,
            endHour: window.endHour,
            endMinute: window.endMinute
        )
    }

    public static func setQuietHours(start: DateComponents, end: DateComponents) throws {
        try setQuietHours(
            startHour: start.hour ?? 0,
            startMinute: start.minute ?? 0,
            endHour: end.hour ?? 0,
            endMinute: end.minute ?? 0
        )
    }

    public static func disableQuietHours() {
        engine?.disableQuietHours()
    }

    /// Back to the default 20:00–08:00.
    public static func resetQuietHours() {
        engine?.resetQuietHours()
    }

    // MARK: - Tokens

    /// The FCM registration token (from `MessagingDelegate`); safe to call before `configure`.
    public static func setFCMToken(_ token: String?) {
        pendingFcmToken = token
        engine?.setFcmToken(token: token)
    }

    public static var fcmToken: String? { pendingFcmToken }

    /// The APNs device token, hex. With Firebase swizzling off the app also hands it to
    /// `Messaging.messaging().apnsToken`; the SDK only keeps it for diagnostics.
    public private(set) static var apnsToken: String?

    public static func setAPNSToken(_ deviceToken: Data) {
        apnsToken = deviceToken.map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - Notifications

    /// `granted`, `denied`, `not-determined` or `unknown`, as last read.
    public static var notificationPermission: String { cachedPermission }

    /// Reads the notification permission and hands it to the SDK; completion on the main thread.
    public static func refreshNotificationPermission(completion: ((String) -> Void)? = nil) {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            let value = permission(settings.authorizationStatus)
            engine?.setNotificationPermission(value: value)
            DispatchQueue.main.async {
                cachedPermission = value
                completion?(value)
            }
        }
    }

    /// Asks for alert, badge and sound; on yes registers for remote notifications.
    public static func requestNotificationAuthorization(completion: ((Bool) -> Void)? = nil) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            DispatchQueue.main.async {
                if granted { UIApplication.shared.registerForRemoteNotifications() }
                refreshNotificationPermission { _ in completion?(granted) }
            }
        }
    }

    /// Return true from the handler to handle a tapped BV push yourself; else the SDK opens
    /// its review link in Safari.
    public static func setNotificationTapHandler(_ handler: ((String) -> Bool)?) {
        tapHandler = handler
    }

    /// Opens the review link of a push the SDK showed or the user tapped.
    @discardableResult
    public static func openReview(notificationId: String) -> Bool {
        guard let url = reviewLinks[notificationId] else { return false }
        return openReview(notificationId: notificationId, link: url)
    }

    // MARK: - Module internals

    static func consumeTap(notificationId: String) -> Bool {
        tapHandler?(notificationId) ?? false
    }

    static func rememberReviewLink(_ link: String?, for notificationId: String) {
        guard let link, !link.isEmpty else { return }
        DispatchQueue.main.async { reviewLinks[notificationId] = link }
    }

    @discardableResult
    static func openReview(notificationId: String, link: String) -> Bool {
        guard let facade = engine, facade.isOpenableReviewLink(url: link), let url = URL(string: link) else {
            return false
        }
        guard SafariReviewPresenter.present(url: url) else { return false }
        facade.reviewOpened(notificationId: notificationId, presenter: "safari")
        return true
    }

    static func report(_ event: Event) {
        DispatchQueue.main.async { publish(event) }
    }

    // MARK: - Private

    private static func dormant(kind: String) {
        engine = nil
        configuredAppId = nil
        configuredBaseUrl = nil
        cachedState = State(initialized: false, deviceId: nil, lastError: kind)
        publish(cachedState)
        publish(.error(kind: kind))
    }

    private static func publish(_ state: State) {
        cachedState = state
        stateObservers.values.forEach { $0(state) }
    }

    private static func publish(_ event: Event) {
        eventObservers.values.forEach { $0(event) }
    }

    private static func startMonitors() {
        if pathMonitor == nil {
            let monitor = NWPathMonitor()
            monitor.pathUpdateHandler = { path in
                engine?.setConnected(value: path.status == .satisfied)
            }
            monitor.start(queue: DispatchQueue(label: "com.bazaarvoice.push.path"))
            pathMonitor = monitor
        }
        if lifecycleTokens.isEmpty {
            let center = NotificationCenter.default
            lifecycleTokens.append(center.addObserver(
                forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main
            ) { _ in
                refreshNotificationPermission()
                engine?.onForeground()
            })
            lifecycleTokens.append(center.addObserver(
                forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main
            ) { _ in
                refreshNotificationPermission()
                HeldPushes.reconcile()
            })
        }
    }

    private static func permission(_ status: UNAuthorizationStatus) -> String {
        switch status {
        case .authorized, .provisional, .ephemeral: return permissionGranted
        case .denied: return permissionDenied
        case .notDetermined: return permissionNotDetermined
        @unknown default: return permissionUnknown
        }
    }

    static func event(from event: IosEvent) -> Event? {
        let id = event.notificationId ?? ""
        let detail = event.detail ?? ""
        switch event.kind {
        case "registered": return .registered
        case "registration-blocked": return .registrationBlocked(reason: detail)
        case "push-token-synced": return .pushTokenSynced
        case "identity-linked": return .identityLinked
        case "identity-pending": return .identityPending(reason: detail)
        case "signed-in": return .signedIn
        case "session-refreshed": return .sessionRefreshed
        case "signed-out": return .signedOut
        case "identity-unlinked": return .identityUnlinked
        case "preference-synced": return .preferenceSynced(optedOut: detail == "true")
        case "notification-received": return .notificationReceived(id: id)
        case "notification-displayed": return .notificationDisplayed(id: id)
        case "notification-held": return .notificationHeld(id: id, untilMillis: Int64(detail) ?? 0)
        case "notification-suppressed": return .notificationSuppressed(id: id, reason: detail)
        case "notification-opened": return .notificationOpened(id: id)
        case "review-opened": return .reviewOpened(id: id, presenter: detail)
        case "error": return .error(kind: detail)
        default: return nil
        }
    }
}
