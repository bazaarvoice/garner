import Foundation
import UserNotifications

/// Subclassable NSE base and helper for apps that already have
/// an NSE: returns "not BV" so the host keeps its own logic.
open class BVNotificationServiceExtension: UNNotificationServiceExtension {
    open func bvIsOurs(_ request: UNNotificationRequest) -> Bool {
        (request.content.userInfo["bv_source"] as? String) == "push_sdk"
    }

    override open func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        guard bvIsOurs(request) else {
            contentHandler(request.content)
            return
        }
        contentHandler(bestAttempt(request.content))
    }

    open func bestAttempt(_ content: UNNotificationContent) -> UNNotificationContent {
        content
    }
}

public enum BVNotificationServiceHelper {
    public static func isBV(_ request: UNNotificationRequest) -> Bool {
        (request.content.userInfo["bv_source"] as? String) == "push_sdk"
    }
}
