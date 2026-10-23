package ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvCodecTest {
    @Test
    void roundTripWithQuotes() {
        Entry e = new Entry(1, LocalDate.of(2026, 1, 10), EntryType.EXPENSE, "a,\"b\"",
                new BigDecimal("12.50"), "x, \"y\"");
        LedgerData data = new LedgerData(5, List.of(e));
        String text = CsvCodec.serialize(data);
        assertEquals("#next_id=5\n1,2026-01-10,expense,\"a,\"\"b\"\"\",12.50,\"x, \"\"y\"\"\"\n", text);
        LedgerData back = CsvCodec.parse(text);
        assertEquals(5, back.nextId());
        assertEquals(List.of(e), back.entries());
    }

    @Test
    void emptyIsEmptyData() {
        assertEquals(1, CsvCodec.parse("").nextId());
    }

    @Test
    void corruptInputsAreIoErrors() {
        String[] bad = {
            "1,2026-01-01,income,a,1.00,\n",
            "#next_id=0\n",
            "#next_id=2\n1,2026-01-01,income,a,1.00\n",
            "#next_id=2\n1,2026-01-01,income,\"a,1.00,\n",
            "#next_id=2\n1,2026-01-01,income,a\"b,1.00,\n",
            "#next_id=2\n1,2026-02-30,income,a,1.00,\n",
            "#next_id=2\n2,2026-01-01,income,a,1.00,\n",
        };
        for (String s : bad) {
            LedgerException ex = assertThrows(LedgerException.class, () -> CsvCodec.parse(s), s);
            assertEquals(1, ex.exitCode());
        }
    }
}
