package ledger.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CLI をプロセス内で実行し、標準出力・標準エラー出力・終了コードを検証する。 */
class LedgerAppTest {

    @TempDir
    Path dir;

    private Path data;

    /** 1 回分の実行結果。 */
    private record Result(int code, String out, String err) {
    }

    private Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = new LedgerApp(dir.resolve("default.csv")).run(args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private Result ledger(String... args) {
        String[] all = new String[args.length + 2];
        all[0] = "--data";
        all[1] = data.toString();
        System.arraycopy(args, 0, all, 2, args.length);
        return run(all);
    }

    private void assertError(int expectedCode, Result r) {
        assertEquals(expectedCode, r.code());
        assertEquals("", r.out());
        assertTrue(r.err().startsWith("error: "), r.err());
        assertEquals(1, r.err().lines().count());
    }

    @Test
    void specUsageExample() {
        data = dir.resolve("l.csv");
        assertEquals(new Result(0, "1\n", ""),
                ledger("add", "2026-01-10", "expense", "food", "1200.5", "lunch, with \"Taro\""));
        assertEquals("2\n", ledger("add", "2026-01-05", "income", "salary", "300000").out());
        assertEquals("3\n", ledger("add", "2026-01-10", "expense", "transport", "300", "train").out());

        assertEquals("""
                id\tdate\ttype\tcategory\tamount\tmemo
                2\t2026-01-05\tincome\tsalary\t300000.00\t
                1\t2026-01-10\texpense\tfood\t1200.50\tlunch, with "Taro"
                3\t2026-01-10\texpense\ttransport\t300.00\ttrain
                """, ledger("list").out());

        assertEquals("""
                month\t2026-01
                income\t300000.00
                expense\t1500.50
                balance\t298499.50
                expense_by_category
                food\t1200.50
                transport\t300.00
                """, ledger("summary", "--month", "2026-01").out());

        assertEquals(new Result(0, "deleted 3\n", ""), ledger("delete", "3"));
        assertError(2, ledger("add", "2026-02-01", "expense", "food", "0"));
        assertError(3, ledger("delete", "99"));
    }

    @Test
    void idsAreNeverReused() {
        data = dir.resolve("l.csv");
        ledger("add", "2026-01-01", "expense", "a", "1");
        ledger("add", "2026-01-01", "expense", "a", "1");
        ledger("delete", "2");
        assertEquals("3\n", ledger("add", "2026-01-01", "expense", "a", "1").out());
        ledger("delete", "1");
        ledger("delete", "3");
        assertEquals("4\n", ledger("add", "2026-01-01", "expense", "a", "1").out());
    }

    @Test
    void listFiltersByMonthAndPrintsHeaderWhenEmpty() {
        data = dir.resolve("l.csv");
        assertEquals("id\tdate\ttype\tcategory\tamount\tmemo\n", ledger("list").out());
        assertFalse(Files.exists(data), "read-only commands must not create the file");

        ledger("add", "2026-02-01", "expense", "a", "1");
        ledger("add", "2026-01-31", "expense", "b", "2");
        assertEquals("id\tdate\ttype\tcategory\tamount\tmemo\n2\t2026-01-31\texpense\tb\t2.00\t\n",
                ledger("list", "--month", "2026-01").out());
    }

    @Test
    void summarySortsCategoriesAndHandlesNegativeBalance() {
        data = dir.resolve("l.csv");
        ledger("add", "2026-03-01", "expense", "b", "100");
        ledger("add", "2026-03-02", "expense", "a", "100");
        ledger("add", "2026-03-03", "expense", "c", "300");
        ledger("add", "2026-03-04", "income", "salary", "0.1");
        ledger("add", "2026-03-05", "income", "salary", "0.1");
        ledger("add", "2026-03-06", "income", "salary", "0.1");
        assertEquals("""
                month\t2026-03
                income\t0.30
                expense\t500.00
                balance\t-499.70
                expense_by_category
                c\t300.00
                a\t100.00
                b\t100.00
                """, ledger("summary", "--month", "2026-03").out());

        assertEquals("""
                month\t2026-04
                income\t0.00
                expense\t0.00
                balance\t0.00
                expense_by_category
                """, ledger("summary", "--month", "2026-04").out());
    }

    @Test
    void invalidArgumentsAreInputErrorsEvenWithBrokenFile() throws Exception {
        data = Files.writeString(dir.resolve("broken.csv"), "garbage\n");
        assertError(2, run());
        assertError(2, run("--data"));
        assertError(2, run("--data", data.toString()));
        assertError(2, ledger("unknown"));
        assertError(2, ledger("add", "2026-01-01", "expense", "a"));
        assertError(2, ledger("add", "2026-01-01", "expense", "a", "1", "m", "extra"));
        assertError(2, ledger("add", "2026-01-01", "Expense", "a", "1"));
        assertError(2, ledger("list", "--month"));
        assertError(2, ledger("list", "--month", "2026-13"));
        assertError(2, ledger("list", "extra"));
        assertError(2, ledger("summary"));
        assertError(2, ledger("delete", "01"));
        assertError(2, ledger("delete", "1", "2"));
        assertError(2, ledger("list", "--data", data.toString()));
        assertEquals("garbage\n", Files.readString(data));
    }

    @Test
    void ioErrors() throws Exception {
        data = Files.writeString(dir.resolve("broken.csv"), "garbage\n");
        assertError(1, ledger("list"));
        assertError(1, ledger("add", "2026-01-01", "expense", "a", "1"));

        data = dir;
        assertError(1, ledger("list"));

        data = dir.resolve("missing/l.csv");
        assertError(1, ledger("add", "2026-01-01", "expense", "a", "1"));
    }

    @Test
    void emptyMemoIsAccepted() {
        data = dir.resolve("l.csv");
        assertEquals("1\n", ledger("add", "2026-01-01", "expense", " 食費 ", "1", "").out());
        assertEquals("id\tdate\ttype\tcategory\tamount\tmemo\n1\t2026-01-01\texpense\t 食費 \t1.00\t\n",
                ledger("list").out());
    }
}
