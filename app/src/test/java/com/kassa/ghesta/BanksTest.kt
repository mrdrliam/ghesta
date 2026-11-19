package com.kassa.ghesta

import com.kassa.ghesta.data.Banks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BanksTest {
    @Test
    fun cardBankFromPrefix() {
        assertEquals("بانک ملی", Banks.cardBank("6037991234567890"))
        assertEquals("بانک ملت", Banks.cardBank("610433"))
        assertNull(Banks.cardBank("60379"))
        assertNull(Banks.cardBank("123456"))
    }

    @Test
    fun luhn() {
        assertTrue(Banks.luhnValid("6037997000000001")) 
        assertFalse(Banks.luhnValid("6037997000000002"))
        assertFalse(Banks.luhnValid("60379970000000"))
    }

    @Test
    fun shebaChecksumAndBank() {
        // Well-known valid example Sheba (bank code 017)
        val ok = "IR062960000000100324200001"
        assertTrue(Banks.shebaValid(ok))
        assertFalse(Banks.shebaValid("IR062960000000100324200002"))
        assertFalse(Banks.shebaValid("062960000000100324200001"))
        assertEquals("بانک ملی", Banks.shebaBank("00017"))
        assertNull(Banks.shebaBank("0001"))
    }
}
