import BVGarnerKit
import Foundation

/// Version of the BVGarner Swift package: the engine's `SdkInfo.VERSION`, so it is set in one place
/// only. The release tag is the same version.
public enum BVPushVersion {
    public static let version: String = SdkInfo.shared.VERSION
}
