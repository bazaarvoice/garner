import Foundation

/// Every text the tester sees, in one place. Simple English; the backend is
/// "Garner Backend" in every message. Same texts as the Android test app, except where iOS differs
/// (APNs instead of Google Play services, Settings path, Bundle ID).
enum UiText {
    // Tabs and navigation.
    static let TAB_HOME = "Home"
    static let TAB_ACCOUNT = "Account"
    static let TAB_INBOX = "Inbox"
    static let TAB_DETAILS = "Details"
    static let ICON_HOME = "house" // SF Symbol
    static let ICON_ACCOUNT = "person" // SF Symbol
    static let ICON_INBOX = "bell" // SF Symbol
    static let ICON_DETAILS = "info.circle" // SF Symbol
    static let BADGE_MAX = "99+"
    // Top bar chips.
    static let CHIP_APP_ID = "App-ID "
    static let CHIP_SDK = "SDK "
    static let SIGNED_IN = "Signed in"
    static let SIGNED_OUT = "Signed out"
    static let SIGNING_IN = "Signing in…"
    static let SIGNING_OUT = "Signing out…"
    static let DETAILS_CHIP = "Tap Copy next to any value"
    static func inboxChip(_ count: Int) -> String { "\(count) received · kept on this device" }
    // Environments.
    static let ENV_LOCAL = "Local"
    static let ENV_QA = "QA"
    static let ENV_STAGING = "Staging"
    static let ENV_PRODUCTION = "Production"
    // Common words.
    static let COPY = "Copy"
    static let CANCEL = "Cancel"
    static let SAVE = "Save"
    static let SHOW = "Show"
    static let HIDE = "Hide"
    static let NONE = "none"
    static let UNKNOWN = "unknown"
    static let YES = "yes"
    static let NO = "no"
    static let YESTERDAY = "Yesterday"
    static let ELLIPSIS = "…"
    static func copied(_ label: String) -> String { "\(label) copied" }
    // Home: banner.
    static func bannerReady(_ total: Int) -> String { "Ready: all \(total) checks pass" }
    static func bannerAction(_ passed: Int, _ total: Int) -> String { "Almost ready: \(passed) of \(total) checks pass" }
    static func bannerChecking(_ passed: Int, _ total: Int) -> String { "Checking: \(passed) of \(total) checks pass" }
    static let BANNER_READY_DETAIL = "Real and test pushes can show on this device."
    static let BANNER_ACTION_DETAIL = "Fix the items marked below to get real pushes."
    static let BANNER_CHECKING_DETAIL = "The SDK is still starting. This takes a few seconds."
    // Home: checklist.
    static let CHECKLIST = "Checklist"
    static let ACTION_SIGN_IN = "Sign in"
    static let ACTION_ALLOW = "Allow"
    static let ACTION_CHANGE = "Change"
    static let REGISTERED_OK = "Device registered"
    static func registeredDetail(_ deviceId: String) -> String { "Device ID \(deviceId)" }
    static let REGISTERED_WAIT = "Registering…"
    static let REGISTERED_WAIT_DETAIL = "Starts by itself when the app opens"
    static let REGISTERED_SLOW_DETAIL = "Still waiting: check the internet, then Details"
    static let REGISTERED_BAD = "Not registered"
    static let REGISTERED_BAD_DETAIL = "No device ID. See Last error in Details"
    static let TOKEN_OK = "Push token sent"
    static let TOKEN_OK_DETAIL = "FCM token synced with Garner Backend"
    static let TOKEN_WAIT = "Sending push token…"
    static let TOKEN_WAIT_DETAIL = "Needs APNs, Firebase and internet"
    static let TOKEN_BAD = "Push token not sent"
    static let TOKEN_BAD_DETAIL = "Check GoogleService-Info.plist and the APNs key in Firebase"
    static let SIGNED_IN_OK = "User signed in"
    static let SIGNED_IN_OK_DETAIL = "Session active"
    static let SIGNED_IN_WAIT = "User sign-in"
    static let SIGNED_IN_WAIT_DETAIL = "Checked once the device is registered"
    static let SIGNED_IN_BAD = "No user signed in"
    static let SIGNED_IN_BAD_DETAIL = "Real pushes are hidden. Test pushes still show"
    static let NOTIFICATIONS_OK = "Notifications allowed"
    static let NOTIFICATIONS_OK_DETAIL = "Pushes can show on this device"
    static let NOTIFICATIONS_BAD = "Notifications blocked"
    static let NOTIFICATIONS_BAD_DETAIL = "Pushes cannot show until you allow them"
    static let QUIET_OFF_TITLE = "Quiet hours off"
    static let QUIET_OFF_DETAIL = "Pushes show at any time"
    static func quietLaterTitle(_ range: String) -> String { "Quiet hours \(range)" }
    static let QUIET_LATER_DETAIL = "Not now. Pushes in that window are held"
    static let QUIET_NOW_TITLE = "Quiet hours in effect"
    static func quietNowDetail(_ end: String) -> String { "Pushes are held until \(end)" }
    static let QUIET_UNKNOWN_TITLE = "Quiet hours"
    static let QUIET_UNKNOWN_DETAIL = "Checked once the SDK is ready"
    // Home: test push card.
    static let TEST_PUSH_TITLE = "Send a test push"
    static let TEST_PUSH_STEPS = "1. Copy the device ID.\n" + "2. In Portal, open your push sender profile, then Send test push.\n" + "3. Watch the Inbox tab."
    static let COPY_DEVICE_ID = "Copy device ID"
    static let COPY_DEVICE_ID_WAITING = "Copy device ID (after registration)"
    static let DEVICE_ID = "Device ID"
    // Account: sign in.
    static let SIGN_IN_TITLE = "Sign in"
    static let SIGN_IN_HELP = "Enter the user id and the consumer email the review request goes to. " + "Real pushes for this email then reach this device."
    static let LABEL_USER_ID = "User id"
    static let LABEL_EMAIL = "Email"
    static let HINT_USER_ID = "For example tester-001"
    static let HINT_EMAIL = "name@example.com"
    static let SIGN_IN_BUTTON = "Sign in"
    static let SIGN_IN_NOTE = "The SDK sends the email to Garner Backend once and keeps only its digest."
    static let NEXT_TITLE = "What happens next"
    static let NEXT_STEPS = "1. The SDK sends the email to Garner Backend once.\n" + "2. Garner Backend links this device to the email.\n" + "3. User signed in on Home turns ✓.\n" + "4. Real pushes for this email now show here and in Inbox."
    static let PROBLEM_USER_ID_MISSING = "Enter a user id."
    static let PROBLEM_EMAIL_MISSING = "Enter an email."
    static let PROBLEM_EMAIL_INVALID = "Enter a valid email, like name@example.com."
    static let PROBLEM_USER_ID_TOO_LONG = "The user id is longer than 255 characters."
    static let PROBLEM_EMAIL_TOO_LONG = "The email is longer than 254 characters."
    static func signingIn(_ email: String) -> String { "Signing in \(email)… This screen changes when it is done." }
    static let SIGNING_IN_LATER = " It runs once the device is registered."
    static let SIGN_IN_TIMEOUT = "No session after 30 s. Check Recent SDK events and Last error in Details."
    static let SIGN_IN_WAIT_REGISTER = "Waiting for device registration. The SDK keeps the user and signs in by " + "itself once Device registered on Home turns ✓."
    static let SIGN_IN_WAIT_TOKEN = "Waiting for the push token. The SDK keeps the user and signs in by itself " + "once Push token sent on Home turns ✓."
    static func signInWaitOther(_ reason: String) -> String { "Waiting (\(reason)). The SDK keeps the user and signs in by itself later." }
    static func signInFailed(_ message: String?) -> String { "Sign in failed: \(message ?? UNKNOWN)" }
    // Account: signed in.
    static let SIGNED_IN_USER_UNKNOWN = "Signed in user"
    static func userIdLine(_ userId: String) -> String { "User id \(userId)" }
    static func signedInLine(_ time: String) -> String { "Signed in \(time) on this device" }
    static let SIGNED_IN_LINE_NO_TIME = "Signed in on this device"
    static let SESSION_TITLE = "Session"
    static let ROW_IDENTITY = "Identity"
    static let ROW_SESSION_UNTIL = "Session valid until"
    static let ROW_SIGNED_IN_AT = "Signed in at"
    static let SIGN_OUT_HELP = "Sign out unlinks this device from the email. Real pushes are hidden after that."
    static let SIGN_OUT = "Sign out"
    static let SIGN_OUT_TIMEOUT = "Still signed in after 30 s. Check Recent SDK events in Details."
    static let SIGN_OUT_DONE = "Signed out: real pushes are now hidden."
    // Account: preferences.
    static let PREFS_TITLE = "Preferences"
    static let RECEIVE_PUSHES = "Receive pushes"
    static let RECEIVE_PUSHES_HELP = "Off = opt out. Garner Backend stops sending."
    static func serverOptLine(_ serverOptedOut: Bool?) -> String {
        switch serverOptedOut {
        case true?: return "Garner Backend: opted out"
        case false?: return "Garner Backend: receiving"
        case nil: return "Garner Backend: not confirmed yet"
        }
    }
    static let QUIET_TITLE = "Quiet hours"
    static let QUIET_HELP = "Pushes that arrive in this window are held and shown when it ends."
    static let QUIET_OFF = "Off"
    static let QUIET_DEFAULT = "Default"
    static let QUIET_SET = "Set hours"
    static let QUIET_START_TITLE = "Quiet hours start"
    static let QUIET_END_TITLE = "Quiet hours end"
    static let QUIET_DEFAULT_SUFFIX = " (default)"
    static let QUIET_SUMMARY_OFF = "Off: pushes show at any time"
    static func quietSummaryNow(_ range: String) -> String { "\(range), in effect now: pushes are held" }
    static func quietSummaryLater(_ range: String) -> String { "\(range): pushes in that window are held" }
    static let QUIET_SUMMARY_UNKNOWN = "Shown once the SDK is ready"
    static let QUIET_NOT_READY = "The SDK is not ready yet. Wait for Device registered on Home, then try again."
    static let QUIET_SAVED = "Saved."
    // Inbox.
    static let FILTER_ALL = "All"
    static let FILTER_SHOWN = "Shown"
    static let FILTER_HELD = "Held"
    static let FILTER_HIDDEN = "Hidden"
    static func filterChip(_ label: String, _ count: Int) -> String { "\(label) \(count)" }
    static let CLEAR = "Clear"
    static let CLEAR_TITLE = "Clear inbox?"
    static func clearMessage(_ count: Int) -> String { "Removes the \(count) pushes kept on this device. The SDK and Garner Backend are not changed." }
    static let INBOX_EMPTY = "No pushes yet. Send a test push from Portal (see Home)."
    static let INBOX_EMPTY_FILTER = "No pushes match this filter."
    static let NOTIFICATION_ID = "Notification id"
    static let ID_PREFIX = "id "
    static let STEP_RECEIVED_ONLY = "Received, no result yet"
    static let STEP_SHOWN = "Shown"
    static func stepShownAt(_ time: String) -> String { "Shown \(time)" }
    static let STEP_TAPPED = "Tapped"
    static let STEP_REVIEW = "Review opened"
    static let STEP_TAPPED_REVIEW = "Tapped → review opened"
    static let STEP_HELD = "Held"
    static func stepHeldUntil(_ time: String) -> String { "Held until \(time)" }
    static let HIDDEN_LOGGED_OUT = "Hidden: no user signed in"
    static let HIDDEN_OPTED_OUT = "Hidden: opted out"
    static let HIDDEN_PERMISSION = "Hidden: notifications blocked"
    static let HIDDEN_CHANNEL = "Hidden: notifications off in Settings"
    static func hiddenOther(_ reason: String) -> String { "Hidden: \(reason)" }
    static let BODY_HIDDEN = "Not shown, so no title is known."
    static let BODY_HELD = "Held for quiet hours. The title shows here once it is posted."
    static let BODY_RECEIVED = "Received. Waiting for the SDK result."
    static let BODY_SHOWN_NO_TEXT = "Shown. The app could not read its title."
    // Details.
    static let SECTION_DEVICE = "DEVICE"
    static let SECTION_APP = "APP"
    static let SECTION_USER = "USER AND DELIVERY"
    static let FIELD_DEVICE_ID = "Device ID"
    static let FIELD_FCM_TOKEN = "FCM token"
    static let FIELD_APNS_TOKEN = "APNs token"
    static let FIELD_PERMISSION = "Permission"
    static let FIELD_REGISTERED = "Registered"
    static let FIELD_TOKEN_SYNCED = "Token synced"
    static let FIELD_FCM_CONFLICT = "FCM conflict"
    static let FIELD_APP_NAME = "App name"
    static let FIELD_APP_ID = "App ID"
    static let FIELD_PACKAGE = "Bundle ID"
    static let FIELD_ENDPOINT = "Endpoint"
    static let FIELD_ENVIRONMENT = "Environment"
    static let FIELD_SDK_VERSION = "SDK version"
    static let FIELD_USER = "User ID"
    static let FIELD_EMAIL = "Email"
    static let FIELD_IDENTITY = "Identity"
    static let FIELD_SESSION = "Session"
    static let FIELD_SESSION_UNTIL = "Session valid until"
    static let FIELD_OPTED_OUT = "Opted out"
    static let FIELD_QUIET_HOURS = "Quiet hours"
    static let FIELD_LAST_ERROR = "Last error"
    static let FIELD_CORRELATION_ID = "Correlation id"
    static func optedOutValue(_ desired: Bool, _ server: Bool?) -> String { "\(desired) / server \(server.map { String($0) } ?? UNKNOWN)" }
    static let QUIET_VALUE_OFF = "off"
    static let COPY_ALL = "Copy all (JSON)"
    static let SHARE = "Share"
    static let SHARE_TITLE = "Share BV Garner details"
    static let CLIP_DETAILS = "BV Garner details"
    static let EVENTS_TITLE = "Recent SDK events"
    static let EVENTS_HELP = "What the SDK did, newest first, while this app runs."
    static let EVENTS_EMPTY = "Nothing yet. Send a test push from Portal."
    static let RAW_TITLE = "Raw inspector"
    static let RAW_HELP = "Raw SDK state for support."
    // Recent SDK events.
    static let EVENT_RECEIVED = "Push received"
    static let EVENT_SHOWN = "Shown on screen"
    static func eventHeld(_ time: String) -> String { "Held for quiet hours (shows at \(time))" }
    static func eventHidden(_ reason: String, _ hint: String) -> String { "Hidden: \(reason)\(hint)" }
    static let EVENT_TAPPED = "Tapped"
    static func eventReview(_ presenter: String) -> String { "Review page opened (\(presenter))" }
    static func eventError(_ kind: String) -> String { "Error: \(kind)" }
    static func eventBlocked(_ reason: String) -> String { "Registration blocked: \(reason)" }
    static func eventLoginWait(_ reason: String) -> String { "Sign-in waiting: \(reason)" }
    static let EVENT_REGISTERED = "Registered"
    static let EVENT_TOKEN = "Push token sent"
    static let EVENT_LINKED = "User linked"
    static let EVENT_SIGNED_IN = "Signed in"
    static let EVENT_REFRESHED = "Session refreshed"
    static let EVENT_SIGNED_OUT = "Signed out"
    static let EVENT_UNLINKED = "User unlinked"
    static let EVENT_OPTED_OUT = "Opted out of pushes"
    static let EVENT_OPTED_IN = "Opted in to pushes"
    static let HINT_LOGGED_OUT = " (no user signed in: see Account)"
    static let HINT_PERMISSION = " (notifications blocked: see Home)"
    static let HINT_CHANNEL = " (notifications for this app are off in Settings)"
    static let HINT_OPTED_OUT = " (this device opted out: see Account)"
    // System settings.
    static let SETTINGS_OPENED = "Turn notifications on, then come back here"
    static let SETTINGS_MANUAL = "Open Settings → BV Garner → Notifications"
}
