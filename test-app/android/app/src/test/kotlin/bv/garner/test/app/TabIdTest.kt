package bv.garner.test.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabIdTest {

    @Test
    fun fromNameFindsEveryTab() {
        TabId.entries.forEach { assertEquals(it, TabId.fromName(it.name)) }
    }

    @Test
    fun fromNameIsExactAndNullSafe() {
        assertNull(TabId.fromName(null))
        assertNull(TabId.fromName(""))
        assertNull(TabId.fromName("inbox"))
        assertNull(TabId.fromName("SETTINGS"))
    }

    @Test
    fun tabsKeepTheirBarOrder() {
        assertEquals(listOf(TabId.HOME, TabId.ACCOUNT, TabId.INBOX, TabId.DETAILS), TabId.entries.toList())
    }
}
