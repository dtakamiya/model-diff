#!/usr/bin/env bash
# 各構成 java-ledger/<id>/ をビルド・テスト・受け入れ検査し、java-ledger/results.json を出力する。
# 使い方: collect.sh
# 環境変数: GRADLE_TIMEOUT 秒 (既定 600) / ACCEPT_TIMEOUT 秒 (既定 300)
# 依存: gradle, java, python3。Bash 3.2 互換。
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
LEDGER_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
export LEDGER_DIR
export GRADLE_TIMEOUT="${GRADLE_TIMEOUT:-600}"
export ACCEPT_TIMEOUT="${ACCEPT_TIMEOUT:-300}"

# Gradle が使う JVM と bin/ledger (PATH の java) がずれると UnsupportedClassVersionError になるため、
# JAVA_HOME 未設定なら `gradle --version` の Daemon JVM を JAVA_HOME として export する。
resolve_java_home() {
  if [ -n "${JAVA_HOME:-}" ]; then
    return 0
  fi
  local line
  line=$(gradle --version 2>/dev/null | sed -n 's/^Daemon JVM: *//p' | head -1) || true
  line=${line%% (*}
  if [ -n "$line" ] && [ -x "$line/bin/java" ]; then
    export JAVA_HOME="$line"
  fi
}

resolve_java_home

# JSON 組立・集計は python3（文字列エスケープを安全に行うため）。
python3 -I - <<'PY'
import datetime
import glob
import json
import os
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

LEDGER_DIR = os.environ["LEDGER_DIR"]
GRADLE_TIMEOUT = int(os.environ["GRADLE_TIMEOUT"])
ACCEPT_TIMEOUT = int(os.environ["ACCEPT_TIMEOUT"])
CMD_TIMEOUT = 30
LOG_TAIL_LINES = 80

TARGETS = [
    ("solo-haiku", "単発 Haiku"),
    ("solo-sonnet", "単発 Sonnet"),
    ("solo-opus", "単発 Opus"),
    ("conductor-sonnet-haiku", "指揮者 Sonnet＋Haiku"),
    ("conductor-opus-sonnet", "指揮者 Opus＋Sonnet"),
    ("conductor-opus-haiku", "指揮者 Opus＋Haiku"),
]

# 固定のサンプルコマンド列（--data は表示上省略）
SAMPLE_COMMANDS = [
    ["add", "2026-10-01", "expense", "food", "1200.5", "ランチ"],
    ["add", "2026-10-02", "income", "salary", "300000", "給料"],
    ["add", "2026-10-03", "expense", "food", "0.10", "駄菓子"],
    ["add", "2026-10-03", "expense", "food", "0.10", "駄菓子"],
    ["add", "2026-10-03", "expense", "food", "0.10", "駄菓子"],
    ["list"],
    ["summary", "--month", "2026-10"],
    ["delete", "99"],
    ["add", "2026-10-04", "expense", "food", "-5", "不正金額"],
]


def decode(data):
    return data.decode("utf-8", errors="replace") if data is not None else ""


def run(cmd, cwd, timeout):
    """(returncode, 出力) を返す。タイムアウト時は returncode=None。"""
    try:
        p = subprocess.run(
            cmd, cwd=cwd, stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout,
        )
        return p.returncode, decode(p.stdout)
    except subprocess.TimeoutExpired as e:
        return None, decode(e.stdout)
    except OSError as e:
        return -1, "実行失敗: %s\n" % e


def tail(text, n=LOG_TAIL_LINES):
    return "\n".join(text.splitlines()[-n:])


def run_build(d):
    rc, out = run(["gradle", "test", "installDist", "--console=plain"], d, GRADLE_TIMEOUT)
    if rc is None:
        return {"ok": False, "log": tail(out + "\n[TIMEOUT after %ds]" % GRADLE_TIMEOUT)}
    return {"ok": rc == 0, "log": tail(out)}


def own_tests(d):
    files = sorted(glob.glob(os.path.join(d, "build", "test-results", "test", "*.xml")))
    if not files:
        return None
    total = failed = skipped = 0
    for f in files:
        try:
            root = ET.parse(f).getroot()
        except (ET.ParseError, OSError):
            continue
        suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
        for s in suites:
            total += int(s.get("tests", 0))
            failed += int(s.get("failures", 0)) + int(s.get("errors", 0))
            skipped += int(s.get("skipped", 0))
    return {"total": total, "passed": total - failed - skipped, "failed": failed, "skipped": skipped}


def binary_path(d):
    return os.path.join(d, "build", "install", "ledger", "bin", "ledger")


def acceptance(d, tid):
    script = os.path.join(LEDGER_DIR, "acceptance", "run_acceptance.py")
    binary = binary_path(d)
    if not os.path.isfile(script) or not os.path.isfile(binary):
        return None
    rc, out = run(["python3", script, binary], LEDGER_DIR, ACCEPT_TIMEOUT)
    if rc is None:
        return None
    try:
        cases = json.loads(out)
    except ValueError:
        return None
    if not isinstance(cases, list):
        return None
    passed = sum(1 for c in cases if isinstance(c, dict) and c.get("pass"))
    return {"total": len(cases), "passed": passed, "cases": cases}


def count_lines(d, sub):
    n = 0
    for dirpath, _, names in os.walk(os.path.join(d, "src", sub)):
        for name in names:
            if name.endswith(".java"):
                try:
                    with open(os.path.join(dirpath, name), "rb") as fh:
                        n += len(fh.read().splitlines())
                except OSError:
                    pass
    return n


def list_files(d):
    out = []
    for dirpath, _, names in os.walk(os.path.join(d, "src")):
        for name in names:
            if name.endswith(".java"):
                out.append(os.path.relpath(os.path.join(dirpath, name), d))
    for name in ("build.gradle", "settings.gradle", "build.gradle.kts", "settings.gradle.kts"):
        if os.path.isfile(os.path.join(d, name)):
            out.append(name)
    return sorted(out)


def sample_transcript(d):
    binary = binary_path(d)
    if not os.path.isfile(binary):
        return ""
    parts = []
    with tempfile.TemporaryDirectory() as tmp:
        data = os.path.join(tmp, "ledger-data")
        for args in SAMPLE_COMMANDS:
            shown = "$ ledger " + " ".join(args)
            try:
                p = subprocess.run(
                    [binary, "--data", data] + args, stdin=subprocess.DEVNULL,
                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=CMD_TIMEOUT,
                )
                out, err, code = decode(p.stdout), decode(p.stderr), str(p.returncode)
            except subprocess.TimeoutExpired:
                out, err, code = "", "", "timeout"
            except OSError as e:
                out, err, code = "", "実行失敗: %s\n" % e, "error"
            body = out + err
            if body and not body.endswith("\n"):
                body += "\n"
            parts.append("%s\n%s[exit %s]\n" % (shown, body, code))
    return "\n".join(parts)


def family(model_id):
    m = model_id.lower()
    for key, name in (("haiku", "Haiku"), ("sonnet", "Sonnet"), ("opus", "Opus")):
        if key in m:
            return name
    return model_id


def cost(d):
    path = os.path.join(d, "run.json")
    try:
        with open(path, encoding="utf-8") as fh:
            run_json = json.load(fh)
    except (OSError, ValueError):
        return None
    if not isinstance(run_json, dict):
        return None
    total = run_json.get("total_cost_usd")
    dur = run_json.get("duration_ms")
    agg = {}
    for model_id, u in (run_json.get("modelUsage") or {}).items():
        a = agg.setdefault(family(model_id), {
            "model": family(model_id), "inputTokens": 0, "outputTokens": 0,
            "cacheReadInputTokens": 0, "cacheCreationInputTokens": 0, "usd": 0.0})
        a["inputTokens"] += u.get("inputTokens", 0)
        a["outputTokens"] += u.get("outputTokens", 0)
        a["cacheReadInputTokens"] += u.get("cacheReadInputTokens", 0)
        a["cacheCreationInputTokens"] += u.get("cacheCreationInputTokens", 0)
        a["usd"] += u.get("costUSD", 0.0)
    breakdown = []
    for a in agg.values():
        a["usd"] = round(a["usd"], 4)
        breakdown.append(a)
    return {
        "total": round(total, 4) if isinstance(total, (int, float)) else None,
        "minutes": round(dur / 60000, 1) if isinstance(dur, (int, float)) else None,
        "breakdown": breakdown,
    }


def collect(tid, label):
    d = os.path.join(LEDGER_DIR, tid)
    print("[collect] %s" % tid, file=sys.stderr, flush=True)
    if not os.path.isdir(d):
        return {
            "id": tid, "label": label,
            "build": {"ok": False, "log": "ディレクトリが存在しません: %s" % tid},
            "ownTests": None, "acceptance": None,
            "lines": {"main": 0, "test": 0}, "files": [], "sample": "", "cost": None,
        }
    build = run_build(d)
    if not build["ok"] and not os.path.isfile(binary_path(d)):
        # 自前テスト失敗で installDist まで到達しない場合も、受け入れ検査のためバイナリだけは用意する。
        run(["gradle", "installDist", "-x", "test", "--console=plain"], d, GRADLE_TIMEOUT)
    return {
        "id": tid, "label": label, "build": build,
        "ownTests": own_tests(d),
        "acceptance": acceptance(d, tid),
        "lines": {"main": count_lines(d, "main"), "test": count_lines(d, "test")},
        "files": list_files(d),
        "sample": sample_transcript(d),
        "cost": cost(d),
    }


targets = [collect(tid, label) for tid, label in TARGETS]
result = {
    "generatedAt": datetime.datetime.now().astimezone().isoformat(timespec="seconds"),
    "targets": targets,
}
out_path = os.path.join(LEDGER_DIR, "results.json")
with open(out_path, "w", encoding="utf-8") as fh:
    json.dump(result, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
print("wrote %s" % out_path, file=sys.stderr)
PY
