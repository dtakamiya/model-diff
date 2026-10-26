package ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** CLI 全体の結果（標準出力・終了コード）を検証する。 */
class MainTest {
    @TempDir
    Path dir;

    private String data;
    private String stdout;
    private String stderr;

    @BeforeEach
    void setUp() {
        data = dir.resolve("l.csv").toString();
    }

    private int run(String... args) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        int code = Main.run(args, new PrintStream(o, true, StandardCharsets.UTF_8),
                new PrintStream(e, true, StandardCharsets.UTF_8));
        stdout = o.toString(StandardCharsets.UTF_8);
        stderr = e.toString(StandardCharsets.UTF_8);
        return code;
    }

    private int runData(String... args) {
        String[] full = new String[args.length + 2];
        full[0] = "--data";
        full[1] = data;
        System.arraycopy(args, 0, full, 2, args.length);
        return run(full);
    }

    @Test
    void specScenario() {
        assertEquals(0, runData("add", "2026-01-10", "expense", "food", "1200.5", "lunch, with \"Taro\""));
        assertEquals("1\n", stdout);
        runData("add", "2026-01-05", "income", "salary", "300000");
        runData("add", "2026-01-10", "expense", "transport", "300", "train");

        assertEquals(0, runData("list"));
        assertEquals("id\tdate\ttype\tcategory\tamount\tmemo\n"
                + "2\t2026-01-05\tincome\tsalary\t300000.00\t\n"
                + "1\t2026-01-10\texpense\tfood\t1200.50\tlunch, with \"Taro\"\n"
                + "3\t2026-01-10\texpense\ttransport\t300.00\ttrain\n", stdout);

        assertEquals(0, runData("summary", "--month", "2026-01"));
        assertEquals("month\t2026-01\nincome\t300000.00\nexpense\t1500.50\nbalance\t298499.50\n"
                + "expense_by_category\nfood\t1200.50\ntransport\t300.00\n", stdout);

        assertEquals(0, runData("delete", "3"));
        assertEquals("deleted 3\n", stdout);
        assertEquals(0, runData("add", "2026-02-01", "expense", "food", "1"));
        assertEquals("4\n", stdout);
    }

    @Test
    void inputErrorsExitWith2AndPrintNothingToStdout() {
        assertEquals(2, runData("add", "2026-02-01", "expense", "food", "0"));
        assertEquals("", stdout);
        assertEquals(true, stderr.startsWith("error: "));
        assertEquals(2, runData("add", "2026-02-30", "expense", "food", "1"));
        assertEquals(2, runData("add", "2026-01-01", "Expense", "food", "1"));
        assertEquals(2, runData("add", "2026-01-01", "expense", "", "1"));
        assertEquals(2, runData("add", "2026-01-01", "expense", "food"));
        assertEquals(2, runData("delete", "01"));
        assertEquals(2, runData("delete", "9223372036854775808"));
        assertEquals(2, runData("list", "--month"));
        assertEquals(2, runData("summary"));
        assertEquals(2, runData("list", "--data", data));
        assertEquals(2, run());
        assertEquals(2, run("--data"));
        assertEquals(2, run("bogus"));
    }

    @Test
    void deleteMissingIdExitsWith3() {
        assertEquals(3, runData("delete", "99"));
        assertEquals("", stdout);
    }

    @Test
    void missingParentDirectoryExitsWith1() {
        assertEquals(1, run("--data", dir.resolve("none/l.csv").toString(), "add", "2026-01-01", "income", "a", "1"));
    }

    @Test
    void brokenOrDirectoryDataExitsWith1() throws IOException {
        Files.writeString(dir.resolve("l.csv"), "oops\n");
        assertEquals(1, runData("list"));
        assertEquals(1, run("--data", dir.toString(), "list"));
        // 引数不正はデータの状態より先に判定される
        assertEquals(2, runData("add", "bad", "income", "a", "1"));
    }

    @Test
    void emptyFileIsTreatedAsNoData() throws IOException {
        Files.writeString(dir.resolve("l.csv"), "");
        assertEquals(0, runData("add", "2026-01-01", "income", "a", "0.1"));
        assertEquals("1\n", stdout);
    }

    @Test
    void summaryHandlesSortingAndNegativeBalance() {
        runData("add", "2026-03-01", "expense", "b", "10");
        runData("add", "2026-03-02", "expense", "a", "10");
        runData("add", "2026-03-03", "expense", "c", "20.50");
        assertEquals(0, runData("summary", "--month", "2026-03"));
        assertEquals("month\t2026-03\nincome\t0.00\nexpense\t40.50\nbalance\t-40.50\n"
                + "expense_by_category\nc\t20.50\na\t10.00\nb\t10.00\n", stdout);
        assertEquals(0, runData("summary", "--month", "2026-04"));
        assertEquals("month\t2026-04\nincome\t0.00\nexpense\t0.00\nbalance\t0.00\nexpense_by_category\n", stdout);
    }

    @Test
    void incomeIsSummedWithoutFloatingPointError() {
        for (int i = 0; i < 3; i++) {
            runData("add", "2026-01-01", "income", "x", "0.10");
        }
        runData("summary", "--month", "2026-01");
        assertEquals(true, stdout.contains("income\t0.30\n"));
    }
}
