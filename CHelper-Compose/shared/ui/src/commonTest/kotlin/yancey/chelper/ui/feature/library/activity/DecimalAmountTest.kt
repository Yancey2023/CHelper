package yancey.chelper.ui.feature.library.activity

import kotlin.test.Test
import kotlin.test.assertEquals

class DecimalAmountTest {
    @Test
    fun decimalsKeepTheExistingHalfUpDisplay() {
        assertEquals("1.01", formatDecimalAmount(1.005))
        assertEquals("-1.01", formatDecimalAmount(-1.005))
        assertEquals("2.68", formatDecimalAmount(2.675))
        assertEquals("1.5", formatDecimalAmount(1.5))
        assertEquals("0", formatDecimalAmount(0.0001))
        assertEquals("0.01", formatDecimalAmount(0.005))
        assertEquals("100000000000000000000", formatDecimalAmount(1e20))
    }
}
