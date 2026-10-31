import Foundation
import SafariServices
import UIKit

/// SFSafariViewController from the top-most view controller once the app is
/// active; `UIApplication.open` fallback.
public enum SafariReviewPresenter {
    @discardableResult
    public static func present(url: URL) -> Bool {
        guard let scene = UIApplication.shared.connectedScenes.first(where: {
            $0.activationState == .foregroundActive
        }) as? UIWindowScene,
            var top = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController
        else {
            UIApplication.shared.open(url)
            return true
        }
        while let presented = top.presentedViewController { top = presented }
        let safari = SFSafariViewController(url: url)
        top.present(safari, animated: true)
        return true
    }
}
