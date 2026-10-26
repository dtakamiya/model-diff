package ledger;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * データファイル（CSV）の読み書きを担当するクラス。
 *
 * <p>ファイル形式（UTF-8・LF 改行）:
 * <pre>
 * #next_id=&lt;次に採番する ID&gt;
 * id,date,type,category,amount,memo
 * ...
 * </pre>
 * 存在しないファイル・0 バイトのファイルは「データなし（次の ID は 1）」として扱う。
 * 読み込めない・形式が壊れている場合は {@link DataFileException} を投げる。
 */
public final class LedgerRepository {

    /** 1 行目（ヘッダー）の形式。次の ID を取り出す */
    private static final Pattern HEADER = Pattern.compile("#next_id=([1-9][0-9]*)");
    /** レコード 1 行のフィールド数（id,date,type,category,amount,memo） */
    private static final int FIELD_COUNT = 6;

    private final Path path;

    public LedgerRepository(Path path) {
        this.path = path;
    }

    /** データファイルを読み込む。ファイルが無い・空の場合は空のデータを返す */
    public Ledger load() {
        if (Files.isDirectory(path)) {
            throw new DataFileException("データファイルがディレクトリを指しています: " + path);
        }
        try {
            if (!Files.exists(path) || Files.size(path) == 0) {
                return Ledger.empty();
            }
            return parse(Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DataFileException("データファイルを読み込めません: " + path, e);
        }
    }

    /**
     * データファイルへ書き出す（全体を上書き）。
     * 親ディレクトリは作らない。存在しない場合は {@link DataFileException} になる。
     */
    public void save(Ledger ledger) {
        try {
            Files.writeString(path, render(ledger), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataFileException("データファイルに書き込めません: " + path, e);
        }
    }

    /** 読み込んだ行の並びを Ledger にする。形式不正・ID の矛盾は壊れたデータとして扱う */
    private Ledger parse(List<String> lines) {
        if (lines.isEmpty()) {
            return Ledger.empty();
        }
        Matcher header = HEADER.matcher(lines.get(0));
        if (!header.matches()) {
            throw corrupted(1, "1 行目が #next_id=<数値> ではありません");
        }
        long nextId;
        try {
            nextId = Long.parseLong(header.group(1));
        } catch (NumberFormatException e) {
            throw corrupted(1, "次の ID が大きすぎます");
        }

        List<Entry> entries = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            int lineNo = i + 1;
            Entry entry = parseEntry(lines.get(i), lineNo);
            // ID は一意で、次の ID より小さくなければならない
            if (!seenIds.add(entry.id())) {
                throw corrupted(lineNo, "ID が重複しています: " + entry.id());
            }
            if (entry.id() >= nextId) {
                throw corrupted(lineNo, "ID が次の ID 以上です: " + entry.id());
            }
            entries.add(entry);
        }
        return new Ledger(nextId, entries);
    }

    /** CSV 1 行を Entry にする。各フィールドは入力時と同じ検証ルールで確認する */
    private Entry parseEntry(String line, int lineNo) {
        List<String> fields;
        try {
            fields = CsvCodec.parseLine(line);
        } catch (DataFileException e) {
            throw corrupted(lineNo, e.getMessage());
        }
        if (fields.size() != FIELD_COUNT) {
            throw corrupted(lineNo, "フィールド数が " + FIELD_COUNT + " ではありません");
        }
        try {
            long id = Validator.parseId(fields.get(0));
            LocalDate date = Validator.parseDate(fields.get(1));
            EntryType type = Validator.parseType(fields.get(2));
            String category = Validator.parseCategory(fields.get(3));
            BigDecimal amount = Validator.parseAmount(fields.get(4));
            String memo = Validator.parseMemo(fields.get(5));
            return new Entry(id, date, type, category, amount, memo);
        } catch (InputException e) {
            throw corrupted(lineNo, e.getMessage());
        }
    }

    /** Ledger を仕様の書式の文字列にする（LF 改行、金額は小数 2 桁） */
    private static String render(Ledger ledger) {
        StringBuilder sb = new StringBuilder();
        sb.append("#next_id=").append(ledger.nextId()).append('\n');
        for (Entry entry : ledger.entries()) {
            List<String> fields = List.of(
                    String.valueOf(entry.id()),
                    entry.date().toString(),
                    entry.type().label(),
                    entry.category(),
                    entry.amount().setScale(2).toPlainString(),
                    entry.memo());
            sb.append(CsvCodec.encodeLine(fields)).append('\n');
        }
        return sb.toString();
    }

    private DataFileException corrupted(int lineNo, String reason) {
        return new DataFileException("データファイルが壊れています（" + lineNo + " 行目: " + reason + "）: " + path);
    }
}
