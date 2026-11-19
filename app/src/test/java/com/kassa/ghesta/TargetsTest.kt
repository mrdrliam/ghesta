package com.kassa.ghesta

import com.kassa.ghesta.data.TargetType
import com.kassa.ghesta.ui.TargetDraft
import com.kassa.ghesta.ui.cleanNumber
import com.kassa.ghesta.ui.error
import com.kassa.ghesta.ui.groupNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetsTest {
    @Test
    fun cleaningKeepsOnlyWhatFitsTheType() {
        assertEquals("6037997000000001", cleanNumber(TargetType.CARD, "۶۰۳۷ ۹۹۷۰-۰۰۰۰ ۰۰۰۱ 99"))
        assertEquals("710560000000100324200001", cleanNumber(TargetType.SHEBA, "IR71 0560 0000 0010 0324 2000 01"))
        assertEquals("8100-123456-7", cleanNumber(TargetType.ACCOUNT, "8100-123456-7 x"))
        assertEquals("1403/123456-01", cleanNumber(TargetType.FACILITY, "۱۴۰۳/۱۲۳۴۵۶-۰۱ abc"))
    }

    @Test
    fun groupingForDisplayAndCopy() {
        assertEquals("6037 9970 0000 0001", groupNumber(TargetType.CARD, "6037997000000001"))
        assertEquals("IR71 0560 0000 0010 0324 2000 01", groupNumber(TargetType.SHEBA, "IR710560000000100324200001"))
        assertEquals("1403/123456", groupNumber(TargetType.FACILITY, "1403/123456"))
    }

    @Test
    fun savingIsBlockedOnlyForWrongLengths() {
        assertNull(TargetDraft(type = TargetType.CARD, number = "").error()) // empty rows are simply dropped
        assertEquals("شماره کارت باید ۱۶ رقم باشد", TargetDraft(type = TargetType.CARD, number = "60379970").error())
        assertNull(TargetDraft(type = TargetType.CARD, number = "6037997000000001").error())
        assertEquals("شماره تسهیلات کوتاه است", TargetDraft(type = TargetType.FACILITY, number = "12").error())
        assertNull(TargetDraft(type = TargetType.FACILITY, number = "1403/12").error())
    }
}
