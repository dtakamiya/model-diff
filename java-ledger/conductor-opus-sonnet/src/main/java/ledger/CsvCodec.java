package ledger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** CSV（RFC 4180 風クォート）の整形とパース。ファイル IO は含まない。 */
public final class CsvCodec {
    private static final Pattern HEADER_RE = Pattern.compile("#next_id=([1-9][0-9]*)");

    private CsvCodec() {
    }

    // ---------- 書き出し ----------

    public static String serialize(LedgerData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("#next_id=").append(data.nextId()).append('\n');
        for (Entry e : data.entries()) {
            sb.append(e.id()).append(',')
              .append(e.date()).append(',')
              .append(e.type().label()).append(',')
              .append(quote(e.category())).append(',')
              .append(Money.format(e.amount())).append(',')
              .append(quote(e.memo())).append('\n');
        }
        return sb.toString();
    }

    /** カンマまたはダブルクォートを含む場合のみ全体を " で囲み、中の " を "" にする。 */
    static String quote(String s) {
        if (s.indexOf(',') < 0 && s.indexOf('"') < 0) {
            return s;
        }
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    // ---------- 読み込み ----------

    /** 内容が不正なら IO エラー（終了コード 1）。空文字列は「データなし」。 */
    public static LedgerData parse(String text) {
        if (text.isEmpty()) {
            return LedgerData.empty();
        }
        String[] lines = text.split("\n", -1);
        // 末尾が LF で終わる場合、最後の空要素は無視する
        int count = lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;

        Matcher m = HEADER_RE.matcher(lines[0]);
        if (!m.matches()) {
            throw corrupt("missing or invalid #next_id header");
        }
        long nextId;
        try {
            nextId = Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            throw corrupt("invalid #next_id");
        }

        List<Entry> entries = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 1; i < count; i++) {
            Entry e = parseLine(lines[i], i + 1);
            // ID は next_id 未満かつ重複なし
            if (e.id() >= nextId || !seen.add(e.id())) {
                throw corrupt("line " + (i + 1) + ": inconsistent id");
            }
            entries.add(e);
        }
        return new LedgerData(nextId, entries);
    }

    private static Entry parseLine(String line, int lineNo) {
        List<String> f = splitFields(line, lineNo);
        if (f.size() != 6) {
            throw corrupt("line " + lineNo + ": expected 6 fields");
        }
        try {
            return new Entry(Validator.id(f.get(0)), Validator.date(f.get(1)),
                    Validator.type(f.get(2)), Validator.category(f.get(3)),
                    Validator.amount(f.get(4)), Validator.memo(f.get(5)));
        } catch (LedgerException e) {
            // 検証エラー（入力エラー）をデータ破損として扱い直す
            throw corrupt("line " + lineNo + ": " + e.getMessage());
        }
    }

    /** クォートを解釈しながら 1 行をフィールドに分割する。 */
    static List<String> splitFields(String line, int lineNo) {
        List<String> fields = new ArrayList<>();
        int i = 0;
        int n = line.length();
        while (true) {
            StringBuilder sb = new StringBuilder();
            if (i < n && line.charAt(i) == '"') {
                i++;
                boolean closed = false;
                while (i < n) {
                    char c = line.charAt(i);
                    if (c == '"') {
                        if (i + 1 < n && line.charAt(i + 1) == '"') {
                            sb.append('"');
                            i += 2;
                        } else {
                            i++;
                            closed = true;
                            break;
                        }
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                if (!closed) {
                    throw corrupt("line " + lineNo + ": unterminated quote");
                }
                if (i < n && line.charAt(i) != ',') {
                    throw corrupt("line " + lineNo + ": garbage after closing quote");
                }
            } else {
                while (i < n && line.charAt(i) != ',') {
                    if (line.charAt(i) == '"') {
                        throw corrupt("line " + lineNo + ": stray quote");
                    }
                    sb.append(line.charAt(i++));
                }
            }
            fields.add(sb.toString());
            if (i >= n) {
                return fields;
            }
            i++; // カンマを読み飛ばす（直後が行末なら次は空フィールド）
        }
    }

    private static LedgerException corrupt(String detail) {
        return LedgerException.io("corrupt data file: " + detail, null);
    }
}
