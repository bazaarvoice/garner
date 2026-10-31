import BVGarner
import Foundation

/// Glue for apps that use FirebaseMessaging with swizzling off
/// (`FirebaseAppDelegateProxyEnabled = NO`). The app keeps `Messaging.delegate`
/// and forwards here; this target does not link Firebase itself.
///
///     func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
///         BVPushFirebase.didReceiveRegistrationToken(fcmToken)
///     }
///     func application(_ app: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken token: Data) {
///         Messaging.messaging().apnsToken = token
///         BVPushFirebase.didRegisterForRemoteNotifications(deviceToken: token)
///     }
public enum BVPushFirebase {
    /// The FCM registration token, new or refreshed.
    public static func didReceiveRegistrationToken(_ token: String?) {
        BVPush.setFCMToken(token)
    }

    public static func setAPNSToken(_ deviceToken: Data) {
        BVPush.setAPNSToken(deviceToken)
    }

    public static func didRegisterForRemoteNotifications(deviceToken: Data) {
        setAPNSToken(deviceToken)
    }
}
