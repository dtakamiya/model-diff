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
import tempfile

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
        try:
            p = subprocess.run(cmd, capture_output=True, timeout=TIMEOUT_SEC,
                               stdin=subprocess.DEVNULL, env=env)
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
            func(env)
            results.append({"id": case_id, "name": name, "pass": True, "detail": ""})
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
