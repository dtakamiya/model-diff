package ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CsvCodecTest {

    @Test
    @DisplayName("カンマと引用符を含むフィールドだけ囲んでエスケープする")
    void quotesOnlyWhenNeeded() {
        List<String> fields = List.of("1", "2026-01-10", "expense", "food", "1200.50", "lunch, with \"Taro\"");
        assertEquals(
                "1,2026-01-10,expense,food,1200.50,\"lunch, with \"\"Taro\"\"\"",
                CsvCodec.encodeLine(fields));
    }

    @Test
    @DisplayName("空のメモは末尾のカンマだけ残す")
    void emptyMemoKeepsTrailingComma() {
        List<String> fields = List.of("2", "2026-01-05", "income", "salary", "300000.00", "");
        assertEquals("2,2026-01-05,income,salary,300000.00,", CsvCodec.encodeLine(fields));
    }

    @Test
    @DisplayName("エンコードした行を分解すると元のフィールドに戻る")
    void roundTrip() {
        List<String> fields = List.of("a", "b,c", "d\"e", "", "f");
        assertEquals(fields, CsvCodec.parseLine(CsvCodec.encodeLine(fields)));
    }

    @Test
    @DisplayName("末尾の空フィールドと引用符付きの空文字を読める")
    void readsEmptyFields() {
        assertEquals(List.of("a", ""), CsvCodec.parseLine("a,"));
        assertEquals(List.of("", "x"), CsvCodec.parseLine("\"\",x"));
    }

    @Test
    @DisplayName("形式が不正な行は DataFileException")
    void malformedLineThrows() {
        assertThrows(DataFileException.class, () -> CsvCodec.parseLine("\"abc"));
        assertThrows(DataFileException.class, () -> CsvCodec.parseLine("\"ab\"c,d"));
        assertThrows(DataFileException.class, () -> CsvCodec.parseLine("ab\"c,d"));
    }
}
