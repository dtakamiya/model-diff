package ledger.storage;

import ledger.domain.Entry;
import ledger.domain.EntryType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CsvCodecTest {

    @Test
    void カンマとダブルクォートを含む欄はクォートされる() {
        assertEquals("\"lunch, with \"\"Taro\"\"\"", CsvCodec.quote("lunch, with \"Taro\""));
        assertEquals("train", CsvCodec.quote("train"));
    }

    @Test
    void 記録はCSV行に変換でき元に戻せる() {
        Entry entry = new Entry(1, LocalDate.of(2026, 1, 10), EntryType.EXPENSE, "food", 120050,
                "lunch, with \"Taro\"");

        String line = CsvCodec.encode(entry);

        assertEquals("1,2026-01-10,expense,food,1200.50,\"lunch, with \"\"Taro\"\"\"", line);
        assertEquals(entry, CsvCodec.decode(line).orElseThrow());
    }

    @Test
    void 空のメモは末尾の空欄として読める() {
        List<String> fields = CsvCodec.splitFields("2,2026-01-05,income,salary,300000.00,").orElseThrow();

        assertEquals(6, fields.size());
        assertEquals("", fields.get(5));
    }

    @Test
    void 閉じクォートの無い行や余計な文字のある行は不正() {
        assertFalse(CsvCodec.splitFields("1,\"abc").isPresent());
        assertFalse(CsvCodec.splitFields("1,\"abc\"x,2").isPresent());
        assertFalse(CsvCodec.splitFields("1,ab\"c,2").isPresent());
    }

    @Test
    void 欄数や値が不正な行は復元できない() {
        assertFalse(CsvCodec.decode("1,2026-01-10,expense,food,1200.50").isPresent());
        assertFalse(CsvCodec.decode("1,2026-02-30,expense,food,1200.50,").isPresent());
        assertFalse(CsvCodec.decode("1,2026-01-10,expense,,1200.50,").isPresent());
    }
}
