#!/usr/bin/env python3
"""Java CLI 家計簿 `ledger` の共通ブラックボックス受け入れテスト。

使い方: python3 run_acceptance.py <path/to/bin/ledger>
標準出力に JSON 配列のみを出力する。終了コードは常に 0。
期待値は ../spec.md の記述と一致させてある。
"""
import json
import os
import shutil
import subprocess
import sys
import random
import tempfile
import time

TIMEOUT_SEC = 20
HEADER = "id\tdate\ttype\tcategory\tamount\tmemo"
SUMMARY_HEAD = "expense_by_category"

CASES = []  # (id, name, func)


class CaseFail(Exception):
    pass


def case(case_id, name):
    def deco(func):
        CASES.append((case_id, name, func))
        return func
    return deco


class Env:
    """1 ケース専用の一時ディレクトリと bin 実行ヘルパー。"""

    def __init__(self, bin_path):
        self.bin = bin_path
        self.dir = tempfile.mkdtemp(prefix="ledger-acc-")
        # 存在しないパスを使う（mkstemp のような 0 バイトファイルは作らない）
        self.data = os.path.join(self.dir, "data.csv")
        self.last_elapsed = 0.0  # 直前の run() の実行秒数（性能メモ用。pass/fail には使わない）

    def close(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def run(self, *args, data=True, env_extra=None):
        cmd = [self.bin]
        if data is True:
            cmd += ["--data", self.data]
        elif data:
            cmd += ["--data", data]
        cmd += list(args)
        env = dict(os.environ)
        env.setdefault("LANG", "en_US.UTF-8")
        env.setdefault("LC_ALL", "en_US.UTF-8")
        if env_extra:
            env.update(env_extra)
        started = time.monotonic()
        try:
            p = subprocess.run(cmd, capture_output=True, timeout=TIMEOUT_SEC,
                               stdin=subprocess.DEVNULL, env=env)
            self.last_elapsed = time.monotonic() - started
        except subprocess.TimeoutExpired:
            raise CaseFail("timeout(%ds): %s" % (TIMEOUT_SEC, " ".join(cmd[1:])))
        except OSError as e:
            raise CaseFail("cannot execute %s: %s" % (self.bin, e))
        return (p.returncode,
                p.stdout.decode("utf-8", errors="replace"),
                p.stderr.decode("utf-8", errors="replace"))

    # --- 検証ヘルパー ---
    def ok(self, *args, **kw):
        """終了コード 0 を要求して stdout を返す。"""
        rc, out, err = self.run(*args, **kw)
        if rc != 0:
            raise CaseFail("%s: expected exit 0, got %d (stderr=%r)" % (_fmt(args), rc, err[:200]))
        return out

    def fail(self, code, *args, **kw):
        """終了コード code、stdout 空、stderr の 1 行目が 'error: ' で始まることを要求する。"""
        rc, out, err = self.run(*args, **kw)
        if rc != code:
            raise CaseFail("%s: expected exit %d, got %d (stdout=%r stderr=%r)"
                           % (_fmt(args), code, rc, out[:100], err[:100]))
        if out != "":
            raise CaseFail("%s: stdout must be empty on error, got %r" % (_fmt(args), out[:100]))
        lines = err.splitlines()
        if not lines or not lines[0].startswith("error: "):
            raise CaseFail("%s: stderr must start with 'error: ', got %r" % (_fmt(args), err[:100]))


def _fmt(args):
    return "ledger " + " ".join(repr(a) if (" " in a or a == "") else a for a in args)


def eq(actual, expected, label=""):
    if actual != expected:
        raise CaseFail("%sexpected %r, actual %r" % (label + ": " if label else "", expected, actual))


def lines_of(out):
    """末尾の改行 1 つだけを除いて行に分解する（タブ・行内の空白は保持）。"""
    if out == "":
        return []
    if not out.endswith("\n"):
        raise CaseFail("stdout must end with newline: %r" % out[-50:])
    return out[:-1].split("\n")


def tsv(*rows):
    return ["\t".join(r) for r in rows]


# ---------------------------------------------------------------- 正常系

@case("A01", "add は採番した ID 1 を標準出力に出す")
def a01(e):
    eq(e.ok("add", "2026-01-10", "expense", "food", "1200"), "1\n")


@case("A02", "連続 add で ID が 1,2,3 と採番される")
def a02(e):
    got = [e.ok("add", "2026-01-%02d" % d, "expense", "food", "100") for d in (1, 2, 3)]
    eq(got, ["1\n", "2\n", "3\n"])


@case("A03", "list の固定書式と並び順（日付昇順・同日は ID 昇順・memo 空は行末タブ）")
def a03(e):
    e.ok("add", "2026-01-10", "expense", "food", "1200.5", "lunch")
    e.ok("add", "2026-01-05", "income", "salary", "300000")
    e.ok("add", "2026-01-10", "expense", "transport", "300", "train")
    expected = [HEADER] + tsv(
        ("2", "2026-01-05", "income", "salary", "300000.00", ""),
        ("1", "2026-01-10", "expense", "food", "1200.50", "lunch"),
        ("3", "2026-01-10", "expense", "transport", "300.00", "train"),
    )
    eq(lines_of(e.ok("list")), expected)


@case("A04", "別プロセスで読み直しても保存したデータが残る（データファイルが作られる）")
def a04(e):
    e.ok("add", "2026-03-01", "expense", "rent", "80000", "march")
    if not os.path.isfile(e.data):
        raise CaseFail("data file was not created at the --data path")
    eq(lines_of(e.ok("list")),
       [HEADER] + tsv(("1", "2026-03-01", "expense", "rent", "80000.00", "march")))


@case("A05", "list --month は指定月のみを返す（月またぎ・年またぎを除外）")
def a05(e):
    e.ok("add", "2025-12-31", "expense", "food", "1")
    e.ok("add", "2026-01-01", "expense", "food", "2")
    e.ok("add", "2026-01-31", "income", "bonus", "3")
    e.ok("add", "2026-02-01", "expense", "food", "4")
    eq(lines_of(e.ok("list", "--month", "2026-01")),
       [HEADER] + tsv(("2", "2026-01-01", "expense", "food", "2.00", ""),
                      ("3", "2026-01-31", "income", "bonus", "3.00", "")))
    eq(lines_of(e.ok("list", "--month", "2027-01")), [HEADER], "no match month")


@case("A06", "delete は 'deleted <id>' を出し、list から消える")
def a06(e):
    e.ok("add", "2026-01-01", "expense", "a", "1")
    e.ok("add", "2026-01-02", "expense", "b", "2")
    eq(e.ok("delete", "1"), "deleted 1\n")
    eq(lines_of(e.ok("list")), [HEADER] + tsv(("2", "2026-01-02", "expense", "b", "2.00", "")))


@case("A07", "末尾の ID を削除しても次の add は ID を再利用しない")
def a07(e):
    e.ok("add", "2026-01-01", "expense", "a", "1")
    e.ok("add", "2026-01-02", "expense", "b", "2")
    e.ok("delete", "2")
    eq(e.ok("add", "2026-01-03", "expense", "c", "3"), "3\n")
    eq([r.split("\t")[0] for r in lines_of(e.ok("list"))[1:]], ["1", "3"])


@case("A08", "全件削除した後も採番が続く")
def a08(e):
    e.ok("add", "2026-01-01", "expense", "a", "1")
    e.ok("delete", "1")
    eq(lines_of(e.ok("list")), [HEADER], "list after delete-all")
    eq(e.ok("add", "2026-01-02", "expense", "b", "2"), "2\n")


@case("A09", "summary の収入・支出・残高とカテゴリ別支出（金額降順）")
def a09(e):
    e.ok("add", "2026-01-10", "expense", "food", "1200.5", "lunch")
    e.ok("add", "2026-01-05", "income", "salary", "300000")
    e.ok("add", "2026-01-11", "expense", "transport", "300")
    e.ok("add", "2026-01-12", "expense", "food", "0.5")
    e.ok("add", "2026-02-01", "expense", "other", "9999")
    expected = tsv(("month", "2026-01"), ("income", "300000.00"), ("expense", "1501.00"),
                   ("balance", "298499.00")) + [SUMMARY_HEAD] + tsv(
        ("food", "1201.00"), ("transport", "300.00"))
    eq(lines_of(e.ok("summary", "--month", "2026-01")), expected)


@case("A10", "summary のカテゴリ並び（同額はカテゴリ名昇順、収入カテゴリは含めない）")
def a10(e):
    e.ok("add", "2026-04-01", "expense", "zeta", "500")
    e.ok("add", "2026-04-02", "expense", "alpha", "500")
    e.ok("add", "2026-04-03", "expense", "mid", "700")
    e.ok("add", "2026-04-04", "income", "salary", "100000")
    e.ok("add", "2026-04-05", "expense", "beta", "500")
    expected = tsv(("month", "2026-04"), ("income", "100000.00"), ("expense", "2200.00"),
                   ("balance", "97800.00")) + [SUMMARY_HEAD] + tsv(
        ("mid", "700.00"), ("alpha", "500.00"), ("beta", "500.00"), ("zeta", "500.00"))
    eq(lines_of(e.ok("summary", "--month", "2026-04")), expected)


@case("A11", "summary: 該当データの無い月はすべて 0.00 でカテゴリ行は空")
def a11(e):
    e.ok("add", "2026-01-01", "expense", "food", "10")
    expected = tsv(("month", "2026-05"), ("income", "0.00"), ("expense", "0.00"),
                   ("balance", "0.00")) + [SUMMARY_HEAD]
    eq(lines_of(e.ok("summary", "--month", "2026-05")), expected)


@case("A12", "summary: 残高がマイナスのとき先頭に - が付く")
def a12(e):
    e.ok("add", "2026-06-01", "income", "gift", "100")
    e.ok("add", "2026-06-02", "expense", "game", "600.5")
    expected = tsv(("month", "2026-06"), ("income", "100.00"), ("expense", "600.50"),
                   ("balance", "-500.50")) + [SUMMARY_HEAD] + tsv(("game", "600.50"))
    eq(lines_of(e.ok("summary", "--month", "2026-06")), expected)


# ---------------------------------------------------------------- 金額

@case("A13", "金額は常に小数 2 桁で出力（1200→1200.00, 1200.5→1200.50, 1200.50→1200.50, 007→7.00）")
def a13(e):
    for amt in ("1200", "1200.5", "1200.50", "007"):
        e.ok("add", "2026-01-01", "expense", "x", amt)
    amounts = [r.split("\t")[4] for r in lines_of(e.ok("list"))[1:]]
    eq(amounts, ["1200.00", "1200.50", "1200.50", "7.00"])


@case("A14", "浮動小数誤差が出ない（0.10 を 3 回足して 0.30、0.1+0.2 系）")
def a14(e):
    for _ in range(3):
        e.ok("add", "2026-01-01", "income", "tip", "0.10")
    out = lines_of(e.ok("summary", "--month", "2026-01"))
    eq(out[1], "income\t0.30", "income")
    eq(out[3], "balance\t0.30", "balance")
    e2 = Env(e.bin)
    try:
        e2.ok("add", "2026-01-01", "expense", "a", "0.1")
        e2.ok("add", "2026-01-02", "expense", "a", "0.2")
        e2.ok("add", "2026-01-03", "expense", "a", "1234567.89")
        out2 = lines_of(e2.ok("summary", "--month", "2026-01"))
        eq(out2[2], "expense\t1234568.19", "expense")
    finally:
        e2.close()


@case("A15", "金額不正（0・0.00・負・小数3桁・非数値・1e3・1,200 等）は終了コード 2 で保存されない")
def a15(e):
    bad = ["0", "0.00", "-5", "-0.10", "12.345", "abc", "1e3", "1,200", "1200.", ".5", "+5", "",
           "1234567890123"]
    for amt in bad:
        e.fail(2, "add", "2026-01-01", "expense", "food", amt)
    eq(lines_of(e.ok("list")), [HEADER], "nothing must be stored")
    eq(e.ok("add", "2026-01-01", "expense", "food", "1"), "1\n", "id must not be consumed")


# ---------------------------------------------------------------- 入力検証

@case("A16", "日付不正（2026-02-30 等）は終了コード 2、実在日（2024-02-29）は成功")
def a16(e):
    for d in ("2026-02-30", "2026-13-01", "2026-00-10", "2026-01-32", "2026/01/05", "2026-1-5",
              "20260105", "2025-02-29", "abc", ""):
        e.fail(2, "add", d, "expense", "food", "100")
    eq(e.ok("add", "2024-02-29", "expense", "food", "100"), "1\n")


@case("A17", "type 不正（Income / transfer / 空）は終了コード 2")
def a17(e):
    for t in ("Income", "EXPENSE", "transfer", ""):
        e.fail(2, "add", "2026-01-01", t, "food", "100")
    eq(lines_of(e.ok("list")), [HEADER])


@case("A18", "category 空・タブ含み、memo にタブ・改行含みは終了コード 2")
def a18(e):
    e.fail(2, "add", "2026-01-01", "expense", "", "100")
    e.fail(2, "add", "2026-01-01", "expense", "fo\tod", "100")
    e.fail(2, "add", "2026-01-01", "expense", "food", "100", "a\tb")
    e.fail(2, "add", "2026-01-01", "expense", "food", "100", "a\nb")
    eq(lines_of(e.ok("list")), [HEADER])


@case("A19", "存在しない ID の delete は終了コード 3（データは変更されない）")
def a19(e):
    e.ok("add", "2026-01-01", "expense", "a", "1")
    e.fail(3, "delete", "99")
    e.fail(3, "delete", "1000000000000")
    eq(len(lines_of(e.ok("list"))), 2)
    e.ok("delete", "1")
    e.fail(3, "delete", "1")  # 二重削除


@case("A20", "delete の ID が不正（abc・0・-1・01・小数・オーバーフロー）は終了コード 2")
def a20(e):
    e.ok("add", "2026-01-01", "expense", "a", "1")
    for bad in ("abc", "0", "-1", "01", "1.5", "", "9223372036854775808"):
        e.fail(2, "delete", bad)
    eq(len(lines_of(e.ok("list"))), 2)


@case("A21", "引数の過不足・未知のコマンド・不正オプションは終了コード 2")
def a21(e):
    e.fail(2, "add", "2026-01-01", "expense", "food")          # 4 個未満
    e.fail(2, "add", "2026-01-01", "expense", "food", "1", "m", "extra")  # 6 個
    e.fail(2, "delete")
    e.fail(2, "delete", "1", "2")
    e.fail(2, "summary")                                       # --month 必須
    e.fail(2, "summary", "--month")
    e.fail(2, "summary", "--month", "2026-13")
    e.fail(2, "summary", "--month", "2026-1")
    e.fail(2, "list", "--month")
    e.fail(2, "list", "--month", "2026-00")
    e.fail(2, "list", "2026-01")
    e.fail(2, "list", "--bogus", "x")
    e.fail(2, "list", "--data", e.data)                        # --data はコマンドの前だけ
    e.fail(2, "frobnicate")
    e.fail(2)                                                  # コマンド無し
    e.fail(2, "--data", data=False)                            # --data の値なし
    if os.path.exists(e.data):
        raise CaseFail("data file must not be created by invalid invocations")


# ---------------------------------------------------------------- 文字列の扱い

@case("A22", "メモ・カテゴリにカンマとダブルクォートを含めても壊れない")
def a22(e):
    memo = 'he said "hi", then left'
    e.ok("add", "2026-01-01", "expense", "a,b", "10", memo)
    e.ok("add", "2026-01-02", "expense", 'q"uote', "20", '"')
    e.ok("add", "2026-01-03", "expense", "plain", "30", ",")
    expected = [HEADER] + tsv(
        ("1", "2026-01-01", "expense", "a,b", "10.00", memo),
        ("2", "2026-01-02", "expense", 'q"uote', "20.00", '"'),
        ("3", "2026-01-03", "expense", "plain", "30.00", ","))
    eq(lines_of(e.ok("list")), expected)
    # 書き戻し（delete）後も壊れない
    e.ok("delete", "3")
    eq(lines_of(e.ok("list")), expected[:3])
    summary = lines_of(e.ok("summary", "--month", "2026-01"))
    eq(summary[5:], tsv(("q\"uote", "20.00"), ("a,b", "10.00")))


@case("A23", "日本語・空白を含むカテゴリとメモ、空文字メモを扱える")
def a23(e):
    e.ok("add", "2026-01-01", "expense", "食費", "980", "昼ごはん 定食")
    e.ok("add", "2026-01-02", "expense", " sp ace ", "1", "")
    expected = [HEADER] + tsv(
        ("1", "2026-01-01", "expense", "食費", "980.00", "昼ごはん 定食"),
        ("2", "2026-01-02", "expense", " sp ace ", "1.00", ""))
    eq(lines_of(e.ok("list")), expected)


# ---------------------------------------------------------------- データファイル

@case("A24", "データファイル未存在・0 バイトでも list は空（ヘッダーのみ）、summary はゼロ")
def a24(e):
    eq(lines_of(e.ok("list")), [HEADER], "missing file list")
    eq(lines_of(e.ok("summary", "--month", "2026-01"))[1:4],
       ["income\t0.00", "expense\t0.00", "balance\t0.00"], "missing file summary")
    open(e.data, "wb").close()
    eq(lines_of(e.ok("list")), [HEADER], "empty file list")
    eq(e.ok("add", "2026-01-01", "expense", "a", "1"), "1\n", "add on empty file")


@case("A25", "--data を指定しないとき ~/.ledger.csv（user.home 直下）を使う")
def a25(e):
    home = os.path.join(e.dir, "home")
    os.mkdir(home)
    extra = {"HOME": home, "JAVA_TOOL_OPTIONS": "-Duser.home=" + home}
    rc, out, err = e.run("add", "2026-01-01", "expense", "a", "1", data=False, env_extra=extra)
    if rc != 0 or out != "1\n":
        raise CaseFail("add without --data: rc=%d stdout=%r stderr=%r" % (rc, out, err[:200]))
    if not os.path.isfile(os.path.join(home, ".ledger.csv")):
        raise CaseFail("%s/.ledger.csv was not created" % home)
    rc, out, err = e.run("list", data=False, env_extra=extra)
    eq(out.splitlines(), [HEADER, "1\t2026-01-01\texpense\ta\t1.00\t"])


@case("A26", "ファイル IO エラー（--data がディレクトリ・保存先ディレクトリ無し・壊れたファイル）は終了コード 1")
def a26(e):
    e.fail(1, "list", data=e.dir)
    e.fail(1, "add", "2026-01-01", "expense", "a", "1", data=e.dir)
    e.fail(1, "add", "2026-01-01", "expense", "a", "1",
           data=os.path.join(e.dir, "no-such-dir", "x.csv"))
    with open(e.data, "w", encoding="utf-8") as f:
        f.write("this is not a ledger file\n")
    e.fail(1, "list")
    e.fail(1, "summary", "--month", "2026-01")
    # 引数エラーはデータファイルの状態より優先される
    e.fail(2, "add", "2026-02-30", "expense", "a", "1")
    e.fail(2, "list", "--month", "bad", data=e.dir)


# ---------------------------------------------------------------- 意地悪ケース（A27〜）
# 大量データ・異常 CSV・境界値。期待値はすべて spec.md の記述から導く。
# データファイルは spec「データ保存」のファイル形式どおりに直接生成する。

LONG_MAX = 9223372036854775807
LARGE_ROWS = 20000


def cents_str(c):
    """銭（整数）を spec の金額表記（小数 2 桁・符号付き）に直す。"""
    sign = "-" if c < 0 else ""
    c = abs(c)
    return "%s%d.%02d" % (sign, c // 100, c % 100)


def csv_q(s):
    """spec: カンマまたはダブルクォートを含む場合のみ全体を " で囲み、中の " は "" にする。"""
    if "," in s or '"' in s:
        return '"' + s.replace('"', '""') + '"'
    return s


def csv_row(rid, date, typ, cat, cents, memo):
    return "%d,%s,%s,%s,%s,%s" % (rid, date, typ, csv_q(cat), cents_str(cents), csv_q(memo))


def write_ledger(e, next_id, rows, path=None):
    """spec のファイル形式（#next_id 行 + 1 件 1 行、LF、UTF-8）でデータファイルを直接作る。"""
    body = "#next_id=%d\n" % next_id + "".join(csv_row(*r) + "\n" for r in rows)
    with open(path or e.data, "wb") as f:
        f.write(body.encode("utf-8"))


def write_raw(e, content):
    with open(e.data, "wb") as f:
        f.write(content if isinstance(content, bytes) else content.encode("utf-8"))


def read_bytes(path):
    with open(path, "rb") as f:
        return f.read()


def list_expected(rows, month=None):
    """spec: 日付昇順・同日は ID 昇順（ID は数値比較）。"""
    sel = [r for r in rows if month is None or r[1].startswith(month)]
    sel.sort(key=lambda r: (r[1], r[0]))
    return [HEADER] + tsv(*[(str(r[0]), r[1], r[2], r[3], cents_str(r[4]), r[5]) for r in sel])


def utf16_key(s):
    """Java の String.compareTo（UTF-16 コードユニット順）と同じ並びになるキー。"""
    return s.encode("utf-16-be")


def summary_expected(rows, month):
    inc = sum(r[4] for r in rows if r[1].startswith(month) and r[2] == "income")
    exp = sum(r[4] for r in rows if r[1].startswith(month) and r[2] == "expense")
    by_cat = {}
    for r in rows:
        if r[1].startswith(month) and r[2] == "expense":
            by_cat[r[3]] = by_cat.get(r[3], 0) + r[4]
    cats = sorted(by_cat.items(), key=lambda kv: (-kv[1], utf16_key(kv[0])))
    return (tsv(("month", month), ("income", cents_str(inc)), ("expense", cents_str(exp)),
                ("balance", cents_str(inc - exp))) + [SUMMARY_HEAD]
            + tsv(*[(c, cents_str(v)) for c, v in cats]))


def make_large_rows(n, seed=20260101):
    """n 件の大量データ（ID 1..n）。カンマ・引用符・日本語・絵文字を含むカテゴリ/メモ、同額タイ付き。"""
    rnd = random.Random(seed)
    special_cats = ['a,b', 'q"uote', "食費", "交通費", "😀", "Ａ", " sp ", "Food", "food"]
    cats = ["cat%03d" % i for i in range(300)] + special_cats
    memos = ["", "lunch", "with, comma", 'say "hi"', "日本語メモ", "x" * 80, "😀 emoji"]
    rows = []
    for i in range(1, n + 1):
        date = "%s-%02d" % (rnd.choice(["2025-11", "2025-12", "2026-01", "2026-02", "2026-03"]), rnd.randint(1, 28))
        typ = "income" if rnd.random() < 0.25 else "expense"
        rows.append((i, date, typ, rnd.choice(cats), rnd.randint(1, 9999999), rnd.choice(memos)))
    # 同額タイ（2026-01 の支出）: カテゴリ名昇順の安定性を見る
    for k in range(60):
        rows[k] = (rows[k][0], "2026-01-15", "expense", "tie%03d" % (59 - k), 1234567, "tie")
    return rows


def timing_note(label, rows_count, secs):
    return "%s: %d rows, %.2fs" % (label, rows_count, secs)


@case("A27", "大量データ: 20,000 件の list が全件・日付昇順・同日は ID 数値昇順で正しい")
def a27(e):
    rows = make_large_rows(LARGE_ROWS)
    shuffled = rows[:]
    random.Random(1).shuffle(shuffled)  # ファイル上の行順は仕様で固定されていないので崩しておく
    write_ledger(e, LARGE_ROWS + 1, shuffled)
    out = e.ok("list")
    eq(lines_of(out), list_expected(rows))
    return timing_note("list", LARGE_ROWS, e.last_elapsed)


@case("A28", "大量データ: 20,000 件の list --month と summary（カテゴリ別は金額降順・同額はカテゴリ昇順）が正しい")
def a28(e):
    rows = make_large_rows(LARGE_ROWS)
    write_ledger(e, LARGE_ROWS + 1, rows)
    eq(lines_of(e.ok("list", "--month", "2026-01")), list_expected(rows, "2026-01"), "list --month")
    t_list = e.last_elapsed
    for month in ("2026-01", "2025-12", "2026-03"):
        eq(lines_of(e.ok("summary", "--month", month)), summary_expected(rows, month), "summary " + month)
    return "list --month %.2fs, summary %.2fs (%d rows)" % (t_list, e.last_elapsed, LARGE_ROWS)


@case("A29", "大量データ: 20,000 件のファイルへ add すると #next_id の値で採番され、既存の件数・内容が保たれる")
def a29(e):
    rows = make_large_rows(LARGE_ROWS)
    next_id = LARGE_ROWS + 5  # 末尾の ID を削除済みという状態（spec: 削除後も ID は再利用しない）
    write_ledger(e, next_id, rows)
    eq(e.ok("add", "2026-01-20", "expense", "新規", "12.5", "追加"), "%d\n" % next_id, "first add")
    t_add = e.last_elapsed
    eq(e.ok("add", "2026-01-21", "income", "bonus", "7"), "%d\n" % (next_id + 1), "second add")
    rows = rows + [(next_id, "2026-01-20", "expense", "新規", 1250, "追加"),
                   (next_id + 1, "2026-01-21", "income", "bonus", 700, "")]
    eq(lines_of(e.ok("list")), list_expected(rows))
    first = read_bytes(e.data).split(b"\n", 1)[0]
    eq(first, ("#next_id=%d" % (next_id + 2)).encode(), "#next_id after adds")
    return "add %.2fs (%d rows)" % (t_add, LARGE_ROWS)


@case("A30", "大量データ: 20,000 件から delete しても残りは無傷で、次の add は削除前の続き番号")
def a30(e):
    rows = make_large_rows(LARGE_ROWS)
    write_ledger(e, LARGE_ROWS + 1, rows)
    target = LARGE_ROWS // 2
    eq(e.ok("delete", str(target)), "deleted %d\n" % target)
    t_del = e.last_elapsed
    e.fail(3, "delete", str(target))
    rows = [r for r in rows if r[0] != target]
    eq(lines_of(e.ok("list")), list_expected(rows))
    eq(e.ok("delete", str(LARGE_ROWS)), "deleted %d\n" % LARGE_ROWS, "delete last id")
    eq(e.ok("add", "2026-02-01", "expense", "x", "1"), "%d\n" % (LARGE_ROWS + 1), "add after deleting last id")
    return "delete %.2fs (%d rows)" % (t_del, LARGE_ROWS)


# ---- 金額の境界

@case("A31", "金額の最大桁・合計精度（999999999999.99 の大量合計、0.01 の千件、-0.01 / 0.00 の残高）")
def a31(e):
    # spec: 金額は BigDecimal か銭の整数。整数部は最大 12 桁、double / float 禁止
    big = 99999999999999  # 999999999999.99 を銭にしたもの
    for _ in range(3):
        e.ok("add", "2026-01-01", "income", "big", "999999999999.99")
    eq(lines_of(e.ok("list"))[1].split("\t")[4], "999999999999.99", "max amount round trip")
    eq(lines_of(e.ok("summary", "--month", "2026-01")),
       tsv(("month", "2026-01"), ("income", "2999999999999.97"), ("expense", "0.00"),
           ("balance", "2999999999999.97")) + [SUMMARY_HEAD], "3 x max")
    e2 = Env(e.bin)
    try:
        rows = [(i, "2026-01-%02d" % (1 + i % 28), "income", "big", big, "") for i in range(1, 5001)]
        rows.append((5001, "2026-01-01", "expense", "tiny", 1, ""))
        rows += [(5001 + i, "2026-02-%02d" % (1 + i % 28), "income", "cent", 1, "") for i in range(1, 1001)]
        write_ledger(e2, 6002, rows)
        eq(lines_of(e2.ok("summary", "--month", "2026-01")), summary_expected(rows, "2026-01"), "5000 x max")
        eq(lines_of(e2.ok("summary", "--month", "2026-02")), summary_expected(rows, "2026-02"), "1000 x 0.01")
        eq(lines_of(e2.ok("summary", "--month", "2026-02"))[1], "income\t10.00", "1000 x 0.01 literal")
        e2.ok("add", "2026-03-01", "income", "a", "1.00")
        e2.ok("add", "2026-03-02", "expense", "a", "1.01")
        eq(lines_of(e2.ok("summary", "--month", "2026-03"))[3], "balance\t-0.01", "balance -0.01")
        e2.ok("add", "2026-04-01", "income", "a", "1.00")
        e2.ok("add", "2026-04-02", "expense", "a", "1")
        eq(lines_of(e2.ok("summary", "--month", "2026-04"))[3], "balance\t0.00", "balance zero has no sign")
        e2.ok("add", "2026-05-01", "income", "a", "0.01")
        for _ in range(3):
            e2.ok("add", "2026-05-02", "expense", "a", "999999999999.99")
        eq(lines_of(e2.ok("summary", "--month", "2026-05"))[3], "balance\t-2999999999999.96",
           "large negative balance")
    finally:
        e2.close()


@case("A32", "金額入力の境界: 12 桁・先頭ゼロ・0.01 は有効、13 桁・全角/非ASCII数字・空白・指数・1d 等は終了コード 2")
def a32(e):
    # spec: amount は [0-9]{1,12}(\.[0-9]{1,2})? に一致し、かつ値が 0 より大きい
    valid = [("000000000001", "1.00"), ("0000000000.01", "0.01"), ("999999999999", "999999999999.00"),
             ("999999999999.99", "999999999999.99"), ("0.01", "0.01"), ("0.1", "0.10"),
             ("100.0", "100.00"), ("000.5", "0.50"), ("1.50", "1.50")]
    for amt, _ in valid:
        e.ok("add", "2026-01-01", "expense", "x", amt)
    eq([r.split("\t")[4] for r in lines_of(e.ok("list"))[1:]], [v for _, v in valid])
    invalid = ["0000000000001", "9999999999999", "000000000000", "0.0", "00.00", "-0.01", "１２３", "١٢٣",
               "१२३", " 100", "100 ", " ", "1E3", "1e-2", "0x10", "1_000", "1.2.3", "--5", "5-", "1 000",
               "NaN", "Infinity", "1d", "1f", "1L", "100.001", "1,5", "１.５"]
    for amt in invalid:
        e.fail(2, "add", "2026-01-01", "expense", "x", amt)
    eq(len(lines_of(e.ok("list"))) - 1, len(valid), "invalid amounts must not be stored")
    eq(e.ok("add", "2026-01-01", "expense", "x", "1"), "%d\n" % (len(valid) + 1), "id not consumed")


# ---- 日付・月の境界

@case("A33", "日付の境界: 閏日（2000/1904/2024-02-29 有効、1900/2100 は無効）・30 日月の 31 日・全角/空白付きは不正")
def a33(e):
    # spec: yyyy-MM-dd かつ実在する暦日（グレゴリオ暦）。4桁-2桁-2桁の書式
    valid = ["2000-02-29", "1904-02-29", "2024-02-29", "2026-12-31", "9999-12-31", "1999-12-31"]
    for d in valid:
        e.ok("add", d, "expense", "d", "1")
    invalid = ["2100-02-29", "1900-02-29", "2026-02-29", "2026-04-31", "2026-06-31", "2026-09-31",
               "2026-11-31", "２０２６-01-05", "٢٠٢٦-٠١-٠٥", " 2026-01-05", "2026-01-05 ", "12026-01-05",
               "+2026-01-05", "2026-01-05T00:00", "2026-01-5", "2026-1-05", "26-01-05", "2026-001-05",
               "2026-01-005", "2026-00-01", "2026-01-00"]
    for d in invalid:
        e.fail(2, "add", d, "expense", "d", "1")
    got = lines_of(e.ok("list"))
    eq([r.split("\t")[1] for r in got[1:]], sorted(valid), "only valid dates stored, sorted")
    eq([r.split("\t")[1] for r in lines_of(e.ok("list", "--month", "2024-02"))[1:]], ["2024-02-29"], "month 2024-02")
    eq(lines_of(e.ok("list", "--month", "2024-03")), [HEADER], "month 2024-03")
    eq([r.split("\t")[1] for r in lines_of(e.ok("list", "--month", "2000-02"))[1:]], ["2000-02-29"], "month 2000-02")


@case("A34", "--month の境界: 2026-13・2026-1・全角・空白付き・日まで付き等は終了コード 2、--month 以外の引数も 2")
def a34(e):
    # spec: --month は yyyy-MM（月は 01〜12）。不正・値なし・--month 以外の引数は終了コード 2
    e.ok("add", "2026-01-31", "expense", "a", "5")
    bad = ["2026-13", "2026-00", "2026-1", "2026-001", "202601", "2026/01", "2026-01 ", " 2026-01",
           "２０２６-０１", "2026-1a", "26-01", "12026-01", "2026-01-01", "2026-", "-01", "",
           "٢٠٢٦-٠١"]
    for m in bad:
        e.fail(2, "list", "--month", m)
    for m in bad[:8] + ["", "2026-13"]:
        e.fail(2, "summary", "--month", m)
    e.fail(2, "list", "--month", "2026-01", "extra")
    e.fail(2, "list", "extra", "--month", "2026-01")
    eq([r.split("\t")[1] for r in lines_of(e.ok("list", "--month", "2026-01"))[1:]], ["2026-01-31"])
    eq(lines_of(e.ok("summary", "--month", "9999-12"))[1:4], ["income\t0.00", "expense\t0.00", "balance\t0.00"],
       "year 9999 is a valid yyyy-MM")


# ---- ID・#next_id の境界

@case("A35", "ID の境界: int 超え（2147483648）・long 上限付近の #next_id で add / delete できる")
def a35(e):
    # spec: ID は long 範囲（delete は 9223372036854775807 以下）、#next_id を保存・復元する
    big_next = LONG_MAX - 1
    write_ledger(e, big_next, [(2147483648, "2026-01-01", "expense", "x", 100, "")])
    eq(e.ok("add", "2026-01-02", "expense", "y", "2"), "%d\n" % big_next, "add uses #next_id")
    eq(lines_of(e.ok("list")),
       [HEADER] + tsv(("2147483648", "2026-01-01", "expense", "x", "1.00", ""),
                      (str(big_next), "2026-01-02", "expense", "y", "2.00", "")))
    e.fail(3, "delete", str(LONG_MAX))
    eq(e.ok("delete", str(big_next)), "deleted %d\n" % big_next)
    eq(e.ok("delete", "2147483648"), "deleted 2147483648\n")
    eq(lines_of(e.ok("list")), [HEADER])
    e2 = Env(e.bin)
    try:
        write_ledger(e2, 3000000000, [])
        eq(e2.ok("add", "2026-01-01", "expense", "a", "1"), "3000000000\n")
        eq(e2.ok("add", "2026-01-01", "expense", "a", "1"), "3000000001\n")
    finally:
        e2.close()


@case("A36", "#next_id が最大 ID+1 でなくても（欠番・全件削除済み）#next_id の値で採番される")
def a36(e):
    # spec: 「#next_id=<次に採番する ID>」を保存・復元。末尾 ID 削除後・全件削除後も続き番号
    write_ledger(e, 100, [(1, "2026-01-01", "expense", "a", 100, ""), (2, "2026-01-02", "expense", "a", 200, ""),
                          (5, "2026-01-03", "expense", "a", 300, "")])
    eq(e.ok("add", "2026-01-04", "expense", "a", "4"), "100\n")
    eq(e.ok("add", "2026-01-05", "expense", "a", "5"), "101\n")
    e.ok("delete", "101")
    eq(e.ok("add", "2026-01-06", "expense", "a", "6"), "102\n")
    e2 = Env(e.bin)
    try:
        write_raw(e2, "#next_id=7\n")  # 1 行だけ（全件削除済み）のファイル
        eq(lines_of(e2.ok("list")), [HEADER], "list of header-only file")
        eq(lines_of(e2.ok("summary", "--month", "2026-01"))[1:4],
           ["income\t0.00", "expense\t0.00", "balance\t0.00"], "summary of header-only file")
        e2.fail(3, "delete", "1")
        eq(e2.ok("add", "2026-01-01", "expense", "a", "1"), "7\n")
    finally:
        e2.close()


@case("A37", "同日の並びは ID の数値昇順（2,9,10,100）で、辞書順（10,100,2,9）にならない")
def a37(e):
    # spec: 日付昇順・同日は ID 昇順
    for i in range(12):
        e.ok("add", "2026-01-05", "expense", "c%d" % i, "1")
    eq([r.split("\t")[0] for r in lines_of(e.ok("list"))[1:]], [str(i) for i in range(1, 13)], "added ids")
    e2 = Env(e.bin)
    try:
        ids = [100, 10, 9, 2, 1]
        rows = [(i, "2026-01-05", "expense", "c", 100, "") for i in ids] + [(50, "2026-01-04", "income", "c", 100, "")]
        write_ledger(e2, 101, rows)
        eq([r.split("\t")[0] for r in lines_of(e2.ok("list"))[1:]], ["50", "1", "2", "9", "10", "100"])
    finally:
        e2.close()


@case("A38", "summary のカテゴリ順: 金額は数値降順、同額は UTF-16 コードユニット順（大小文字・絵文字・結合文字を区別）")
def a38(e):
    # spec: 金額の降順、同額は category の文字列昇順（Java の String.compareTo と同じ UTF-16 コードユニット順）
    spec_rows = []
    rid = 0

    def exp(cat, cents, date="2026-01-10"):
        nonlocal rid
        rid += 1
        spec_rows.append((rid, date, "expense", cat, cents, ""))

    for cat, cents in (("num9", 900), ("num10", 1000), ("num100", 10000)):
        exp(cat, cents)
    for cat in ("B", "a", "C", "b", "Z", "あ", "😀", "Ａ", " f", "f", "F", "f ", "é", "e\u0301", "漢"):
        exp(cat, 5000)
    exp("split", 2000)
    exp("split", 3000)  # 2 件合算して 50.00 になる同額タイ
    spec_rows.append((rid + 1, "2026-01-11", "income", "B", 999999, ""))   # 収入カテゴリは含めない
    spec_rows.append((rid + 2, "2026-02-01", "expense", "other-month", 9999999, ""))
    write_ledger(e, rid + 3, spec_rows)
    got = lines_of(e.ok("summary", "--month", "2026-01"))
    eq(got, summary_expected(spec_rows, "2026-01"))
    # 手書きの要点（UTF-16 順: 絵文字 D83D < 全角 A FF21、コードポイント順なら逆になる）
    cats = [ln.split("\t")[0] for ln in got[5:]]
    if cats.index("😀") > cats.index("Ａ"):
        raise CaseFail("😀 (U+D83D..) must sort before Ａ (U+FF21) in UTF-16 code unit order: %r" % cats)


# ---- ファイル形式（書き込み）

@case("A39", "書き込まれるファイルが spec のファイル形式とバイト一致（LF・BOM なし・クォート規則・#next_id 保持）")
def a39(e):
    # spec: データ保存の例（使用例の 3 件 add 後）と同一内容。クォートはカンマ/" を含む場合のみ
    e.ok("add", "2026-01-10", "expense", "food", "1200.5", 'lunch, with "Taro"')
    e.ok("add", "2026-01-05", "income", "salary", "300000")
    e.ok("add", "2026-01-10", "expense", "transport", "300", "train")
    example = ('#next_id=4\n'
               '1,2026-01-10,expense,food,1200.50,"lunch, with ""Taro"""\n'
               '2,2026-01-05,income,salary,300000.00,\n'
               '3,2026-01-10,expense,transport,300.00,train\n').encode("utf-8")
    eq(read_bytes(e.data), example, "file after 3 adds")
    e.ok("delete", "3")
    eq(read_bytes(e.data), example.replace(b"3,2026-01-10,expense,transport,300.00,train\n", b""),
       "file after delete (#next_id stays 4)")
    e.ok("add", "2026-01-11", "expense", "食 費", "5")
    e.ok("add", "2026-01-12", "expense", "a,b", "1.5", 'say "hi"')
    tail = read_bytes(e.data).split("\n".encode())[3:]
    eq(tail, ['4,2026-01-11,expense,食 費,5.00,'.encode("utf-8"),
              '5,2026-01-12,expense,"a,b",1.50,"say ""hi"""'.encode("utf-8"), b""], "unquoted vs quoted fields")
    eq(read_bytes(e.data).split(b"\n", 1)[0], b"#next_id=6")


# ---- 文字列の境界

@case("A40", "特殊文字の往復: 絵文字・結合文字・U+2028/U+0085/VT/FF・バックスラッシュ・シェル記号・\"\" ・先頭末尾空白")
def a40(e):
    # spec: category/memo はタブ・CR・LF 以外は可（前後空白は保持）。クォートは RFC 4180 風
    items = [
        ("😀🎉", "emoji 👨‍👩‍👧‍👦 zwj"),
        ("e\u0301", "cafe\u0301"),
        ("ls\u2028sep", "a\u2028b\u0085c\x0bd\x0ce"),
        ("back\\slash", "C:\\temp\\new\\n literal \\t"),
        ("$HOME `x` *", "'single' $(echo hi) ${PATH} * ? [a] ~ ; & | > <"),
        ("q", '""'),
        ("lead ", "  padded  "),
        ("sp", "   "),
        ("x", "#next_id=99"),
        ('"', ","),
        (",,,", '",",","'),
        ("漢字カナ", "全角　スペース"),
    ]
    rows = []
    for i, (cat, memo) in enumerate(items, 1):
        eq(e.ok("add", "2026-01-%02d" % i, "expense", cat, "1", memo), "%d\n" % i, "add #%d" % i)
        rows.append((i, "2026-01-%02d" % i, "expense", cat, 100, memo))
    eq(lines_of(e.ok("list")), list_expected(rows), "round trip")
    eq(lines_of(e.ok("summary", "--month", "2026-01")), summary_expected(rows, "2026-01"), "summary")
    e.ok("delete", "12")  # 書き戻しで壊れないこと
    eq(lines_of(e.ok("list")), list_expected(rows[:-1]), "after delete rewrite")


@case("A41", "長大な文字列: 5 万文字のメモ・5 千文字のカテゴリ（add の往復）と 30 万文字のメモ行（直接生成）")
def a41(e):
    # spec: category/memo に長さ制限はない
    memo = 'x,"y' * 12500
    cat = 'c,"' * 1700
    eq(e.ok("add", "2026-01-01", "expense", cat, "1", memo), "1\n")
    rows = [(1, "2026-01-01", "expense", cat, 100, memo)]
    eq(lines_of(e.ok("list")), list_expected(rows), "long add round trip")
    e2 = Env(e.bin)
    try:
        huge = "あ" * 300000
        rows2 = [(1, "2026-01-01", "expense", "h", 100, huge), (2, "2026-01-02", "income", "h", 200, "short")]
        write_ledger(e2, 3, rows2)
        eq(lines_of(e2.ok("list")), list_expected(rows2), "huge memo from file")
        eq(e2.ok("add", "2026-01-03", "expense", "h", "3"), "3\n")
        eq(lines_of(e2.ok("list")), list_expected(rows2 + [(3, "2026-01-03", "expense", "h", 300, "")]),
           "huge memo survives rewrite")
    finally:
        e2.close()


@case("A42", "文字コード非依存: C ロケール・既定文字コード非 UTF-8 でも標準出力とファイルは UTF-8（日本語・絵文字）")
def a42(e):
    # spec: 標準出力・ファイルともに、実行環境の既定文字コードに依存せず UTF-8 を明示すること
    rows = [(1, "2026-01-01", "expense", "食費😀", 12345, "昼ごはん 😀 é"),
            (2, "2026-01-02", "income", "給料", 300000, "")]
    write_ledger(e, 3, rows)
    hostile = {"LANG": "C", "LC_ALL": "C", "LC_CTYPE": "C",
               "JAVA_TOOL_OPTIONS": "-Dfile.encoding=COMPAT"}
    out = e.ok("list", env_extra=hostile)
    eq(lines_of(out), list_expected(rows), "list under C locale")
    got = lines_of(e.ok("summary", "--month", "2026-01", env_extra=hostile))
    eq(got, summary_expected(rows, "2026-01"), "summary under C locale")
    before = read_bytes(e.data)
    eq(e.ok("add", "2026-01-03", "expense", "ascii", "1", env_extra=hostile), "3\n")
    after = read_bytes(e.data)
    eq(after.split(b"\n", 1)[1].startswith(before.split(b"\n", 1)[1]), True,
       "existing UTF-8 rows must be rewritten byte-for-byte")
    after.decode("utf-8")  # 不正な UTF-8 になっていれば harness error ではなく失敗にする
    eq(read_bytes(e.data).split(b"\n", 1)[0], b"#next_id=4")


# ---- 異常な CSV（終了コード 1）

CORRUPT_FILES = [
    ("列数不足(5)", "#next_id=3\n1,2026-01-01,expense,food,1.00\n"),
    ("列数過多(7)", "#next_id=3\n1,2026-01-01,expense,food,1.00,memo,extra\n"),
    ("閉じていないクォート(memo)", '#next_id=3\n1,2026-01-01,expense,food,1.00,"unclosed\n'),
    ("閉じていないクォート(category)", '#next_id=3\n1,2026-01-01,expense,"food,1.00,memo\n'),
    ("2 行目だけ壊れている", "#next_id=3\n1,2026-01-01,expense,food,1.00,memo\n2,2026-01-02,expense,only\n"),
    ("1 列だけの行", "#next_id=3\ngarbage\n"),
    ("#next_id 行が無い", "1,2026-01-01,expense,food,1.00,memo\n"),
    ("#next_id が数値でない", "#next_id=abc\n"),
    ("#next_id が空", "#next_id=\n"),
    ("#next_id が負", "#next_id=-1\n"),
    ("#next_id が小数", "#next_id=1.5\n"),
    ("#next_id が大文字", "#NEXT_ID=3\n"),
    ("#next_id が 2 行ある", "#next_id=3\n#next_id=4\n"),
    ("バイナリ", bytes(range(256)) * 4),
]


@case("A43", "異常 CSV（列数・クォート・#next_id 不正・バイナリ）の list / summary は終了コード 1 でファイルを変更しない")
def a43(e):
    # spec: 読み込めない・壊れているファイル（形式不正）は IO エラー（終了コード 1）
    for label, content in CORRUPT_FILES:
        write_raw(e, content)
        before = read_bytes(e.data)
        try:
            e.fail(1, "list")
            e.fail(1, "summary", "--month", "2026-01")
        except CaseFail as ex:
            raise CaseFail("[%s] %s" % (label, ex))
        if read_bytes(e.data) != before:
            raise CaseFail("[%s] data file was modified by a read-only command" % label)


@case("A44", "異常 CSV に対する add / delete も終了コード 1 で、ファイルは破損したまま（バイト一致）")
def a44(e):
    # spec: 破損したデータファイルは終了コード 1。読み込み失敗時は保存に到達しない（上書きして壊さない）
    for label, content in [CORRUPT_FILES[0], CORRUPT_FILES[2], CORRUPT_FILES[6], CORRUPT_FILES[7]]:
        write_raw(e, content)
        before = read_bytes(e.data)
        try:
            e.fail(1, "add", "2026-01-01", "expense", "a", "1")
            e.fail(1, "delete", "1")
            e.fail(1, "delete", "99")  # 存在しない ID より、読み込みエラーが先
        except CaseFail as ex:
            raise CaseFail("[%s] %s" % (label, ex))
        if read_bytes(e.data) != before:
            raise CaseFail("[%s] corrupt data file was rewritten after an error exit" % label)


CORRUPT_VALUES = [
    ("日付が月 13/日 45", "1,2026-13-45,expense,food,1.00,m"),
    ("日付が文字列", "1,not-a-date,expense,food,1.00,m"),
    ("存在しない日 02-30", "1,2026-02-30,expense,food,1.00,m"),
    ("日付が空", "1,,expense,food,1.00,m"),
    ("金額が abc", "1,2026-01-01,expense,food,abc,m"),
    ("金額が空", "1,2026-01-01,expense,food,,m"),
    ("金額が小数 3 桁", "1,2026-01-01,expense,food,12.345,m"),
    ("金額が 1.2.3", "1,2026-01-01,expense,food,1.2.3,m"),
    ("type が transfer", "1,2026-01-01,transfer,food,1.00,m"),
    ("type が空", "1,2026-01-01,,food,1.00,m"),
    ("ID が abc", "abc,2026-01-01,expense,food,1.00,m"),
    ("ID が空", ",2026-01-01,expense,food,1.00,m"),
    ("ID が小数", "1.5,2026-01-01,expense,food,1.00,m"),
]


@case("A45", "異常な値の行（日付・金額・type・ID が仕様の形式外）を含むファイルは list が終了コード 1")
def a45(e):
    # spec: ファイル形式は id(正の整数),date(yyyy-MM-dd),type(income|expense),amount(小数2桁) と定義。形式不正は終了コード 1
    for label, line in CORRUPT_VALUES:
        write_raw(e, "#next_id=3\n" + line + "\n")
        try:
            e.fail(1, "list")
        except CaseFail as ex:
            raise CaseFail("[%s] %s" % (label, ex))


@case("A46", "読み取り権限の無いデータファイル（chmod 000）は list / summary / add が終了コード 1")
def a46(e):
    # spec: 終了コード 1 =「読み書きできない」
    if hasattr(os, "geteuid") and os.geteuid() == 0:
        return "skipped: root can read any file"
    write_ledger(e, 2, [(1, "2026-01-01", "expense", "a", 100, "")])
    before = read_bytes(e.data)
    os.chmod(e.data, 0)
    try:
        e.fail(1, "list")
        e.fail(1, "summary", "--month", "2026-01")
        e.fail(1, "add", "2026-01-01", "expense", "a", "1")
    finally:
        os.chmod(e.data, 0o600)
    eq(read_bytes(e.data), before, "file content untouched")


@case("A47", "引数エラーはデータファイル・保存先の状態より優先（破損ファイル・存在しないディレクトリ・ディレクトリ指定でも終了コード 2）")
def a47(e):
    # spec: 引数の検証をデータファイルの読み込みより先に行う（引数が不正なら、データファイルの状態に関係なく終了コード 2）
    write_raw(e, "garbage\n")
    before = read_bytes(e.data)
    missing = os.path.join(e.dir, "no-such-dir", "x.csv")
    for target in (True, missing, e.dir):
        e.fail(2, "add", "2026-02-30", "expense", "a", "1", data=target)
        e.fail(2, "add", "2026-01-01", "expense", "a", "0", data=target)
        e.fail(2, "add", "2026-01-01", "expense", "a", data=target)
        e.fail(2, "delete", "abc", data=target)
        e.fail(2, "delete", "0", data=target)
        e.fail(2, "list", "--month", "2026-13", data=target)
        e.fail(2, "summary", data=target)
        e.fail(2, "summary", "--month", "2026-1", data=target)
        e.fail(2, "frobnicate", data=target)
    eq(read_bytes(e.data), before, "data file must be untouched")
    if os.path.exists(os.path.dirname(missing)):
        raise CaseFail("parent directory must not be created")


@case("A48", "--data が存在しないディレクトリ配下: list/summary は空扱い、add は終了コード 1 でディレクトリを作らない。日本語・空白のパスは可")
def a48(e):
    # spec: ファイルが存在しない場合はデータなし。保存時に親ディレクトリは作らない（存在しなければ終了コード 1）
    missing = os.path.join(e.dir, "no-such", "deep", "x.csv")
    eq(lines_of(e.ok("list", data=missing)), [HEADER], "list on missing dir")
    eq(lines_of(e.ok("summary", "--month", "2026-01", data=missing))[1:4],
       ["income\t0.00", "expense\t0.00", "balance\t0.00"], "summary on missing dir")
    e.fail(1, "add", "2026-01-01", "expense", "a", "1", data=missing)
    if os.path.exists(os.path.join(e.dir, "no-such")):
        raise CaseFail("add must not create parent directories")
    nested = os.path.join(e.dir, "ディレクトリ 空白", "子")
    os.makedirs(nested)
    path = os.path.join(nested, "家計 簿.csv")
    eq(e.ok("add", "2026-01-01", "expense", "食費", "1", "メモ", data=path), "1\n")
    eq(lines_of(e.ok("list", data=path)),
       [HEADER] + tsv(("1", "2026-01-01", "expense", "食費", "1.00", "メモ")))


@case("A49", "category / type / memo の検証境界: CR・LF 含みや type の前後空白は終了コード 2、空白だけの category は有効")
def a49(e):
    # spec: category は 1 文字以上でタブ・CR・LF を含まない（空白可）、type は小文字の income/expense のみ、memo はタブ・CR・LF 不可
    e.fail(2, "add", "2026-01-01", "expense", "a\rb", "1")
    e.fail(2, "add", "2026-01-01", "expense", "a\nb", "1")
    e.fail(2, "add", "2026-01-01", "expense", "a\r\nb", "1")
    e.fail(2, "add", "2026-01-01", "expense", "a", "1", "m\r")
    e.fail(2, "add", "2026-01-01", "expense", "a", "1", "\n")
    for t in (" income", "income ", "income\n", "ｉｎｃｏｍｅ", "Expense", "expenses"):
        e.fail(2, "add", "2026-01-01", t, "a", "1")
    eq(lines_of(e.ok("list")), [HEADER], "nothing stored")
    eq(e.ok("add", "2026-01-01", "expense", " ", "1", "\u00a0"), "1\n", "single-space category")
    eq(e.ok("add", "2026-01-01", "income", "\u3000", "2"), "2\n", "ideographic-space category")
    eq(lines_of(e.ok("list")),
       [HEADER] + tsv(("1", "2026-01-01", "expense", " ", "1.00", "\u00a0"),
                      ("2", "2026-01-01", "income", "\u3000", "2.00", "")))


# ---------------------------------------------------------------- 実行

def main():
    bin_path = sys.argv[1] if len(sys.argv) > 1 else ""
    results = []
    bin_problem = None
    if not bin_path:
        bin_problem = "usage: run_acceptance.py <path/to/bin/ledger>"
    elif not os.path.isfile(bin_path):
        bin_problem = "bin not found: %s" % bin_path
    elif not os.access(bin_path, os.X_OK):
        bin_problem = "bin is not executable: %s" % bin_path
    else:
        bin_path = os.path.abspath(bin_path)

    for case_id, name, func in CASES:
        if bin_problem:
            results.append({"id": case_id, "name": name, "pass": False, "detail": bin_problem})
            continue
        env = Env(bin_path)
        try:
            note = func(env)  # 文字列を返したケースは pass 時の detail（性能メモ）として載せる
            results.append({"id": case_id, "name": name, "pass": True,
                            "detail": note if isinstance(note, str) else ""})
        except CaseFail as ex:
            results.append({"id": case_id, "name": name, "pass": False, "detail": str(ex)[:600]})
        except Exception as ex:  # テスト側の想定外例外も JSON に載せる
            results.append({"id": case_id, "name": name, "pass": False,
                            "detail": "harness error: %r" % (ex,)})
        finally:
            env.close()
    sys.stdout.write(json.dumps(results, ensure_ascii=False))
    sys.stdout.write("\n")


if __name__ == "__main__":
    try:
        main()
    except Exception as ex:  # 何があっても JSON を出し、終了コードは 0
        sys.stdout.write(json.dumps([{"id": "A00", "name": "harness", "pass": False,
                                      "detail": "fatal: %r" % (ex,)}], ensure_ascii=False) + "\n")
    sys.exit(0)
