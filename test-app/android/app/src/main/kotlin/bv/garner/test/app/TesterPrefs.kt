package bv.garner.test.app

import android.content.Context

/**
 * What the tester typed and when, in this app's private preferences. The SDK
 * keeps its own state; these values only fill the screens.
 */
class TesterPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    val userId: String get() = prefs.getString(KEY_USER_ID, null).orEmpty()
    val email: String get() = prefs.getString(KEY_EMAIL, null).orEmpty()

    /** Epoch millis of the last successful sign in on this device; 0 when unknown. */
    val signedInAt: Long get() = prefs.getLong(KEY_SIGNED_IN_AT, 0L)

    /** Epoch millis of the newest push the tester has seen in the Inbox. */
    val inboxSeenAt: Long get() = prefs.getLong(KEY_INBOX_SEEN_AT, 0L)

    fun saveIdentity(userId: String, email: String) {
        prefs.edit().putString(KEY_USER_ID, userId).putString(KEY_EMAIL, email).apply()
    }

    fun markSignedIn(atMillis: Long) {
        prefs.edit().putLong(KEY_SIGNED_IN_AT, atMillis).apply()
    }

    /** The email stays, so the form is filled for the next sign in. */
    fun markSignedOut() {
        prefs.edit().remove(KEY_SIGNED_IN_AT).apply()
    }

    fun markInboxSeen(atMillis: Long) {
        if (atMillis > inboxSeenAt) prefs.edit().putLong(KEY_INBOX_SEEN_AT, atMillis).apply()
    }

    private companion object {
        const val FILE = "garner_test_app"
        const val KEY_USER_ID = "userId"
        const val KEY_EMAIL = "email"
        const val KEY_SIGNED_IN_AT = "signedInAt"
        const val KEY_INBOX_SEEN_AT = "inboxSeenAt"
    }
}
