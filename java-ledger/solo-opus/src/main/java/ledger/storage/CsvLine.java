package ledger.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV 1 行分のエンコード・デコード（RFC 4180 風）。
 * カンマまたはダブルクォートを含むフィールドだけを {@code "} で囲み、中の {@code "} は {@code ""} にする。
 * 改行を含むフィールドは扱わない（検証で事前に弾いている前提）。
 */
final class CsvLine {

    private CsvLine() {
    }

    /** 必要な場合だけクォートしてフィールドを 1 つ書き出す。 */
    static String quote(String field) {
        if (field.indexOf(',') < 0 && field.indexOf('"') < 0) {
            return field;
        }
        return '"' + field.replace("\"", "\"\"") + '"';
    }

    /**
     * 1 行をフィールドに分解する。
     *
     * @throws IllegalArgumentException クォートの対応が取れないなど形式が不正な場合
     */
    static List<String> split(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        while (true) {
            current.setLength(0);
            if (i < line.length() && line.charAt(i) == '"') {
                // クォートされたフィールド：閉じクォートまで読み、"" は " 1 文字に戻す
                i++;
                while (true) {
                    if (i >= line.length()) {
                        throw new IllegalArgumentException("unterminated quoted field");
                    }
                    char c = line.charAt(i++);
                    if (c != '"') {
                        current.append(c);
                    } else if (i < line.length() && line.charAt(i) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        break;
                    }
                }
                if (i < line.length() && line.charAt(i) != ',') {
                    throw new IllegalArgumentException("unexpected character after closing quote");
                }
            } else {
                // クォートなしのフィールド：次のカンマまで。途中の " は不正
                while (i < line.length() && line.charAt(i) != ',') {
                    char c = line.charAt(i++);
                    if (c == '"') {
                        throw new IllegalArgumentException("unexpected quote in unquoted field");
                    }
                    current.append(c);
                }
            }
            fields.add(current.toString());
            if (i >= line.length()) {
                return fields;
            }
            i++; // 区切りのカンマを読み飛ばす
        }
    }
}
