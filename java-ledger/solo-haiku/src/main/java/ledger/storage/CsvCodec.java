package ledger.storage;

import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.domain.FieldRules;
import ledger.domain.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 1 件分の記録と CSV 行の相互変換。
 * 列は {@code id,date,type,category,amount,memo} の 6 列で、カンマ・ダブルクォートを含む欄は
 * RFC 4180 風に {@code "} で囲み、中の {@code "} は {@code ""} にする。
 */
final class CsvCodec {

    private static final int COLUMNS = 6;

    private CsvCodec() {
    }

    static String encode(Entry entry) {
        return String.join(",",
                Long.toString(entry.id()),
                entry.date().toString(),
                entry.type().word(),
                quote(entry.category()),
                Money.format(entry.amountCents()),
                quote(entry.memo()));
    }

    /**
     * CSV 行を記録に戻す。形式が不正・値が規則外なら空。
     */
    static Optional<Entry> decode(String line) {
        Optional<List<String>> parsed = splitFields(line);
        if (parsed.isEmpty() || parsed.get().size() != COLUMNS) {
            return Optional.empty();
        }
        List<String> f = parsed.get();
        Optional<Long> id = FieldRules.id(f.get(0));
        Optional<LocalDate> date = FieldRules.date(f.get(1));
        Optional<EntryType> type = FieldRules.type(f.get(2));
        Optional<Long> amount = Money.parseCents(f.get(4));
        String category = f.get(3);
        String memo = f.get(5);
        if (id.isEmpty() || date.isEmpty() || type.isEmpty() || amount.isEmpty()
                || !FieldRules.isValidCategory(category) || !FieldRules.isPlainText(memo)) {
            return Optional.empty();
        }
        return Optional.of(new Entry(id.get(), date.get(), type.get(), category, amount.get(), memo));
    }

    /** 欄をクォートが必要な場合だけ {@code "} で囲む */
    static String quote(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * 1 行を欄に分解する。クォートの閉じ忘れや、閉じクォートの後ろに余計な文字がある行は不正として空を返す。
     */
    static Optional<List<String>> splitFields(String line) {
        List<String> fields = new ArrayList<>();
        int pos = 0;
        int length = line.length();
        while (true) {
            StringBuilder field = new StringBuilder();
            if (pos < length && line.charAt(pos) == '"') {
                pos++;
                boolean closed = false;
                while (pos < length) {
                    char c = line.charAt(pos);
                    if (c != '"') {
                        field.append(c);
                        pos++;
                    } else if (pos + 1 < length && line.charAt(pos + 1) == '"') {
                        // 「""」は「"」1 文字
                        field.append('"');
                        pos += 2;
                    } else {
                        closed = true;
                        pos++;
                        break;
                    }
                }
                if (!closed || (pos < length && line.charAt(pos) != ',')) {
                    return Optional.empty();
                }
            } else {
                while (pos < length && line.charAt(pos) != ',') {
                    if (line.charAt(pos) == '"') {
                        return Optional.empty();
                    }
                    field.append(line.charAt(pos));
                    pos++;
                }
            }
            fields.add(field.toString());
            if (pos >= length) {
                break;
            }
            // カンマを読み飛ばして次の欄へ（末尾のカンマなら空の欄が 1 つ追加される）
            pos++;
        }
        return Optional.of(fields);
    }
}
