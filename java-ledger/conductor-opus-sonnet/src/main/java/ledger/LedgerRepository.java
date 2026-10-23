package ledger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** CSV ファイルへの読み書き。IO 失敗はすべて終了コード 1 の例外に変換する。 */
public final class LedgerRepository {
    private final Path path;

    public LedgerRepository(Path path) {
        this.path = path;
    }

    /** ファイルが無い・0 バイトなら空データ。読み取りでファイルは作らない。 */
    public LedgerData load() {
        if (Files.notExists(path)) {
            return LedgerData.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            // 不正な UTF-8 は置換せずエラーにする
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return CsvCodec.parse(text);
        } catch (CharacterCodingException e) {
            throw LedgerException.io("data file is not valid UTF-8: " + path, e);
        } catch (IOException e) {
            throw LedgerException.io("cannot read data file: " + path + " (" + e.getMessage() + ")", e);
        }
    }

    /** 同じディレクトリの一時ファイルに書いてから置き換える。親ディレクトリは作らない。 */
    public void save(LedgerData data) {
        Path abs = path.toAbsolutePath();
        Path dir = abs.getParent();
        Path tmp = null;
        try {
            tmp = Files.createTempFile(dir, ".ledger-", ".tmp");
            Files.write(tmp, CsvCodec.serialize(data).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // 後始末の失敗は無視する
                }
            }
            throw LedgerException.io("cannot write data file: " + path + " (" + e.getMessage() + ")", e);
        }
    }
}
