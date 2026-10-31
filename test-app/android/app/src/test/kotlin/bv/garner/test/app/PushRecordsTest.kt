package bv.garner.test.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushRecordsTest {

    @Test
    fun groupsByIdNewestFirstWithStepsInTimeOrder() {
        val records = PushRecords.group(
            listOf(
                PushEntry(A, PushStep.RECEIVED, 100),
                PushEntry(B, PushStep.RECEIVED, 200),
                PushEntry(A, PushStep.TAPPED, 400),
                PushEntry(A, PushStep.SHOWN, 150),
                PushContent(A, TITLE, BODY),
            )
        )
        assertEquals(listOf(B, A), records.map { it.id })
        val a = records[1]
        assertEquals(100L, a.firstAtMillis)
        assertEquals(listOf(PushStep.RECEIVED, PushStep.SHOWN, PushStep.TAPPED), a.steps.map { it.step })
        assertEquals(PushContent(A, TITLE, BODY), a.content)
        assertNull(records[0].content)
    }

    @Test
    fun contentWithoutAnyEventIsDropped() {
        assertTrue(PushRecords.group(listOf(PushContent(A, TITLE, BODY))).isEmpty())
    }

    @Test
    fun latestOutcomeWins() {
        assertEquals(PushStatus.SHOWN, status(PushStep.RECEIVED, PushStep.HELD, PushStep.SHOWN))
        assertEquals(PushStatus.HELD, status(PushStep.RECEIVED, PushStep.HELD))
        assertEquals(PushStatus.HIDDEN, status(PushStep.RECEIVED, PushStep.HELD, PushStep.HIDDEN))
        assertEquals(PushStatus.HIDDEN, status(PushStep.RECEIVED, PushStep.HIDDEN))
        assertEquals(PushStatus.RECEIVED, status(PushStep.RECEIVED))
        assertEquals(PushStatus.SHOWN, status(PushStep.TAPPED))
    }

    @Test
    fun filtersMatchStatusAndAllMatchesEverything() {
        val received = record(PushStep.RECEIVED)
        val held = record(PushStep.RECEIVED, PushStep.HELD)
        assertTrue(PushFilter.ALL.matches(received))
        assertTrue(PushFilter.HELD.matches(held))
        assertEquals(false, PushFilter.SHOWN.matches(held))
        assertEquals(false, PushFilter.HIDDEN.matches(received))
        val records = listOf(received, held, record(PushStep.SHOWN))
        assertEquals(3, PushRecords.count(records, PushFilter.ALL))
        assertEquals(1, PushRecords.count(records, PushFilter.HELD))
        assertEquals(1, PushRecords.count(records, PushFilter.SHOWN))
        assertEquals(0, PushRecords.count(records, PushFilter.HIDDEN))
    }

    @Test
    fun limitKeepsTheNewestRecordsInOriginalOrder() {
        val lines = listOf(
            PushEntry(A, PushStep.RECEIVED, 100),
            PushContent(A, TITLE, BODY),
            PushEntry(B, PushStep.RECEIVED, 200),
            PushEntry(C, PushStep.RECEIVED, 300),
            PushEntry(B, PushStep.SHOWN, 350),
        )
        assertEquals(
            listOf(
                PushEntry(B, PushStep.RECEIVED, 200),
                PushEntry(C, PushStep.RECEIVED, 300),
                PushEntry(B, PushStep.SHOWN, 350),
            ),
            PushRecords.limit(lines, max = 2),
        )
        assertEquals(lines, PushRecords.limit(lines))
    }

    @Test
    fun unseenCountsRecordsReceivedAfterTheLastLook() {
        val records = PushRecords.group(
            listOf(
                PushEntry(A, PushStep.RECEIVED, 100),
                PushEntry(B, PushStep.RECEIVED, 200),
                PushEntry(C, PushStep.RECEIVED, 300),
            )
        )
        assertEquals(3, PushRecords.unseen(records, 0))
        assertEquals(1, PushRecords.unseen(records, 200))
        assertEquals(0, PushRecords.unseen(records, 300))
    }

    private fun record(vararg steps: PushStep): PushRecord =
        PushRecords.group(steps.mapIndexed { i, step -> PushEntry(A, step, 100L + i) }).single()

    private fun status(vararg steps: PushStep): PushStatus = record(*steps).status

    private companion object {
        const val A = "id-a"
        const val B = "id-b"
        const val C = "id-c"
        const val TITLE = "How was your order?"
        const val BODY = "Tell other shoppers what you think."
    }
}
