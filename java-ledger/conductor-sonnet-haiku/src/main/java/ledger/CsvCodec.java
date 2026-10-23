package ledger;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV 1 行の組み立てと分解を行うクラス（RFC 4180 風）。
 * フィールドにカンマかダブルクォートを含む場合のみ全体を " で囲み、中の " は "" にする。
 * それ以外のフィールドはクォートしない。
 */
public final class CsvCodec {

    private CsvCodec() {
    }

    /** 複数フィールドを 1 行にまとめる（改行は付けない） */
    public static String encodeLine(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(encodeField(fields.get(i)));
        }
        return sb.toString();
    }

    /** 1 フィールドを必要な場合だけクォートする */
    static String encodeField(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /**
     * CSV 1 行を分解してフィールドのリストを返す。
     * 形式が不正な場合（閉じられていない引用符、引用符の後の余分な文字など）は {@link DataFileException}。
     */
    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        int i = 0;
        int n = line.length();
        while (true) {
            StringBuilder sb = new StringBuilder();
            if (i < n && line.charAt(i) == '"') {
                // 引用符で囲まれたフィールド。"" は " として読む
                i++;
                boolean closed = false;
                while (i < n) {
                    char c = line.charAt(i);
                    if (c == '"') {
                        if (i + 1 < n && line.charAt(i + 1) == '"') {
                            sb.append('"');
                            i += 2;
                        } else {
                            closed = true;
                            i++;
                            break;
                        }
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                if (!closed) {
                    throw new DataFileException("CSV の引用符が閉じられていません: " + line);
                }
                if (i < n && line.charAt(i) != ',') {
                    throw new DataFileException("CSV の引用符の後に余分な文字があります: " + line);
                }
            } else {
                // クォートなしのフィールド。カンマまで読む
                while (i < n && line.charAt(i) != ',') {
                    char c = line.charAt(i);
                    if (c == '"') {
                        throw new DataFileException("CSV のクォート位置が不正です: " + line);
                    }
                    sb.append(c);
                    i++;
                }
            }
            fields.add(sb.toString());
            if (i >= n) {
                break;
            }
            // ここでは line.charAt(i) == ',' なので読み飛ばす。末尾のカンマは空フィールドになる
            i++;
        }
        return fields;
    }
}
