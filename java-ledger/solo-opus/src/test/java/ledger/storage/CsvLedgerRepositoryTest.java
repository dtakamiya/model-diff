package ledger.storage;

import ledger.error.StorageException;
import ledger.model.Entry;
import ledger.model.EntryType;
import ledger.model.LedgerData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvLedgerRepositoryTest {

    @TempDir
    Path dir;

    private static final String SAMPLE = """
            #next_id=4
            1,2026-01-10,expense,food,1200.50,"lunch, with ""Taro\"""
            2,2026-01-05,income,salary,300000.00,
            3,2026-01-10,expense,transport,300.00,train
            """;

    @Test
    void missingOrEmptyFileMeansNoData() throws Exception {
        assertEquals(LedgerData.empty(), new CsvLedgerRepository(dir.resolve("none.csv")).load());
        Path empty = Files.createFile(dir.resolve("empty.csv"));
        assertEquals(LedgerData.empty(), new CsvLedgerRepository(empty).load());
    }

    @Test
    void roundTripsSpecExample() throws Exception {
        Path file = write(SAMPLE);
        CsvLedgerRepository repo = new CsvLedgerRepository(file);
        LedgerData data = repo.load();

        assertEquals(4, data.nextId());
        assertEquals("lunch, with \"Taro\"", data.entries().get(0).memo());
        assertEquals("", data.entries().get(1).memo());

        repo.save(data);
        assertEquals(SAMPLE, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void quotesCategoryAndMemoOnlyWhenNeeded() {
        Entry e = new Entry(1, LocalDate.of(2026, 1, 1), EntryType.EXPENSE, "a,b", new BigDecimal("1.00"), "日本語 memo");
        String text = CsvLedgerRepository.format(new LedgerData(2, List.of(e)));
        assertEquals("#next_id=2\n1,2026-01-01,expense,\"a,b\",1.00,日本語 memo\n", text);
    }

    @Test
    void corruptedFilesAreStorageErrors() throws Exception {
        String[] broken = {
                "1,2026-01-10,expense,food,1.00,\n",                      // ヘッダーなし
                "#next_id=x\n",                                            // next_id 不正
                "#next_id=3\n1,2026-01-10,expense,food,1.00\n",            // 列数不足
                "#next_id=3\n1,2026-02-30,expense,food,1.00,\n",           // 日付不正
                "#next_id=3\n1,2026-01-10,expense,\"food,1.00,\n",         // クォート閉じ忘れ
                "#next_id=2\n5,2026-01-10,expense,food,1.00,\n",           // ID が next_id 以上
                "#next_id=3\n1,2026-01-10,expense,a,1.00,\n1,2026-01-10,expense,b,1.00,\n", // ID 重複
                "#next_id=3\n\n",                                          // 空行
        };
        for (String content : broken) {
            Path file = write(content);
            assertThrows(StorageException.class, () -> new CsvLedgerRepository(file).load(), content);
        }
    }

    @Test
    void directoryIsStorageError() {
        assertThrows(StorageException.class, () -> new CsvLedgerRepository(dir).load());
    }

    @Test
    void savingIntoMissingDirectoryFails() {
        CsvLedgerRepository repo = new CsvLedgerRepository(dir.resolve("no/such/dir/l.csv"));
        assertThrows(StorageException.class, () -> repo.save(LedgerData.empty()));
    }

    private Path write(String content) throws IOException {
        return Files.writeString(dir.resolve("data.csv"), content, StandardCharsets.UTF_8);
    }
}
