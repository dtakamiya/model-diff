package ledger;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ledger と CSV テキストの相互変換。形式不正は IllegalArgumentException。 */
public final class CsvCodec {
    private static final Pattern HEADER = Pattern.compile("#next_id=([1-9][0-9]*)");
    private static final Pattern ID = Pattern.compile("[1-9][0-9]*");
    private static final Pattern DATE = Pattern.compile("([0-9]{4})-([0-9]{2})-([0-9]{2})");
    private static final Pattern AMOUNT = Pattern.compile("[0-9]+\\.[0-9]{2}");
    private static final int FIELD_COUNT = 6;

    private CsvCodec() {
    }

    public static String encode(Ledger ledger) {
        StringBuilder sb = new StringBuilder();
        sb.append("#next_id=").append(ledger.nextId()).append('\n');
        for (Entry e : ledger.entries()) {
            sb.append(e.id()).append(',')
                    .append(e.date()).append(',')
                    .append(e.type().label()).append(',')
                    .append(quote(e.category())).append(',')
                    .append(Money.format(e.amountCents())).append(',')
                    .append(quote(e.memo())).append('\n');
        }
        return sb.toString();
    }

    /** カンマまたはダブルクォートを含む場合のみ全体を囲み、中の " は "" にする。 */
    static String quote(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    public static Ledger decode(String text) {
        if (text.isEmpty()) {
            return Ledger.empty();
        }
        String[] lines = text.split("\n", -1);
        Matcher header = HEADER.matcher(lines[0]);
        if (!header.matches()) {
            throw new IllegalArgumentException("1 行目が #next_id=<ID> ではありません");
        }
        long nextId = parseLong(header.group(1));

        List<Entry> entries = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            // split の最後の要素は最終行の改行の後ろ（空）なので、空行はそこでのみ許す
            if (lines[i].isEmpty() && i == lines.length - 1) {
                break;
            }
            Entry entry = parseEntry(lines[i], i + 1);
            if (entry.id() >= nextId) {
                throw new IllegalArgumentException((i + 1) + " 行目: ID が #next_id 以上です");
            }
            entries.add(entry);
        }
        return new Ledger(entries, nextId);
    }

    private static Entry parseEntry(String line, int lineNo) {
        List<String> f = splitFields(line, lineNo);
        if (f.size() != FIELD_COUNT) {
            throw new IllegalArgumentException(lineNo + " 行目: フィールド数が " + FIELD_COUNT + " ではありません");
        }
        EntryType type = EntryType.fromLabel(f.get(2));
        Long cents = AMOUNT.matcher(f.get(4)).matches() ? Money.parsePositive(f.get(4)) : null;
        if (!ID.matcher(f.get(0)).matches() || type == null || cents == null || f.get(3).isEmpty()) {
            throw new IllegalArgumentException(lineNo + " 行目: 値が不正です");
        }
        return new Entry(parseLong(f.get(0)), parseDate(f.get(1), lineNo), type, f.get(3), cents, f.get(5));
    }

    private static LocalDate parseDate(String text, int lineNo) {
        Matcher m = DATE.matcher(text);
        if (m.matches()) {
            try {
                return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)));
            } catch (DateTimeException ignored) {
                // 下で形式不正として扱う
            }
        }
        throw new IllegalArgumentException(lineNo + " 行目: 日付が不正です");
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("数値が範囲外です: " + s);
        }
    }

    /** RFC 4180 風に 1 行をフィールドへ分割する。 */
    private static List<String> splitFields(String line, int lineNo) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        int n = line.length();
        while (true) {
            if (i < n && line.charAt(i) == '"') {
                i = readQuoted(line, i + 1, cur, lineNo);
                if (i < n && line.charAt(i) != ',') {
                    throw new IllegalArgumentException(lineNo + " 行目: クォートの後ろに不正な文字があります");
                }
            } else {
                while (i < n && line.charAt(i) != ',') {
                    if (line.charAt(i) == '"') {
                        throw new IllegalArgumentException(lineNo + " 行目: 不正なクォートです");
                    }
                    cur.append(line.charAt(i++));
                }
            }
            fields.add(cur.toString());
            cur.setLength(0);
            if (i >= n) {
                return fields;
            }
            i++; // 区切りのカンマを読み飛ばす
        }
    }

    /** クォート内を読み、閉じクォートの次の位置を返す。 */
    private static int readQuoted(String line, int start, StringBuilder out, int lineNo) {
        int i = start;
        while (i < line.length()) {
            char c = line.charAt(i++);
            if (c != '"') {
                out.append(c);
            } else if (i < line.length() && line.charAt(i) == '"') {
                out.append('"');
                i++;
            } else {
                return i;
            }
        }
        throw new IllegalArgumentException(lineNo + " 行目: クォートが閉じていません");
    }
}
