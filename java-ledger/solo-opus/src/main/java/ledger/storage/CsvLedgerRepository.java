package ledger.storage;

import ledger.error.StorageException;
import ledger.model.Entry;
import ledger.model.FieldParser;
import ledger.model.LedgerData;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CSV ファイルに家計簿データを保存するリポジトリ。
 *
 * <pre>
 * #next_id=4
 * 1,2026-01-10,expense,food,1200.50,"lunch, with ""Taro"""
 * </pre>
 *
 * 1 行目に次の採番 ID、2 行目以降に {@code id,date,type,category,amount,memo} を 1 件 1 行で書く（UTF-8、LF 改行）。
 */
public final class CsvLedgerRepository implements LedgerRepository {

    private static final String NEXT_ID_PREFIX = "#next_id=";
    private static final int FIELD_COUNT = 6;

    private final Path file;

    public CsvLedgerRepository(Path file) {
        this.file = file;
    }

    @Override
    public LedgerData load() throws StorageException {
        // ファイルが無い・0 バイトなら「データなし」。ディレクトリ等は読み込みで IOException になる
        if (!Files.exists(file)) {
            return LedgerData.empty();
        }
        String content;
        try {
            content = decodeUtf8(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new StorageException("cannot read data file: " + file, e);
        }
        if (content.isEmpty()) {
            return LedgerData.empty();
        }
        try {
            return parse(content);
        } catch (IllegalArgumentException e) {
            throw new StorageException("data file is corrupted: " + file + " (" + e.getMessage() + ")", e);
        }
    }

    @Override
    public void save(LedgerData data) throws StorageException {
        // 親ディレクトリは作らない。存在しなければ書き込みが失敗し IO エラーになる
        try {
            Files.writeString(file, format(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("cannot write data file: " + file, e);
        }
    }

    /** 不正なバイト列を置換せずエラーにするため、厳格な UTF-8 デコーダを使う。 */
    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    /** ファイル全体を解析する。形式不正は {@link IllegalArgumentException}。 */
    static LedgerData parse(String content) {
        // 末尾の改行 1 つは行終端として扱い、それ以外の空行は不正とする
        String body = content.endsWith("\n") ? content.substring(0, content.length() - 1) : content;
        String[] lines = body.split("\n", -1);

        if (!lines[0].startsWith(NEXT_ID_PREFIX)) {
            throw new IllegalArgumentException("missing #next_id header");
        }
        long nextId = FieldParser.parseId(lines[0].substring(NEXT_ID_PREFIX.length()));

        List<Entry> entries = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        for (int i = 1; i < lines.length; i++) {
            Entry entry;
            try {
                entry = parseEntry(lines[i]);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("line " + (i + 1) + ": " + e.getMessage(), e);
            }
            if (!seenIds.add(entry.id())) {
                throw new IllegalArgumentException("line " + (i + 1) + ": duplicate id " + entry.id());
            }
            if (entry.id() >= nextId) {
                throw new IllegalArgumentException("line " + (i + 1) + ": id " + entry.id() + " is not below next_id");
            }
            entries.add(entry);
        }
        return new LedgerData(nextId, entries);
    }

    private static Entry parseEntry(String line) {
        List<String> f = CsvLine.split(line);
        if (f.size() != FIELD_COUNT) {
            throw new IllegalArgumentException("expected " + FIELD_COUNT + " fields but got " + f.size());
        }
        return new Entry(
                FieldParser.parseId(f.get(0)),
                FieldParser.parseDate(f.get(1)),
                FieldParser.parseType(f.get(2)),
                FieldParser.parseCategory(f.get(3)),
                FieldParser.parseAmount(f.get(4)),
                FieldParser.parseMemo(f.get(5)));
    }

    /** データをファイル内容の文字列に変換する。 */
    static String format(LedgerData data) {
        StringBuilder sb = new StringBuilder();
        sb.append(NEXT_ID_PREFIX).append(data.nextId()).append('\n');
        for (Entry e : data.entries()) {
            sb.append(e.id()).append(',')
                    .append(e.date()).append(',')
                    .append(e.type().label()).append(',')
                    .append(CsvLine.quote(e.category())).append(',')
                    .append(e.amount().toPlainString()).append(',')
                    .append(CsvLine.quote(e.memo())).append('\n');
        }
        return sb.toString();
    }
}
