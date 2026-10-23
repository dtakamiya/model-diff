package ledger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CSV データファイルの読み書きを担当するリポジトリ。
 *
 * <p>形式: 1 行目は {@code #next_id=N}、2 行目以降は 1 件 1 行の 6 フィールド
 * （{@code id,date,type,category,amount,memo}、ヘッダーなし）。UTF-8・LF 改行。
 * category と memo は、カンマか {@code "} を含むときだけ {@code "} で囲む（中の {@code "} は {@code ""}）。
 */
final class LedgerFile {

    private static final Pattern HEADER = Pattern.compile("#next_id=([1-9][0-9]*)");
    private static final int FIELD_COUNT = 6;

    private final Path path;

    LedgerFile(Path path) {
        this.path = path;
    }

    /**
     * ファイルを読み込む。ファイル無し・0 バイトは空データとして扱う。
     * 読めない・形式が壊れている場合は {@link LedgerIOException}。
     */
    LedgerData load() {
        try {
            if (Files.isDirectory(path)) {
                throw new LedgerIOException("データファイルの位置がディレクトリです: " + path);
            }
            if (!Files.exists(path)) {
                return LedgerData.empty();
            }
            String text = readText();
            if (text.isEmpty()) {
                return LedgerData.empty();
            }
            return parse(text);
        } catch (SecurityException | UncheckedIOException e) {
            // 権限などで JDK が投げる想定外の実行時例外も IO エラーに揃える（業務の例外はそのまま通す）
            throw new LedgerIOException("データファイルを読み込めません: " + path, e);
        }
    }

    /**
     * データを保存する。一時ファイルに書いてから置き換えるので、途中で失敗しても元ファイルは壊れない。
     * 親ディレクトリは作らない（無ければ IO エラー）。
     */
    void save(LedgerData data) {
        String text = render(data);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | SecurityException | UncheckedIOException e) {
            deleteQuietly(tmp);
            throw new LedgerIOException("データファイルを保存できません（保存先ディレクトリや権限を確認してください）: " + path, e);
        }
    }

    /** ファイルをバイト列として読み、UTF-8 として厳密にデコードする。 */
    private String readText() {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new LedgerIOException("データファイルを読み込めません: " + path, e);
        }
        try {
            // 不正なバイト列を黙って置換せず、破損としてエラーにする
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new LedgerIOException("データファイルが UTF-8 ではありません: " + path, e);
        }
    }

    /** テキスト全体を LedgerData に変換する。形式不正はデータ破損（IO エラー）として扱う。 */
    private static LedgerData parse(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        // 末尾の改行による空要素は行として数えない
        if (lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }

        long nextId = parseHeader(lines.get(0));
        List<Entry> entries = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            int lineNo = i + 1;
            Entry entry = parseEntry(lines.get(i), lineNo);
            // 採番済みの ID より大きい・重複する ID は整合性が崩れているので破損扱い
            if (entry.id() >= nextId) {
                throw corrupted(lineNo, "ID が #next_id 以上です");
            }
            if (!ids.add(entry.id())) {
                throw corrupted(lineNo, "ID が重複しています");
            }
            entries.add(entry);
        }
        return new LedgerData(nextId, entries);
    }

    private static long parseHeader(String line) {
        Matcher m = HEADER.matcher(line);
        if (!m.matches()) {
            throw new LedgerIOException("データファイルの 1 行目が #next_id=<数値> の形式ではありません");
        }
        try {
            return Validator.id(m.group(1));
        } catch (InputException e) {
            throw new LedgerIOException("データファイルの #next_id が範囲外です", e);
        }
    }

    private static Entry parseEntry(String line, int lineNo) {
        List<String> f = splitCsvLine(line, lineNo);
        if (f.size() != FIELD_COUNT) {
            throw corrupted(lineNo, "フィールド数が " + FIELD_COUNT + " ではありません");
        }
        try {
            // 保存時と同じ検証規則で読み直す
            return new Entry(
                    Validator.id(f.get(0)),
                    Validator.date(f.get(1)),
                    Validator.type(f.get(2)),
                    Validator.category(f.get(3)),
                    Validator.amount(f.get(4)),
                    Validator.memo(f.get(5)));
        } catch (InputException e) {
            throw corrupted(lineNo, e.getMessage());
        }
    }

    /**
     * 1 行を CSV のフィールドに分解する（RFC 4180 風）。
     * クォートされた項目内のカンマはそのまま扱い、{@code ""} は {@code "} 1 文字とみなす。
     * 改行は値に含まれない前提（保存時に拒否しているため）なので、行単位で解析する。
     */
    private static List<String> splitCsvLine(String line, int lineNo) {
        List<String> fields = new ArrayList<>();
        int i = 0;
        int n = line.length();
        while (true) {
            StringBuilder field = new StringBuilder();
            if (i < n && line.charAt(i) == '"') {
                // クォートされた項目：閉じクォートまで読む
                i++;
                boolean closed = false;
                while (i < n) {
                    char c = line.charAt(i);
                    if (c != '"') {
                        field.append(c);
                        i++;
                    } else if (i + 1 < n && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                    } else {
                        closed = true;
                        i++;
                        break;
                    }
                }
                if (!closed) {
                    throw corrupted(lineNo, "閉じられていないクォートがあります");
                }
                if (i < n && line.charAt(i) != ',') {
                    throw corrupted(lineNo, "クォートの直後に不正な文字があります");
                }
            } else {
                // クォートされていない項目：次のカンマまで読む
                while (i < n && line.charAt(i) != ',') {
                    char c = line.charAt(i);
                    if (c == '"') {
                        throw corrupted(lineNo, "クォートされていない項目に \" が含まれています");
                    }
                    field.append(c);
                    i++;
                }
            }
            fields.add(field.toString());
            if (i >= n) {
                break;
            }
            i++; // 区切りのカンマを読み飛ばす（行末のカンマなら次の反復で空項目になる）
        }
        return fields;
    }

    /** 保存用の全文を組み立てる。 */
    static String render(LedgerData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("#next_id=").append(data.nextId()).append('\n');
        for (Entry e : data.entries()) {
            sb.append(e.id()).append(',')
              .append(e.date()).append(',')
              .append(e.type()).append(',')
              .append(quote(e.category())).append(',')
              .append(Money.format(e.amount())).append(',')
              .append(quote(e.memo())).append('\n');
        }
        return sb.toString();
    }

    /** カンマか {@code "} を含むときだけ囲み、中の {@code "} を {@code ""} にする。 */
    static String quote(String s) {
        if (s.indexOf(',') < 0 && s.indexOf('"') < 0) {
            return s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static LedgerIOException corrupted(int lineNo, String reason) {
        return new LedgerIOException("データファイルの " + lineNo + " 行目が不正です: " + reason);
    }

    private static void deleteQuietly(Path tmp) {
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException | SecurityException ignored) {
            // 後始末の失敗は本来のエラーより優先しない
        }
    }
}
