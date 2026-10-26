package ledger.storage;

import ledger.ExitCode;
import ledger.LedgerException;
import ledger.domain.Entry;
import ledger.domain.FieldRules;
import ledger.domain.Ledger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CSV ファイルへの {@link Ledger} の読み書き。
 * <p>
 * 形式: 1 行目は {@code #next_id=<次の ID>}、2 行目以降は 1 件 1 行の CSV（LF 改行、UTF-8、ヘッダーなし）。
 * ファイルが無い・0 バイトの場合は空の家計簿として扱う。
 */
public final class CsvLedgerStore {

    private static final String NEXT_ID_PREFIX = "#next_id=";

    /**
     * 家計簿を読み込む。ファイルを作ったり書き換えたりはしない。
     *
     * @throws LedgerException 読めない・形式が壊れている場合（{@link ExitCode#IO_ERROR}）
     */
    public Ledger load(Path path) {
        if (!Files.exists(path)) {
            return Ledger.empty();
        }
        List<String> lines = readLines(path);
        if (lines.isEmpty()) {
            return Ledger.empty();
        }
        long nextId = parseNextIdLine(path, lines.get(0));
        List<Entry> entries = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            entries.add(parseEntryLine(path, i + 1, lines.get(i)));
        }
        try {
            return new Ledger(nextId, entries);
        } catch (IllegalArgumentException e) {
            throw corrupted(path, e.getMessage());
        }
    }

    /**
     * 家計簿を書き出す。親ディレクトリは作らない。
     *
     * @throws LedgerException 書けない場合（{@link ExitCode#IO_ERROR}）
     */
    public void save(Path path, Ledger ledger) {
        try {
            Files.writeString(path, render(ledger), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LedgerException(ExitCode.IO_ERROR, "データファイルに書き込めません: " + path);
        }
    }

    private static String render(Ledger ledger) {
        StringBuilder sb = new StringBuilder();
        sb.append(NEXT_ID_PREFIX).append(ledger.nextId()).append('\n');
        for (Entry entry : ledger.entries()) {
            sb.append(CsvCodec.encode(entry)).append('\n');
        }
        return sb.toString();
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LedgerException(ExitCode.IO_ERROR, "データファイルを読み込めません: " + path);
        }
    }

    private static long parseNextIdLine(Path path, String line) {
        if (!line.startsWith(NEXT_ID_PREFIX)) {
            throw corrupted(path, "1 行目が " + NEXT_ID_PREFIX + " で始まっていません");
        }
        Optional<Long> nextId = FieldRules.id(line.substring(NEXT_ID_PREFIX.length()));
        return nextId.orElseThrow(() -> corrupted(path, "次の ID が不正です"));
    }

    private static Entry parseEntryLine(Path path, int lineNumber, String line) {
        Optional<Entry> entry = CsvCodec.decode(line);
        return entry.orElseThrow(() -> corrupted(path, lineNumber + " 行目の形式が不正です"));
    }

    private static LedgerException corrupted(Path path, String detail) {
        return new LedgerException(ExitCode.IO_ERROR, "データファイルが壊れています: " + path + " (" + detail + ")");
    }
}
