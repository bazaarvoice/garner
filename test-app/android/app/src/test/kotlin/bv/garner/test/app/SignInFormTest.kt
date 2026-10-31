package bv.garner.test.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInFormTest {

    @Test
    fun bothFieldsAreRequired() {
        assertEquals(
            listOf(SignInProblem.USER_ID_MISSING, SignInProblem.EMAIL_MISSING),
            SignInForm.problems("  ", ""),
        )
        assertEquals(listOf(SignInProblem.USER_ID_MISSING), SignInForm.problems("", EMAIL))
        assertEquals(listOf(SignInProblem.EMAIL_MISSING), SignInForm.problems(USER, " "))
    }

    @Test
    fun emailMustLookLikeAnEmail() {
        listOf("tester", "tester@", "@example.com", "a b@example.com", "a@b@c").forEach {
            assertEquals(it, listOf(SignInProblem.EMAIL_INVALID), SignInForm.problems(USER, it))
        }
    }

    @Test
    fun lengthsMatchTheSdkLimits() {
        val longUser = "u".repeat(SignInForm.MAX_USER_ID + 1)
        val longEmail = "e".repeat(SignInForm.MAX_EMAIL - 11) + "@example.com"
        assertEquals(listOf(SignInProblem.USER_ID_TOO_LONG), SignInForm.problems(longUser, EMAIL))
        assertEquals(listOf(SignInProblem.EMAIL_TOO_LONG), SignInForm.problems(USER, longEmail))
        assertTrue(SignInForm.problems("u".repeat(SignInForm.MAX_USER_ID), EMAIL).isEmpty())
    }

    @Test
    fun valuesAreTrimmedBeforeTheyAreChecked() {
        assertTrue(SignInForm.problems("  $USER ", " $EMAIL\n").isEmpty())
        assertEquals(EMAIL, SignInForm.clean(" $EMAIL\n"))
    }

    private companion object {
        const val USER = "tester-001"
        const val EMAIL = "tester.one@example.com"
    }
}
