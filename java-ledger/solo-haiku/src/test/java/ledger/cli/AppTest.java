package ledger.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    @TempDir
    Path tempDir;

    /** 1 回のコマンド実行結果 */
    private record Result(int exitCode, String stdout, String stderr) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = new App(
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)).run(args);
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private String[] withData(String... args) {
        String[] all = new String[args.length + 2];
        all[0] = "--data";
        all[1] = tempDir.resolve("l.csv").toString();
        System.arraycopy(args, 0, all, 2, args.length);
        return all;
    }

    @Test
    void 使用例どおりに追加一覧集計削除できる() throws IOException {
        assertEquals("1\n", run(withData("add", "2026-01-10", "expense", "food", "1200.5", "lunch, with \"Taro\"")).stdout());
        assertEquals("2\n", run(withData("add", "2026-01-05", "income", "salary", "300000")).stdout());
        assertEquals("3\n", run(withData("add", "2026-01-10", "expense", "transport", "300", "train")).stdout());

        assertEquals("id\tdate\ttype\tcategory\tamount\tmemo\n"
                + "2\t2026-01-05\tincome\tsalary\t300000.00\t\n"
                + "1\t2026-01-10\texpense\tfood\t1200.50\tlunch, with \"Taro\"\n"
                + "3\t2026-01-10\texpense\ttransport\t300.00\ttrain\n",
                run(withData("list")).stdout());

        assertEquals("month\t2026-01\n"
                + "income\t300000.00\n"
                + "expense\t1500.50\n"
                + "balance\t298499.50\n"
                + "expense_by_category\n"
                + "food\t1200.50\n"
                + "transport\t300.00\n",
                run(withData("summary", "--month", "2026-01")).stdout());

        assertEquals("deleted 3\n", run(withData("delete", "3")).stdout());
        assertEquals("4\n", run(withData("add", "2026-02-01", "expense", "food", "100")).stdout());

        String saved = Files.readString(tempDir.resolve("l.csv"));
        assertTrue(saved.startsWith("#next_id=5\n"), saved);
    }

    @Test
    void 検証エラーは終了コード2で標準出力は空() {
        Result result = run(withData("add", "2026-02-30", "expense", "food", "100"));

        assertEquals(2, result.exitCode());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().startsWith("error: "), result.stderr());
        assertFalse(Files.exists(tempDir.resolve("l.csv")));
    }

    @Test
    void 不正な金額やタブを含むメモは終了コード2() {
        assertEquals(2, run(withData("add", "2026-01-01", "expense", "food", "0")).exitCode());
        assertEquals(2, run(withData("add", "2026-01-01", "expense", "food", "12.345")).exitCode());
        assertEquals(2, run(withData("add", "2026-01-01", "expense", "food", "100", "a\tb")).exitCode());
    }

    @Test
    void 存在しないIDの削除は終了コード3() {
        assertEquals(3, run(withData("delete", "99")).exitCode());
    }

    @Test
    void IDの書式が不正なら終了コード2() {
        assertEquals(2, run(withData("delete", "01")).exitCode());
        assertEquals(2, run(withData("delete", "0")).exitCode());
        assertEquals(2, run(withData("delete", "abc")).exitCode());
    }

    @Test
    void summaryは月が必須() {
        assertEquals(2, run(withData("summary")).exitCode());
        assertEquals(2, run(withData("summary", "--month", "2026-13")).exitCode());
    }

    @Test
    void 読み取りだけのコマンドはファイルを作らない() {
        assertEquals("id\tdate\ttype\tcategory\tamount\tmemo\n", run(withData("list")).stdout());
        assertEquals(0, run(withData("summary", "--month", "2026-01")).exitCode());
        assertFalse(Files.exists(tempDir.resolve("l.csv")));
    }

    @Test
    void 引数の検証はデータファイルより先に行う() throws IOException {
        Files.writeString(tempDir.resolve("l.csv"), "broken");

        assertEquals(2, run(withData("add", "2026-01-01", "expense")).exitCode());
    }

    @Test
    void データが壊れていれば終了コード1() throws IOException {
        Files.writeString(tempDir.resolve("l.csv"), "#next_id=2\n1,2026-01-10,expense,food\n");

        assertEquals(1, run(withData("list")).exitCode());
    }

    @Test
    void 保存先ディレクトリが無ければ終了コード1() {
        String missing = tempDir.resolve("no-such-dir/l.csv").toString();

        assertEquals(1, run("--data", missing, "add", "2026-01-01", "expense", "food", "100").exitCode());
    }

    @Test
    void ディレクトリを指定したら終了コード1() {
        assertEquals(1, run("--data", tempDir.toString(), "list").exitCode());
    }

    @Test
    void コマンド未指定や未知のコマンドやdataの位置違いは終了コード2() {
        assertEquals(2, run().exitCode());
        assertEquals(2, run("--data", tempDir.resolve("l.csv").toString()).exitCode());
        assertEquals(2, run(withData("purge")).exitCode());
        assertEquals(2, run("list", "--data", tempDir.resolve("l.csv").toString()).exitCode());
        assertEquals(2, run("--data").exitCode());
    }

    @Test
    void 空の0バイトファイルはデータなしとして扱う() throws IOException {
        Files.createFile(tempDir.resolve("l.csv"));

        assertEquals("1\n", run(withData("add", "2026-01-01", "expense", "food", "100")).stdout());
    }
}
