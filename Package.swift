// swift-tools-version: 5.9
import PackageDescription

// Bazaarvoice Garner SDK for iOS. Add this repository as a Swift package dependency:
//
//     .package(url: "https://github.com/bazaarvoice/garner.git", from: "1.1.0")
//
// and link the `BVGarner` product (plus `BVGarnerFirebase` / `BVGarnerNotificationService`
// when you want them). The SDK holds no app ids, keys or project numbers: the app
// passes them to `BVPush.configure(appId:options:)`.
//
// BVGarnerKit is the SDK engine (bv-push-sdk/sdk, Kotlin) as an XCFramework, attached to the
// GitHub release of this version. To run your own engine build instead, run
// `./gradlew :sdk:assembleBVGarnerKitDebugXCFramework` in bv-push-sdk/ and replace `url:` and
// `checksum:` below with
// `path: "bv-push-sdk/sdk/build/XCFrameworks/debug/BVGarnerKit.xcframework"`.
let package = Package(
    // Not "BVGarner": Xcode names build folders <project>.build/<target>.build, so an app
    // project and app target both called BVGarner would share a folder with this package's
    // BVGarner target. Apps refer to the package by its URL, not by this name.
    name: "Garner",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "BVGarner", targets: ["BVGarner"]),
        .library(name: "BVGarnerFirebase", targets: ["BVGarnerFirebase"]),
        .library(name: "BVGarnerNotificationService", targets: ["BVGarnerNotificationService"]),
    ],
    targets: [
        .binaryTarget(
            name: "BVGarnerKit",
            url: "https://github.com/bazaarvoice/garner/releases/download/1.1.0/BVGarnerKit.xcframework.zip",
            checksum: "5101dca5517e48e37edb3769683e25ff0ae96f0710e9585a58a46765c365c6df"
        ),
        .target(
            name: "BVGarner",
            dependencies: ["BVGarnerKit"],
            path: "bv-push-sdk/ios/Sources/BVGarner",
            resources: [.copy("PrivacyInfo.xcprivacy")]
        ),
        .target(
            name: "BVGarnerFirebase",
            dependencies: ["BVGarner"],
            path: "bv-push-sdk/ios/Sources/BVGarnerFirebase"
        ),
        .target(
            name: "BVGarnerNotificationService",
            dependencies: ["BVGarner"],
            path: "bv-push-sdk/ios/Sources/BVGarnerNotificationService"
        ),
    ]
)
