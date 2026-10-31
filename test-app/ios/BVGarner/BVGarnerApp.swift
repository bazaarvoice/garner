import BVGarner
import BVGarnerFirebase
import FirebaseCore
import FirebaseMessaging
import SwiftUI
import UIKit
import UserNotifications

@main
struct BVGarnerApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            Shell(model: model).onAppear { model.start() }
        }
    }
}

/// Wires the SDK like an integrating app would: notification delegate proxy, APNs and FCM
/// tokens, background pushes. Firebase swizzling is off (`FirebaseAppDelegateProxyEnabled = NO`).
final class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Subscribe before configure, so the first events reach the Inbox and Details.
        ActivityLog.shared.start()
        PushHistory.shared.start()
        BVPushUserNotificationCenterProxy.install()
        if AppConfig.hasFirebaseConfig {
            FirebaseApp.configure()
            Messaging.messaging().delegate = self
        } else {
            NSLog("BVGarner: no GoogleService-Info.plist, so no FCM token (see test-app/ios/NEXT-STEPS.md)")
        }
        guard !AppConfig.isRunningTests else { return true }
        #if DEBUG
        let driven = Driver.start()
        #else
        let driven = false
        #endif
        if !driven { AppConfig.startSDK() }
        askForNotifications()
        return true
    }

    /// Like Android 13+, ask on first start. CI screenshots ask quietly (provisional, no prompt).
    private func askForNotifications() {
        #if DEBUG
        if Driver.screenshots {
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .provisional]) { _, _ in
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                    BVPush.refreshNotificationPermission()
                }
            }
            return
        }
        #endif
        BVPush.requestNotificationAuthorization()
        UIApplication.shared.registerForRemoteNotifications()
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        if AppConfig.hasFirebaseConfig { Messaging.messaging().apnsToken = deviceToken }
        BVPushFirebase.didRegisterForRemoteNotifications(deviceToken: deviceToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        NSLog("BVGarner: APNs registration failed: \(error.localizedDescription)")
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        if AppConfig.hasFirebaseConfig { Messaging.messaging().appDidReceiveMessage(userInfo) }
        // Completes with .noData for pushes that are not from BV.
        BVPushBackground.didReceiveRemoteNotification(userInfo, completion: completionHandler)
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        BVPushFirebase.didReceiveRegistrationToken(fcmToken)
    }
}
