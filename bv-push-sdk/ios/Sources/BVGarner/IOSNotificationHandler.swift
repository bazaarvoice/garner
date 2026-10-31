import BVGarnerKit
import Foundation
import UIKit
import UserNotifications

/// Notification-centre helpers. `willPresent` returns nil when not BV,
/// else `[.banner, .list, .sound]`. `didReceive` drives the tap → review
/// hand-off. The optional delegate proxy forwards everything non-BV to the
/// previously installed delegate.
///
/// Two kinds of BV push reach an iOS app:
/// - data pushes (`content-available`, no `aps.alert`): the SDK runs the same
///   display policy as Android (opted out, signed out, quiet hours) and posts a
///   local notification `bv-<id>` itself, now or when quiet hours end;
/// - alert pushes: the system shows them; the SDK reports them as shown.
public enum IOSNotificationHandler {
    static let sourceKey = "bv_source"
    static let source = "push_sdk"
    static let idKey = "correspondence_id"
    static let reviewKey = "review_url"
    static let localKey = "bv_local"
    static let heldKey = "bv_held"
    /// Every notification the SDK posts has the identifier `bv-<notification id>`.
    public static let requestPrefix = "bv-"
    public static let threadIdentifier = "bv-review"
    static let reasonPermission = "permission-denied"
    static let reasonPostFailed = "post-failed"

    public static func isBV(_ userInfo: [AnyHashable: Any]) -> Bool {
        (userInfo[sourceKey] as? String) == source
    }

    /// The payload's top-level values as strings (`aps` left out), the form the SDK parser
    /// reads. Numbers and booleans become their text; nested objects become JSON text.
    public static func payload(_ userInfo: [AnyHashable: Any]) -> [String: String] {
        var data: [String: String] = [:]
        for (key, value) in userInfo {
            guard let key = key as? String, key != "aps" else { continue }
            if let text = value as? String {
                data[key] = text
            } else if let number = value as? NSNumber {
                data[key] = CFGetTypeID(number) == CFBooleanGetTypeID()
                    ? (number.boolValue ? "true" : "false")
                    : number.stringValue
            } else if JSONSerialization.isValidJSONObject(value),
                      let json = try? JSONSerialization.data(withJSONObject: value),
                      let text = String(data: json, encoding: .utf8) {
                data[key] = text
            }
        }
        return data
    }

    public static func willPresent(
        _ notification: UNNotification
    ) -> UNNotificationPresentationOptions? {
        let info = notification.request.content.userInfo
        guard isBV(info) else { return nil }
        if info[localKey] == nil {
            // A remote alert push while the app is open.
            reportShownBySystem(info)
        } else if (info[heldKey] as? Bool) == true, let id = info[idKey] as? String {
            // A held push whose quiet hours just ended.
            HeldPushes.shown(id)
        }
        return [.banner, .list, .sound]
    }

    @discardableResult
    public static func didReceive(_ response: UNNotificationResponse) -> Bool {
        let info = response.notification.request.content.userInfo
        guard isBV(info) else { return false }
        guard response.actionIdentifier == UNNotificationDefaultActionIdentifier else { return true }
        let parsed = BVPush.facade?.inspectPush(data: payload(info))
        guard let id = parsed?.notificationId ?? (info[idKey] as? String), !id.isEmpty else { return true }
        if (info[heldKey] as? Bool) == true { HeldPushes.shown(id) }
        BVPush.facade?.notificationOpened(notificationId: id)
        if BVPush.consumeTap(notificationId: id) { return true }
        if let link = parsed?.reviewUrl ?? (info[reviewKey] as? String) {
            // Let the app become active first, so Safari opens over the right screen.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                _ = BVPush.openReview(notificationId: id, link: link)
            }
        }
        return true
    }

    /// Received and shown, once per id: an alert push reaches both `willPresent` and
    /// `didReceiveRemoteNotification` when it also has `content-available`.
    static func reportShownBySystem(_ userInfo: [AnyHashable: Any]) {
        guard let facade = BVPush.facade else { return }
        let parsed = facade.inspectPush(data: payload(userInfo))
        guard let id = parsed.notificationId else { return }
        BVPush.rememberReviewLink(parsed.reviewUrl, for: id)
        guard Once.first("shown-by-system:\(id)") else { return }
        facade.notificationReceived(notificationId: id)
        facade.notificationDisplayed(notificationId: id)
    }
}

/// Background-push entry point. Call from
/// `application(_:didReceiveRemoteNotification:fetchCompletionHandler:)`; returns
/// false (and completes with `.noData`) for pushes that are not BV.
public enum BVPushBackground {
    @discardableResult
    public static func didReceiveRemoteNotification(
        _ userInfo: [AnyHashable: Any],
        completion: @escaping (UIBackgroundFetchResult) -> Void
    ) -> Bool {
        guard IOSNotificationHandler.isBV(userInfo) else {
            completion(.noData)
            return false
        }
        guard let facade = BVPush.facade else {
            completion(.noData)
            return true
        }
        let aps = userInfo["aps"] as? [String: Any]
        if aps?["alert"] != nil {
            IOSNotificationHandler.reportShownBySystem(userInfo)
            completion(.newData)
            return true
        }
        let now = Date()
        let clock = Calendar.current.dateComponents([.hour, .minute], from: now)
        let result = facade.handlePush(
            data: IOSNotificationHandler.payload(userInfo),
            nowMillis: Int64(now.timeIntervalSince1970 * 1000),
            nowMinutes: Int32((clock.hour ?? 0) * 60 + (clock.minute ?? 0))
        )
        switch result.decision {
        case "display":
            LocalNotificationPoster.post(result, held: false) { completion(.newData) }
        case "hold":
            LocalNotificationPoster.post(result, held: true) { completion(.newData) }
        default:
            completion(.noData)
        }
        return true
    }
}

/// Posts the SDK's local notification for a data push: now, or held until quiet hours end.
enum LocalNotificationPoster {
    private static let imageTimeout: TimeInterval = 10

    static func post(_ push: IosPushResult, held: Bool, completion: @escaping () -> Void) {
        guard let facade = BVPush.facade, let id = push.notificationId else {
            completion()
            return
        }
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { settings in
            switch settings.authorizationStatus {
            case .authorized, .provisional, .ephemeral:
                break
            default:
                facade.notificationSuppressed(notificationId: id, reason: IOSNotificationHandler.reasonPermission)
                completion()
                return
            }
            let content = UNMutableNotificationContent()
            content.title = push.title ?? ""
            content.body = push.body ?? ""
            content.sound = .default
            content.threadIdentifier = IOSNotificationHandler.threadIdentifier
            var info: [String: Any] = [
                IOSNotificationHandler.sourceKey: IOSNotificationHandler.source,
                IOSNotificationHandler.idKey: id,
                IOSNotificationHandler.localKey: true,
            ]
            if let review = push.reviewUrl { info[IOSNotificationHandler.reviewKey] = review }
            if held { info[IOSNotificationHandler.heldKey] = true }
            content.userInfo = info
            BVPush.rememberReviewLink(push.reviewUrl, for: id)
            attachment(push.imageUrl) { attachment in
                if let attachment { content.attachments = [attachment] }
                let trigger: UNNotificationTrigger? = held
                    ? UNTimeIntervalNotificationTrigger(timeInterval: max(1, TimeInterval(push.holdSeconds)), repeats: false)
                    : nil
                let request = UNNotificationRequest(
                    identifier: IOSNotificationHandler.requestPrefix + id,
                    content: content,
                    trigger: trigger
                )
                center.add(request) { error in
                    if error != nil {
                        facade.notificationSuppressed(notificationId: id, reason: IOSNotificationHandler.reasonPostFailed)
                    } else if held {
                        HeldPushes.add(id)
                    } else {
                        facade.notificationDisplayed(notificationId: id)
                    }
                    completion()
                }
            }
        }
    }

    /// The push image as an attachment (https only, at most 10 seconds), or nil.
    private static func attachment(_ link: String?, completion: @escaping (UNNotificationAttachment?) -> Void) {
        guard let link, let url = URL(string: link), url.scheme?.lowercased() == "https" else {
            completion(nil)
            return
        }
        var request = URLRequest(url: url)
        request.timeoutInterval = imageTimeout
        URLSession.shared.downloadTask(with: request) { location, response, _ in
            guard let location,
                  let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode)
            else {
                completion(nil)
                return
            }
            let ext = url.pathExtension.isEmpty ? "jpg" : url.pathExtension
            let file = FileManager.default.temporaryDirectory
                .appendingPathComponent(UUID().uuidString)
                .appendingPathExtension(ext)
            do {
                try FileManager.default.moveItem(at: location, to: file)
                completion(try UNNotificationAttachment(identifier: "image", url: file, options: nil))
            } catch {
                completion(nil)
            }
        }.resume()
    }
}

/// Held pushes waiting for quiet hours to end. A held push that fires while the app is in
/// the background is reported as shown the next time the app becomes active.
enum HeldPushes {
    private static let key = "bv_push_held_pending"
    private static let lock = NSLock()

    static func add(_ id: String) {
        lock.lock()
        defer { lock.unlock() }
        var ids = UserDefaults.standard.stringArray(forKey: key) ?? []
        if !ids.contains(id) { ids.append(id) }
        UserDefaults.standard.set(Array(ids.suffix(100)), forKey: key)
    }

    /// Reports the held push as shown once; false when it was not pending.
    @discardableResult
    static func shown(_ id: String) -> Bool {
        lock.lock()
        var ids = UserDefaults.standard.stringArray(forKey: key) ?? []
        guard let index = ids.firstIndex(of: id) else {
            lock.unlock()
            return false
        }
        ids.remove(at: index)
        UserDefaults.standard.set(ids, forKey: key)
        lock.unlock()
        BVPush.facade?.notificationDisplayed(notificationId: id)
        return true
    }

    /// Delivered held notifications → shown.
    static func reconcile() {
        guard !(UserDefaults.standard.stringArray(forKey: key) ?? []).isEmpty else { return }
        UNUserNotificationCenter.current().getDeliveredNotifications { delivered in
            for notification in delivered {
                let info = notification.request.content.userInfo
                guard (info[IOSNotificationHandler.heldKey] as? Bool) == true,
                      let id = info[IOSNotificationHandler.idKey] as? String
                else { continue }
                shown(id)
            }
        }
    }
}

/// True the first time a key is seen in this process.
enum Once {
    private static let lock = NSLock()
    private static var seen: Set<String> = []

    static func first(_ key: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return seen.insert(key).inserted
    }
}

/// UNUserNotificationCenter delegate proxy: install with
/// `BVPushUserNotificationCenterProxy.install()`. BV traffic is handled by
/// the SDK; everything else forwards to the previously installed delegate
/// (which may be nil).
public final class BVPushUserNotificationCenterProxy: NSObject, UNUserNotificationCenterDelegate {
    private weak var previous: UNUserNotificationCenterDelegate?

    public static func install() {
        let center = UNUserNotificationCenter.current()
        if center.delegate is BVPushUserNotificationCenterProxy { return }
        let proxy = BVPushUserNotificationCenterProxy()
        proxy.previous = center.delegate
        center.delegate = proxy
        // The notification centre holds its delegate weakly; keep the proxy for the process.
        retained.append(proxy)
    }

    private static var retained: [BVPushUserNotificationCenterProxy] = []

    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        if let options = IOSNotificationHandler.willPresent(notification) {
            completionHandler(options)
            return
        }
        if let previous {
            previous.userNotificationCenter?(center, willPresent: notification, withCompletionHandler: completionHandler)
        } else {
            completionHandler([])
        }
    }

    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if IOSNotificationHandler.didReceive(response) {
            completionHandler()
            return
        }
        if let previous {
            previous.userNotificationCenter?(center, didReceive: response, withCompletionHandler: completionHandler)
        } else {
            completionHandler()
        }
    }
}
