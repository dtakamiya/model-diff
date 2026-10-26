package ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MoneyTest {
    @Test
    void parsesValidAmounts() {
        assertEquals(120000L, Money.parsePositive("1200"));
        assertEquals(120050L, Money.parsePositive("1200.5"));
        assertEquals(120050L, Money.parsePositive("1200.50"));
        assertEquals(1L, Money.parsePositive("0.01"));
    }

    @Test
    void rejectsInvalidAmounts() {
        for (String s : new String[] {"0", "0.00", "-5", "12.345", "abc", "1e3", "1,200", "1200.", ".5", "+5",
                "1234567890123"}) {
            assertNull(Money.parsePositive(s), s);
        }
    }

    @Test
    void formatsWithTwoDecimals() {
        assertEquals("0.00", Money.format(0));
        assertEquals("1200.50", Money.format(120050));
        assertEquals("-500.00", Money.format(-50000));
        assertEquals("-0.05", Money.format(-5));
    }
}
