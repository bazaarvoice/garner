package bv.garner.test.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PushLogCodecTest {

    @Test
    fun entryRoundTripsWithEveryStep() {
        PushStep.entries.forEach { step ->
            val entry = PushEntry(ID, step, AT, DETAIL)
            assertEquals(entry, PushLogCodec.decode(PushLogCodec.encode(entry)))
        }
    }

    @Test
    fun contentRoundTripsWithTabsNewlinesAndBackslashes() {
        val content = PushContent(ID, "Rate\tyour \\order\\", "Line one\nLine two\r\nend\\")
        val line = PushLogCodec.encode(content)
        assertFalse(line.contains('\n'))
        assertEquals(content, PushLogCodec.decode(line))
    }

    @Test
    fun emptyDetailAndBodyRoundTrip() {
        val entry = PushEntry(ID, PushStep.SHOWN, AT)
        val content = PushContent(ID, "", "")
        assertEquals(entry, PushLogCodec.decode(PushLogCodec.encode(entry)))
        assertEquals(content, PushLogCodec.decode(PushLogCodec.encode(content)))
    }

    @Test
    fun brokenLinesDecodeToNull() {
        val good = PushLogCodec.encode(PushEntry(ID, PushStep.HELD, AT, DETAIL))
        assertNull(PushLogCodec.decode(""))
        assertNull(PushLogCodec.decode(good.replaceFirst("v1", "v2")))
        assertNull(PushLogCodec.decode(good.replace("\theld\t", "\tlater\t")))
        assertNull(PushLogCodec.decode(good.replace("\t$AT\t", "\tsoon\t")))
        assertNull(PushLogCodec.decode(good + "\textra"))
        assertNull(PushLogCodec.decode(good.replace("\t$ID\t", "\t\t")))
        assertNull(PushLogCodec.decode("v1\tX\t$ID\ta\tb"))
    }

    private companion object {
        const val ID = "7c41aa00-1111-2222-3333-44445555e2a0"
        const val AT = 1_761_300_000_000L
        const val DETAIL = "logged-out"
    }
}
