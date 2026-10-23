package ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {
    @TempDir Path dir;
    Path file;
    String out;
    String err;

    @BeforeEach
    void setUp() {
        file = dir.resolve("l.csv");
    }

    int run(String... args) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        int code = Main.run(args, dir.resolve("default.csv").toString(),
                new PrintStream(o, true, StandardCharsets.UTF_8), new PrintStream(e, true, StandardCharsets.UTF_8));
        out = o.toString(StandardCharsets.UTF_8);
        err = e.toString(StandardCharsets.UTF_8);
        return code;
    }

    /** --data にテスト用ファイルを付けて実行する。 */
    int data(String... args) {
        String[] full = new String[args.length + 2];
        full[0] = "--data";
        full[1] = file.toString();
        System.arraycopy(args, 0, full, 2, args.length);
        return run(full);
    }

    @Test
    void usageExample() throws Exception {
        assertEquals(0, data("add", "2026-01-10", "expense", "food", "1200.5", "lunch, with \"Taro\""));
        assertEquals("1\n", out);
        assertEquals(0, data("add", "2026-01-05", "income", "salary", "300000"));
        assertEquals(0, data("add", "2026-01-10", "expense", "transport", "300", "train"));
        assertEquals("#next_id=4\n1,2026-01-10,expense,food,1200.50,\"lunch, with \"\"Taro\"\"\"\n"
                + "2,2026-01-05,income,salary,300000.00,\n3,2026-01-10,expense,transport,300.00,train\n",
                Files.readString(file));
        assertEquals(0, data("list"));
        assertEquals("id\tdate\ttype\tcategory\tamount\tmemo\n2\t2026-01-05\tincome\tsalary\t300000.00\t\n"
                + "1\t2026-01-10\texpense\tfood\t1200.50\tlunch, with \"Taro\"\n"
                + "3\t2026-01-10\texpense\ttransport\t300.00\ttrain\n", out);
        assertEquals(0, data("summary", "--month", "2026-01"));
        assertEquals("month\t2026-01\nincome\t300000.00\nexpense\t1500.50\nbalance\t298499.50\n"
                + "expense_by_category\nfood\t1200.50\ntransport\t300.00\n", out);
        assertEquals(0, data("delete", "3"));
        assertEquals("deleted 3\n", out);
        assertEquals(2, data("add", "2026-02-01", "expense", "food", "0"));
        assertEquals("", out);
        assertTrue(err.startsWith("error: "));
        assertEquals(3, data("delete", "99"));
        // 末尾 ID を削除しても再利用されない
        assertEquals(0, data("add", "2026-02-01", "expense", "food", "1"));
        assertEquals("4\n", out);
    }

    @Test
    void idNotReusedAfterDeletingAll() {
        data("add", "2026-01-01", "income", "a", "1");
        data("delete", "1");
        data("add", "2026-01-01", "income", "a", "1");
        assertEquals("2\n", out);
    }

    @Test
    void argumentErrors() {
        assertEquals(2, run());
        assertEquals(2, run("--data"));
        assertEquals(2, run("--data", file.toString()));
        assertEquals(2, run("bogus"));
        assertEquals(2, data("list", "--data", "x"));
        assertEquals(2, data("list", "--month"));
        assertEquals(2, data("list", "extra"));
        assertEquals(2, data("list", "--month", "2026-13"));
        assertEquals(2, data("summary"));
        assertEquals(2, data("delete", "01"));
        assertEquals(2, data("delete", "9223372036854775808"));
        assertEquals(2, data("add", "2026-01-01", "income", "a"));
        assertFalse(Files.exists(file));
    }

    @Test
    void validationRules() {
        for (String d : new String[] {"2026-02-30", "2026-13-01", "2026/01/05", "2026-1-5"}) {
            assertEquals(2, data("add", d, "income", "a", "1"), d);
        }
        assertEquals(0, data("add", "2024-02-29", "income", "a", "1"));
        String[] badAmounts = {"0", "0.00", "-5", "12.345", "abc", "1e3", "1,200", "1200.", ".5", "+5",
            "1234567890123"};
        for (String a : badAmounts) {
            assertEquals(2, data("add", "2026-01-01", "income", "a", a), a);
        }
        assertEquals(2, data("add", "2026-01-01", "Income", "a", "1"));
        assertEquals(2, data("add", "2026-01-01", "income", "", "1"));
        assertEquals(2, data("add", "2026-01-01", "income", "a\tb", "1"));
        assertEquals(2, data("add", "2026-01-01", "income", "a", "1", "m\nx"));
        assertEquals(0, data("add", "2026-01-01", "income", " 食費 ", "1", ""));
    }

    @Test
    void summarySortingAndNegativeBalance() {
        data("add", "2026-03-01", "expense", "b", "10");
        data("add", "2026-03-02", "expense", "a", "10");
        data("add", "2026-03-03", "expense", "c", "20.5");
        data("add", "2026-04-03", "expense", "z", "99");
        data("summary", "--month", "2026-03");
        assertEquals("month\t2026-03\nincome\t0.00\nexpense\t40.50\nbalance\t-40.50\nexpense_by_category\n"
                + "c\t20.50\na\t10.00\nb\t10.00\n", out);
        data("summary", "--month", "2025-01");
        assertEquals("month\t2025-01\nincome\t0.00\nexpense\t0.00\nbalance\t0.00\nexpense_by_category\n", out);
    }

    @Test
    void decimalSumIsExact() {
        for (int i = 0; i < 3; i++) {
            data("add", "2026-01-01", "income", "x", "0.10");
        }
        data("summary", "--month", "2026-01");
        assertTrue(out.contains("income\t0.30\n"));
    }

    @Test
    void listMonthFilterAndEmpty() {
        data("add", "2026-01-31", "income", "a", "1");
        data("add", "2026-02-01", "income", "a", "1");
        data("list", "--month", "2026-02");
        assertEquals("id\tdate\ttype\tcategory\tamount\tmemo\n2\t2026-02-01\tincome\ta\t1.00\t\n", out);
        data("list", "--month", "2030-01");
        assertEquals("id\tdate\ttype\tcategory\tamount\tmemo\n", out);
    }

    @Test
    void readOnlyCommandsDoNotCreateFile() {
        assertEquals(0, data("list"));
        assertEquals(0, data("summary", "--month", "2026-01"));
        assertFalse(Files.exists(file));
    }

    @Test
    void emptyFileIsEmptyData() throws Exception {
        Files.write(file, new byte[0]);
        assertEquals(0, data("add", "2026-01-01", "income", "a", "1"));
        assertEquals("1\n", out);
    }

    @Test
    void ioErrors() throws Exception {
        Files.writeString(file, "garbage\n");
        assertEquals(1, data("list"));
        assertEquals("", out);
        assertEquals(1, run("--data", dir.toString(), "list"));
        assertEquals(1, run("--data", dir.resolve("nodir/x.csv").toString(), "add", "2026-01-01", "income", "a", "1"));
        // 引数不正はファイル状態に関わらず 2
        assertEquals(2, data("add", "bad", "income", "a", "1"));
    }

    @Test
    void notFoundDoesNotModify() throws Exception {
        data("add", "2026-01-01", "income", "a", "1");
        String before = Files.readString(file);
        assertEquals(3, data("delete", "5"));
        assertEquals(before, Files.readString(file));
    }
}
