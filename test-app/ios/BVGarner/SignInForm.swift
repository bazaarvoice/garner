import Foundation

/// Why the Sign in form cannot be sent yet.
enum SignInProblem: CaseIterable {
    case userIdMissing, userIdTooLong, emailMissing, emailInvalid, emailTooLong

    var message: String {
        switch self {
        case .userIdMissing: return UiText.PROBLEM_USER_ID_MISSING
        case .userIdTooLong: return UiText.PROBLEM_USER_ID_TOO_LONG
        case .emailMissing: return UiText.PROBLEM_EMAIL_MISSING
        case .emailInvalid: return UiText.PROBLEM_EMAIL_INVALID
        case .emailTooLong: return UiText.PROBLEM_EMAIL_TOO_LONG
        }
    }
}

/// Sign in form rules. Both fields are required. The length limits are the SDK's
/// linkIdentity limits, so the SDK never rejects a form that passed.
enum SignInForm {
    static let maxUserId = 255
    static let maxEmail = 254

    static func clean(_ raw: String) -> String { raw.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// Empty when the cleaned values can be sent; else every problem, user id first.
    static func problems(userId: String, email: String) -> [SignInProblem] {
        let user = clean(userId)
        let address = clean(email)
        var found: [SignInProblem] = []
        if user.isEmpty {
            found.append(.userIdMissing)
        } else if user.count > maxUserId {
            found.append(.userIdTooLong)
        }
        if address.isEmpty {
            found.append(.emailMissing)
        } else if address.count > maxEmail {
            found.append(.emailTooLong)
        } else if !isEmail(address) {
            found.append(.emailInvalid)
        }
        return found
    }

    /// `^[^@\s]+@[^@\s]+$`
    private static func isEmail(_ text: String) -> Bool {
        text.range(of: #"^[^@\s]+@[^@\s]+$"#, options: .regularExpression) != nil
    }
}
