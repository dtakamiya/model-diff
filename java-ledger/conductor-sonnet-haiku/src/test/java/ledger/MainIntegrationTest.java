package ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** コマンド単位の統合テスト。Main.run を実際の引数で呼び、標準出力・標準エラー・終了コードを確認する */
class MainIntegrationTest {

    @TempDir
    Path tempDir;

    /** 実行結果（終了コードと標準出力・標準エラーの文字列） */
    private record Result(int exitCode, String stdout, String stderr) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("add → list → summary → delete の一連の流れが仕様どおり")
    void fullFlow() throws IOException {
        String data = tempDir.resolve("l.csv").toString();

        assertEquals("1\n", run("--data", data, "add", "2026-01-10", "expense", "food", "1200.5",
                "lunch, with \"Taro\"").stdout());
        assertEquals("2\n", run("--data", data, "add", "2026-01-05", "income", "salary", "300000").stdout());
        assertEquals("3\n", run("--data", data, "add", "2026-01-10", "expense", "transport", "300", "train").stdout());

        assertEquals(
                "id\tdate\ttype\tcategory\tamount\tmemo\n"
                        + "2\t2026-01-05\tincome\tsalary\t300000.00\t\n"
                        + "1\t2026-01-10\texpense\tfood\t1200.50\tlunch, with \"Taro\"\n"
                        + "3\t2026-01-10\texpense\ttransport\t300.00\ttrain\n",
                run("--data", data, "list").stdout());

        assertEquals(
                "month\t2026-01\nincome\t300000.00\nexpense\t1500.50\nbalance\t298499.50\n"
                        + "expense_by_category\nfood\t1200.50\ntransport\t300.00\n",
                run("--data", data, "summary", "--month", "2026-01").stdout());

        Result deleted = run("--data", data, "delete", "3");
        assertEquals(0, deleted.exitCode());
        assertEquals("deleted 3\n", deleted.stdout());

        // 末尾を削除しても次の ID は 4 のまま
        assertEquals("4\n", run("--data", data, "add", "2026-02-01", "expense", "food", "10").stdout());
    }

    @Test
    @DisplayName("存在しない ID の delete は終了コード 3 で、標準出力は空")
    void deleteMissingIdReturns3() {
        String data = tempDir.resolve("missing.csv").toString();
        run("--data", data, "add", "2026-01-01", "income", "x", "1");

        Result result = run("--data", data, "delete", "99");
        assertEquals(3, result.exitCode());
        assertEquals("", result.stdout());
        assertEquals(true, result.stderr().startsWith("error: "));
    }

    @Test
    @DisplayName("入力エラーは終了コード 2 で、データファイルは作られない")
    void invalidInputReturns2AndDoesNotTouchData() {
        Path file = tempDir.resolve("never.csv");
        Result result = run("--data", file.toString(), "add", "2026-01-01", "expense", "food", "0");

        assertEquals(2, result.exitCode());
        assertEquals("", result.stdout());
        assertEquals(true, result.stderr().startsWith("error: "));
        assertFalse(Files.exists(file));
    }

    @Test
    @DisplayName("--data の後ろに置いた --data は入力エラー（終了コード 2）")
    void dataOptionAfterCommandReturns2() {
        assertEquals(2, run("list", "--data", tempDir.resolve("x.csv").toString()).exitCode());
        assertEquals(2, run("add", "2026-01-01", "expense", "food", "10", "--data").exitCode());
    }

    @Test
    @DisplayName("保存先の親ディレクトリが無ければ終了コード 1")
    void missingParentDirectoryReturns1() {
        Path file = tempDir.resolve("no-dir").resolve("l.csv");
        Result result = run("--data", file.toString(), "add", "2026-01-01", "expense", "food", "10");
        assertEquals(1, result.exitCode());
        assertEquals("", result.stdout());
    }

    @Test
    @DisplayName("壊れたファイルや、ディレクトリを指定した場合は終了コード 1")
    void brokenOrDirectoryReturns1() throws IOException {
        Path broken = tempDir.resolve("broken.csv");
        Files.writeString(broken, "#next_id=3\n1,2026-01-10,expense,\"food,1200.50,\n", StandardCharsets.UTF_8);
        assertEquals(1, run("--data", broken.toString(), "list").exitCode());

        Path dir = Files.createDirectory(tempDir.resolve("adir"));
        assertEquals(1, run("--data", dir.toString(), "list").exitCode());
    }

    @Test
    @DisplayName("引数の検証はデータ読込より先（壊れたファイルでも不正引数は 2）")
    void argumentValidationComesFirst() throws IOException {
        Path broken = tempDir.resolve("broken2.csv");
        Files.writeString(broken, "garbage\n", StandardCharsets.UTF_8);
        assertEquals(2, run("--data", broken.toString(), "delete", "abc").exitCode());
        assertEquals(2, run("--data", broken.toString(), "summary").exitCode());
    }

    @Test
    @DisplayName("一覧は該当 0 件でもヘッダー行を出す（list --month）")
    void listHeaderOnlyWhenNoMatch() {
        String data = tempDir.resolve("month.csv").toString();
        run("--data", data, "add", "2026-01-01", "income", "x", "1");
        assertEquals("id\tdate\ttype\tcategory\tamount\tmemo\n",
                run("--data", data, "list", "--month", "2026-02").stdout());
    }

    @Test
    @DisplayName("コマンド未指定と未知のコマンドは終了コード 2")
    void unknownOrMissingCommandReturns2() {
        assertEquals(2, run().exitCode());
        assertEquals(2, run("frobnicate").exitCode());
    }
}
