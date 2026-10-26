package ledger.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({
            "1200, 120000",
            "1200.5, 120050",
            "1200.50, 120050",
            "0.10, 10",
            "0.5, 50",
    })
    void 正の金額を銭に変換する(String text, long cents) {
        assertEquals(cents, Money.parseCents(text).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5", "12.345", "abc", "1e3", "1,200", "1200.", ".5", "+5", "1234567890123", ""})
    void 不正な金額は空になる(String text) {
        assertFalse(Money.parseCents(text).isPresent());
    }

    @Test
    void 書式は小数2桁で3桁区切りなし() {
        assertEquals("1200.50", Money.format(120050));
        assertEquals("0.30", Money.format(30));
        assertEquals("298499.50", Money.format(29849950));
        assertEquals("-500.00", Money.format(-50000));
    }
}
