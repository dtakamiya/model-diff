package ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LedgerRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("ファイルが無ければ空のデータ（次の ID は 1）で、読み込みでファイルは作らない")
    void missingFileIsEmptyAndNotCreated() {
        Path file = tempDir.resolve("none.csv");
        Ledger ledger = new LedgerRepository(file).load();
        assertEquals(1, ledger.nextId());
        assertTrue(ledger.entries().isEmpty());
        assertFalse(Files.exists(file));
    }

    @Test
    @DisplayName("0 バイトのファイルも空のデータとして扱う")
    void zeroByteFileIsEmpty() throws IOException {
        Path file = Files.createFile(tempDir.resolve("empty.csv"));
        Ledger ledger = new LedgerRepository(file).load();
        assertEquals(1, ledger.nextId());
        assertTrue(ledger.entries().isEmpty());
    }

    @Test
    @DisplayName("保存したデータは読み直すと同じ内容になり、書式は仕様どおり")
    void saveAndLoadRoundTrip() throws IOException {
        Path file = tempDir.resolve("ledger.csv");
        LedgerRepository repository = new LedgerRepository(file);

        Ledger ledger = Ledger.empty();
        ledger.add(LocalDate.of(2026, 1, 10), EntryType.EXPENSE, "food",
                new BigDecimal("1200.5"), "lunch, with \"Taro\"");
        ledger.add(LocalDate.of(2026, 1, 5), EntryType.INCOME, "salary",
                new BigDecimal("300000"), "");
        repository.save(ledger);

        assertEquals(
                "#next_id=3\n"
                        + "1,2026-01-10,expense,food,1200.50,\"lunch, with \"\"Taro\"\"\"\n"
                        + "2,2026-01-05,income,salary,300000.00,\n",
                Files.readString(file, StandardCharsets.UTF_8));

        Ledger reloaded = repository.load();
        assertEquals(3, reloaded.nextId());
        assertEquals(ledger.entries(), reloaded.entries());
    }

    @Test
    @DisplayName("末尾の ID を削除した後も ID は再利用しない")
    void deletedLastIdIsNotReused() {
        LedgerRepository repository = new LedgerRepository(tempDir.resolve("reuse.csv"));
        Ledger ledger = Ledger.empty();
        ledger.add(LocalDate.of(2026, 1, 1), EntryType.EXPENSE, "a", new BigDecimal("1"), "");
        ledger.add(LocalDate.of(2026, 1, 2), EntryType.EXPENSE, "b", new BigDecimal("2"), "");
        repository.save(ledger);

        Ledger afterDelete = repository.load();
        assertTrue(afterDelete.remove(2));
        repository.save(afterDelete);

        Ledger next = repository.load();
        assertEquals(3, next.add(LocalDate.of(2026, 1, 3), EntryType.EXPENSE, "c", new BigDecimal("3"), ""));
    }

    @Test
    @DisplayName("全件削除した後も ID は続きから採番される")
    void idsContinueAfterDeletingEverything() {
        LedgerRepository repository = new LedgerRepository(tempDir.resolve("all.csv"));
        Ledger ledger = Ledger.empty();
        ledger.add(LocalDate.of(2026, 1, 1), EntryType.INCOME, "a", new BigDecimal("1"), "");
        repository.save(ledger);

        Ledger loaded = repository.load();
        assertTrue(loaded.remove(1));
        repository.save(loaded);

        Ledger afterAll = repository.load();
        assertTrue(afterAll.entries().isEmpty());
        assertEquals(2, afterAll.nextId());
    }

    @Test
    @DisplayName("ディレクトリを指定すると DataFileException")
    void directoryIsDataFileException() throws IOException {
        Path dir = Files.createDirectory(tempDir.resolve("adir"));
        assertThrows(DataFileException.class, () -> new LedgerRepository(dir).load());
    }

    @Test
    @DisplayName("親ディレクトリが無い場所への保存は DataFileException（ディレクトリは作らない）")
    void saveFailsWithoutParentDirectory() {
        Path file = tempDir.resolve("no-such-dir").resolve("ledger.csv");
        LedgerRepository repository = new LedgerRepository(file);
        assertThrows(DataFileException.class, () -> repository.save(Ledger.empty()));
        assertFalse(Files.exists(file.getParent()));
    }

    @Test
    @DisplayName("壊れたデータ（ヘッダー欠落、ID の矛盾、重複）は DataFileException")
    void corruptedDataIsDataFileException() throws IOException {
        assertCorrupted("no-header.csv", "1,2026-01-10,expense,food,1200.50,\n");
        assertCorrupted("id-too-big.csv", "#next_id=2\n5,2026-01-10,expense,food,1.00,\n");
        assertCorrupted("duplicated.csv", "#next_id=5\n1,2026-01-10,expense,food,1.00,\n1,2026-01-11,expense,food,2.00,\n");
        assertCorrupted("bad-amount.csv", "#next_id=2\n1,2026-01-10,expense,food,0.00,\n");
    }

    private void assertCorrupted(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        assertThrows(DataFileException.class, () -> new LedgerRepository(file).load(), name);
    }
}
