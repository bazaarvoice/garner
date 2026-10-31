package bv.garner.test.app

/** Why the Sign in form cannot be sent yet. */
enum class SignInProblem(val message: String) {
    USER_ID_MISSING(UiText.PROBLEM_USER_ID_MISSING),
    USER_ID_TOO_LONG(UiText.PROBLEM_USER_ID_TOO_LONG),
    EMAIL_MISSING(UiText.PROBLEM_EMAIL_MISSING),
    EMAIL_INVALID(UiText.PROBLEM_EMAIL_INVALID),
    EMAIL_TOO_LONG(UiText.PROBLEM_EMAIL_TOO_LONG),
}

/**
 * Sign in form rules. Both fields are required. The length limits are the
 * SDK's linkIdentity limits, so the SDK never throws for a form that passed.
 */
object SignInForm {
    const val MAX_USER_ID = 255
    const val MAX_EMAIL = 254
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+$")

    fun clean(raw: String): String = raw.trim()

    /** Empty when the cleaned values can be sent; else every problem, user id first. */
    fun problems(userId: String, email: String): List<SignInProblem> {
        val user = clean(userId)
        val address = clean(email)
        val found = mutableListOf<SignInProblem>()
        when {
            user.isEmpty() -> found += SignInProblem.USER_ID_MISSING
            user.length > MAX_USER_ID -> found += SignInProblem.USER_ID_TOO_LONG
        }
        when {
            address.isEmpty() -> found += SignInProblem.EMAIL_MISSING
            address.length > MAX_EMAIL -> found += SignInProblem.EMAIL_TOO_LONG
            !EMAIL.matches(address) -> found += SignInProblem.EMAIL_INVALID
        }
        return found
    }
}
