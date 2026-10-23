package ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** App（CLI 全体）のテスト。spec の使用例と終了コードを、一時ディレクトリのデータで検証する。 */
class AppTest {

    private static final String HEADER = "id\tdate\ttype\tcategory\tamount\tmemo\n";

    @TempDir
    Path tmp;

    /** 1 回の実行結果。 */
    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int code;
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            code = App.run(args, out, err);
        }
        return new Result(code,
                outBytes.toString(StandardCharsets.UTF_8),
                errBytes.toString(StandardCharsets.UTF_8));
    }

    /** --data を先頭に付けて実行する。 */
    private static Result ledger(Path data, String... args) {
        List<String> all = new ArrayList<>(List.of("--data", data.toString()));
        all.addAll(List.of(args));
        return run(all.toArray(new String[0]));
    }

    private static void assertOk(Result r, String expectedOut) {
        assertEquals(0, r.code(), "exit code (err=" + r.err() + ")");
        assertEquals(expectedOut, r.out());
        assertEquals("", r.err());
    }

    private static void assertFails(Result r, int expectedCode) {
        assertEquals(expectedCode, r.code());
        assertEquals("", r.out(), "エラー時は標準出力に何も出さない");
        assertTrue(r.err().startsWith("error: "), "stderr: " + r.err());
        assertEquals(1, r.err().split("\n", -1).length - 1, "stderr は 1 行だけ");
    }

    @Test
    void specUsageExampleMatchesExactly() {
        Path data = tmp.resolve("l.csv");

        assertOk(ledger(data, "add", "2026-01-10", "expense", "food", "1200.5", "lunch, with \"Taro\""), "1\n");
        assertOk(ledger(data, "add", "2026-01-05", "income", "salary", "300000"), "2\n");
        assertOk(ledger(data, "add", "2026-01-10", "expense", "transport", "300", "train"), "3\n");
        assertOk(ledger(data, "list"),
                HEADER
                + "2\t2026-01-05\tincome\tsalary\t300000.00\t\n"
                + "1\t2026-01-10\texpense\tfood\t1200.50\tlunch, with \"Taro\"\n"
                + "3\t2026-01-10\texpense\ttransport\t300.00\ttrain\n");
        assertOk(ledger(data, "summary", "--month", "2026-01"),
                "month\t2026-01\n"
                + "income\t300000.00\n"
                + "expense\t1500.50\n"
                + "balance\t298499.50\n"
                + "expense_by_category\n"
                + "food\t1200.50\n"
                + "transport\t300.00\n");
        assertOk(ledger(data, "delete", "3"), "deleted 3\n");
        assertFails(ledger(data, "add", "2026-02-01", "expense", "food", "0"), 2);
        assertFails(ledger(data, "delete", "99"), 3);
    }

    @Test
    void idIsNotReusedAfterDeletingLastOrAll() {
        Path data = tmp.resolve("l.csv");
        assertOk(ledger(data, "add", "2026-01-01", "expense", "a", "1"), "1\n");
        assertOk(ledger(data, "add", "2026-01-02", "expense", "a", "1"), "2\n");
        assertOk(ledger(data, "delete", "2"), "deleted 2\n");
        assertOk(ledger(data, "add", "2026-01-03", "expense", "a", "1"), "3\n");
        assertOk(ledger(data, "delete", "1"), "deleted 1\n");
        assertOk(ledger(data, "delete", "3"), "deleted 3\n");
        assertOk(ledger(data, "add", "2026-01-04", "expense", "a", "1"), "4\n");
    }

    @Test
    void invalidArgumentsExitWithTwoEvenIfFileIsCorrupted() throws IOException {
        Path data = tmp.resolve("l.csv");
        Files.writeString(data, "garbage");

        assertFails(ledger(data, "add", "2026-02-30", "expense", "food", "100"), 2);
        assertFails(ledger(data, "list", "--month", "2026-13"), 2);
        assertEquals("garbage", Files.readString(data));
    }

    @Test
    void dataOptionWithoutValueExitsWithTwo() {
        assertFails(run("--data"), 2);
    }

    @Test
    void dataOptionAfterCommandIsInputError() {
        Path data = tmp.resolve("l.csv");
        assertFails(run("list", "--data", data.toString()), 2);
    }

    @Test
    void missingOrUnknownCommandIsInputError() {
        Path data = tmp.resolve("l.csv");
        assertFails(run(), 2);
        assertFails(ledger(data, "frobnicate"), 2);
    }

    @Test
    void addWithWrongArgumentCountIsInputError() {
        Path data = tmp.resolve("l.csv");
        assertFails(ledger(data, "add", "2026-01-01", "expense", "food"), 2);
        assertFails(ledger(data, "add", "2026-01-01", "expense", "food", "1", "m", "extra"), 2);
    }

    @Test
    void listReadsWithoutCreatingFileAndShowsHeaderOnly() {
        Path data = tmp.resolve("l.csv");
        assertOk(ledger(data, "list"), HEADER);
        assertFalse(Files.exists(data));
    }

    @Test
    void listFiltersByMonth() {
        Path data = tmp.resolve("l.csv");
        ledger(data, "add", "2026-01-31", "expense", "food", "100");
        ledger(data, "add", "2026-02-01", "expense", "food", "200", "feb");

        assertOk(ledger(data, "list", "--month", "2026-02"),
                HEADER + "2\t2026-02-01\texpense\tfood\t200.00\tfeb\n");
    }

    @Test
    void summaryOfTenCentsThreeTimesIsThirtyCents() {
        Path data = tmp.resolve("l.csv");
        for (int i = 0; i < 3; i++) {
            ledger(data, "add", "2026-01-01", "income", "x", "0.10");
        }
        assertOk(ledger(data, "summary", "--month", "2026-01"),
                "month\t2026-01\n"
                + "income\t0.30\n"
                + "expense\t0.00\n"
                + "balance\t0.30\n"
                + "expense_by_category\n");
    }

    @Test
    void summaryOfEmptyMonthIsAllZero() {
        Path data = tmp.resolve("l.csv");
        assertOk(ledger(data, "summary", "--month", "2026-05"),
                "month\t2026-05\n"
                + "income\t0.00\n"
                + "expense\t0.00\n"
                + "balance\t0.00\n"
                + "expense_by_category\n");
    }

    @Test
    void summaryShowsNegativeBalanceAndSortsCategories() {
        Path data = tmp.resolve("l.csv");
        ledger(data, "add", "2026-01-01", "expense", "b", "100");
        ledger(data, "add", "2026-01-01", "expense", "a", "100");
        ledger(data, "add", "2026-01-01", "expense", "c", "300");
        ledger(data, "add", "2026-01-01", "income", "salary", "50");

        assertOk(ledger(data, "summary", "--month", "2026-01"),
                "month\t2026-01\n"
                + "income\t50.00\n"
                + "expense\t500.00\n"
                + "balance\t-450.00\n"
                + "expense_by_category\n"
                + "c\t300.00\n"
                + "a\t100.00\n"
                + "b\t100.00\n");
    }

    @Test
    void summaryRequiresMonth() {
        Path data = tmp.resolve("l.csv");
        assertFails(ledger(data, "summary"), 2);
        assertFails(ledger(data, "summary", "--month", "2026-1"), 2);
    }

    @Test
    void deleteRejectsMalformedIdsWithTwo() {
        Path data = tmp.resolve("l.csv");
        for (String id : List.of("abc", "0", "-1", "01", "9223372036854775808")) {
            assertFails(ledger(data, "delete", id), 2);
        }
        assertFails(ledger(data, "delete"), 2);
    }

    @Test
    void deleteOfMissingIdOnMissingFileExitsWithThreeAndCreatesNothing() {
        Path data = tmp.resolve("l.csv");
        assertFails(ledger(data, "delete", "1"), 3);
        assertFalse(Files.exists(data));
    }

    @Test
    void addToMissingParentDirectoryIsIoError() {
        Path data = tmp.resolve("no-such-dir").resolve("l.csv");
        assertFails(ledger(data, "add", "2026-01-01", "expense", "food", "100"), 1);
    }

    @Test
    void invalidDataPathIsIoErrorWithoutStdout() {
        // NUL 文字を含むパスは Path にできないので、読み込み前に IO エラーとして扱う
        assertFails(run("--data", "a\u0000b", "list"), 1);
    }

    @Test
    void invalidDataPathStillReportsArgumentErrorFirst() {
        assertFails(run("--data", "a\u0000b", "add", "2026-01-01", "expense", "food", "0"), 2);
    }

    @Test
    void directoryAsDataFileIsIoError() {
        assertFails(ledger(tmp, "list"), 1);
        assertFails(ledger(tmp, "add", "2026-01-01", "expense", "food", "100"), 1);
    }

    @Test
    void corruptedFileIsIoError() throws IOException {
        Path data = tmp.resolve("l.csv");
        Files.writeString(data, "#next_id=2\n1,2026-01-01,expense,food\n");

        assertFails(ledger(data, "list"), 1);
    }

    @Test
    void emptyMemoIsAcceptedAsOmitted() {
        Path data = tmp.resolve("l.csv");
        assertOk(ledger(data, "add", "2026-01-01", "expense", "food", "100", ""), "1\n");
        assertOk(ledger(data, "list"), HEADER + "1\t2026-01-01\texpense\tfood\t100.00\t\n");
    }

    @Test
    void listSortsByDateThenId() {
        Path data = tmp.resolve("l.csv");
        ledger(data, "add", "2026-01-10", "expense", "a", "1");
        ledger(data, "add", "2026-01-05", "expense", "b", "2");
        ledger(data, "add", "2026-01-10", "expense", "c", "3");

        assertOk(ledger(data, "list"),
                HEADER
                + "2\t2026-01-05\texpense\tb\t2.00\t\n"
                + "1\t2026-01-10\texpense\ta\t1.00\t\n"
                + "3\t2026-01-10\texpense\tc\t3.00\t\n");
    }
}
