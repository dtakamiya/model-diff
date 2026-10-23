package ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvCodecTest {
    private static final String SAMPLE = "#next_id=4\n"
            + "1,2026-01-10,expense,food,1200.50,\"lunch, with \"\"Taro\"\"\"\n"
            + "2,2026-01-05,income,salary,300000.00,\n"
            + "3,2026-01-10,expense,transport,300.00,train\n";

    @Test
    void decodesSpecExample() {
        Ledger ledger = CsvCodec.decode(SAMPLE);
        assertEquals(4, ledger.nextId());
        assertEquals(3, ledger.entries().size());
        assertEquals("lunch, with \"Taro\"", ledger.entries().get(0).memo());
        assertEquals("", ledger.entries().get(1).memo());
    }

    @Test
    void roundTripsExactly() {
        assertEquals(SAMPLE, CsvCodec.encode(CsvCodec.decode(SAMPLE)));
    }

    @Test
    void emptyTextIsEmptyLedger() {
        assertEquals(1, CsvCodec.decode("").nextId());
    }

    @Test
    void keepsNextIdAfterDeletingAll() {
        Ledger ledger = new Ledger(List.of(), 1);
        long id = ledger.add(LocalDate.of(2026, 1, 1), EntryType.INCOME, "a", 100, "");
        ledger.delete(id);
        assertEquals("#next_id=2\n", CsvCodec.encode(ledger));
    }

    @Test
    void rejectsBrokenData() {
        for (String s : new String[] {"garbage\n", "#next_id=2\n1,2026-01-01,income\n",
                "#next_id=2\n1,2026-02-30,income,a,1.00,\n", "#next_id=2\n1,2026-01-01,income,a,1,\n",
                "#next_id=2\n1,2026-01-01,income,\"a,1.00,\n", "#next_id=1\n1,2026-01-01,income,a,1.00,\n"}) {
            assertThrows(IllegalArgumentException.class, () -> CsvCodec.decode(s), s);
        }
    }
}
