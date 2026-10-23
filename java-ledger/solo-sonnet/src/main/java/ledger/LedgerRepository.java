package ledger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** CSV ファイルへの読み書き（UTF-8 固定）。 */
public final class LedgerRepository {
    private final Path path;

    public LedgerRepository(Path path) {
        this.path = path;
    }

    /** ファイルが無い・0 バイトならデータなしとして扱う。壊れていれば IO エラー。 */
    public Ledger load() throws LedgerException {
        if (!Files.exists(path)) {
            return Ledger.empty();
        }
        try {
            // 不正な UTF-8 は MalformedInputException（IOException）になる
            return CsvCodec.decode(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw LedgerException.io("データファイルを読み込めません: " + path, e);
        } catch (IllegalArgumentException e) {
            throw LedgerException.io("データファイルが壊れています: " + e.getMessage(), e);
        }
    }

    /** 親ディレクトリは作成しない（無ければ IO エラー）。 */
    public void save(Ledger ledger) throws LedgerException {
        try {
            Files.writeString(path, CsvCodec.encode(ledger), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw LedgerException.io("データファイルを書き込めません: " + path, e);
        }
    }
}
