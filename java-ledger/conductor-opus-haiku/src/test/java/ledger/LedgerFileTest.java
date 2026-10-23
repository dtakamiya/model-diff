package ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** LedgerFile（CSV の読み書き）のテスト。 */
class LedgerFileTest {

    @TempDir
    Path tmp;

    private static Entry entry(long id, String date, String type, String category, String amount, String memo) {
        return new Entry(id, LocalDate.parse(date), type, category, new BigDecimal(amount), memo);
    }

    @Test
    void quoteOnlyWhenCommaOrDoubleQuoteIsPresent() {
        assertEquals("food", LedgerFile.quote("food"));
        assertEquals("\"a,b\"", LedgerFile.quote("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", LedgerFile.quote("say \"hi\""));
        assertEquals("", LedgerFile.quote(""));
    }

    @Test
    void rendersExpectedFileFormat() throws IOException {
        Path file = tmp.resolve("l.csv");
        LedgerFile repo = new LedgerFile(file);
        repo.save(new LedgerData(4, List.of(
                entry(1, "2026-01-10", "expense", "food", "1200.50", "lunch, with \"Taro\""),
                entry(2, "2026-01-05", "income", "salary", "300000.00", ""))));

        String expected = "#next_id=4\n"
                + "1,2026-01-10,expense,food,1200.50,\"lunch, with \"\"Taro\"\"\"\n"
                + "2,2026-01-05,income,salary,300000.00,\n";
        assertEquals(expected, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void roundTripsSpecialCharacters() {
        Path file = tmp.resolve("l.csv");
        LedgerFile repo = new LedgerFile(file);
        LedgerData data = new LedgerData(3, List.of(
                entry(1, "2026-03-01", "expense", "a,\"b\"", "5.00", "x, \"y\""),
                entry(2, "2026-03-02", "income", "食費", "10.00", "ランチ")));
        repo.save(data);

        assertEquals(data, repo.load());
    }

    @Test
    void missingFileIsEmptyAndIsNotCreatedOnRead() {
        Path file = tmp.resolve("none.csv");
        LedgerData data = new LedgerFile(file).load();

        assertEquals(1L, data.nextId());
        assertTrue(data.entries().isEmpty());
        assertFalse(Files.exists(file));
    }

    @Test
    void zeroByteFileIsEmpty() throws IOException {
        Path file = tmp.resolve("zero.csv");
        Files.write(file, new byte[0]);

        assertEquals(LedgerData.empty(), new LedgerFile(file).load());
    }

    @Test
    void invalidHeaderIsIoError() throws IOException {
        Path file = tmp.resolve("bad.csv");
        Files.writeString(file, "1,2026-01-10,expense,food,1200.50,\n");

        assertThrows(LedgerIOException.class, () -> new LedgerFile(file).load());
    }

    @Test
    void wrongFieldCountIsIoError() throws IOException {
        Path file = tmp.resolve("bad.csv");
        Files.writeString(file, "#next_id=2\n1,2026-01-10,expense,food,1200.50\n");

        assertThrows(LedgerIOException.class, () -> new LedgerFile(file).load());
    }

    @Test
    void unterminatedQuoteIsIoError() throws IOException {
        Path file = tmp.resolve("bad.csv");
        Files.writeString(file, "#next_id=2\n1,2026-01-10,expense,\"food,1200.50,\n");

        assertThrows(LedgerIOException.class, () -> new LedgerFile(file).load());
    }

    @Test
    void junkAfterClosingQuoteIsIoError() throws IOException {
        Path file = tmp.resolve("bad.csv");
        Files.writeString(file, "#next_id=2\n1,2026-01-10,expense,\"ab\"c,1200.50,\n");

        assertThrows(LedgerIOException.class, () -> new LedgerFile(file).load());
    }

    @Test
    void idNotBelowNextIdIsIoError() throws IOException {
        Path file = tmp.resolve("bad.csv");
        Files.writeString(file, "#next_id=2\n5,2026-01-10,expense,food,1200.50,\n");

        assertThrows(LedgerIOException.class, () -> new LedgerFile(file).load());
    }

    @Test
    void directoryPathIsIoError() {
        LedgerFile repo = new LedgerFile(tmp);

        assertThrows(LedgerIOException.class, repo::load);
    }

    @Test
    void saveToMissingParentIsIoError() {
        LedgerFile repo = new LedgerFile(tmp.resolve("no-such-dir").resolve("l.csv"));

        assertThrows(LedgerIOException.class, () -> repo.save(LedgerData.empty()));
    }
}
