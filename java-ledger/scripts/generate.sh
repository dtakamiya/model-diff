#!/usr/bin/env bash
# 6構成の Claude に spec.md を実装させ、成果物を java-ledger/<id>/ に収集する。
# 使い方: generate.sh [id ...]   (省略時は6構成を順に)
# 環境変数: WORK_ROOT (既定 ${TMPDIR:-/tmp}/ledger-gen)
# 注意: claude -p を実行するため高コスト。Bash 3.2 互換。
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
LEDGER_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
SPEC="$LEDGER_DIR/spec.md"
WORK_ROOT="${WORK_ROOT:-${TMPDIR:-/tmp}/ledger-gen}"

ALL_IDS="solo-haiku solo-sonnet solo-opus conductor-sonnet-haiku conductor-opus-sonnet conductor-opus-haiku"

BASE_TOOLS="Read,Write,Edit,Glob,Grep,Bash(gradle:*),Bash(java:*),Bash(ls:*),Bash(cat:*),Bash(mkdir:*)"

PROMPT="spec.md を読み、その仕様どおりにカレントディレクトリへ実装してください。完了まで自走し、gradle test installDist が通ることを確認してください。"

# 指揮者(作業者)側へ付与する system prompt。__WORKER__ を作業者モデル名に置換して使う。
CONDUCTOR_SYSTEM_TEMPLATE="あなたは指揮者です。計画・指示書の作成・成果物の検証のみを行い、コードの作成・編集は必ず Agent ツールでサブエージェント（model パラメータに __WORKER__ を明示）へ委譲してください。自分で Write / Edit によりソースやビルドファイルを書いてはいけません。サブエージェントへの指示書には目的・対象ファイル・完了条件を含めてください。サブエージェントの報告は鵜呑みにせず、自分で gradle test installDist を実行して検証し、失敗していれば原因を具体化して再委譲してください。"

CONDUCTOR=""
WORKER=""

# id から構成を引く（連想配列は使わない）。CONDUCTOR / WORKER を設定。未知の id は 1 を返す。
lookup_config() {
  case "$1" in
    solo-haiku)             CONDUCTOR=haiku;  WORKER="" ;;
    solo-sonnet)            CONDUCTOR=sonnet; WORKER="" ;;
    solo-opus)              CONDUCTOR=opus;   WORKER="" ;;
    conductor-sonnet-haiku) CONDUCTOR=sonnet; WORKER=haiku ;;
    conductor-opus-sonnet)  CONDUCTOR=opus;   WORKER=sonnet ;;
    conductor-opus-haiku)   CONDUCTOR=opus;   WORKER=haiku ;;
    *) return 1 ;;
  esac
}

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

now_iso() { date -u +%Y-%m-%dT%H:%M:%SZ; }

# 1 構成を実行する。失敗は return 1 で通知（呼び出し側が継続を担保）。
run_one() {
  local id="$1"
  local work="$WORK_ROOT/$id"
  local log="$WORK_ROOT/$id.generate.log"
  local dest="$LEDGER_DIR/$id"
  local rc=0 started ended started_epoch ended_epoch

  lookup_config "$id" || { echo "unknown id: $id" >&2; return 1; }

  mkdir -p "$WORK_ROOT"
  : > "$log"
  say() { echo "[$(now_iso)] $*" | tee -a "$log"; }

  say "== $id (conductor=$CONDUCTOR worker=${WORKER:--}) =="

  if [ ! -f "$SPEC" ]; then
    say "ERROR: spec.md が見つかりません: $SPEC"
    finalize_log "$id" "$log" "$dest"
    return 1
  fi

  # 1. 隔離された空の作業ディレクトリ
  rm -rf "$work"
  mkdir -p "$work"
  cp "$SPEC" "$work/spec.md"

  # 2. claude -p 実行
  local tools="$BASE_TOOLS"
  local -a extra=()
  if [ -z "$WORKER" ]; then
    extra=(--disallowedTools "Agent")
  else
    tools="$BASE_TOOLS,Agent"
    extra=(--append-system-prompt "${CONDUCTOR_SYSTEM_TEMPLATE//__WORKER__/$WORKER}")
  fi

  started=$(now_iso); started_epoch=$(date +%s)
  say "claude 開始 (cwd=$work)"
  (
    cd "$work"
    claude -p "$PROMPT" \
      --model "$CONDUCTOR" \
      --output-format json \
      --setting-sources project \
      --permission-mode acceptEdits \
      --allowedTools "$tools" \
      "${extra[@]}" \
      < /dev/null > run.json 2>> "$log"
  ) || rc=$?
  ended=$(now_iso); ended_epoch=$(date +%s)
  say "claude 終了 exit=$rc elapsed=$((ended_epoch - started_epoch))s"

  # 3. 開始・終了時刻の記録
  printf '{"id":"%s","startedAt":"%s","endedAt":"%s","wallSeconds":%s,"exitCode":%s}\n' \
    "$id" "$started" "$ended" "$((ended_epoch - started_epoch))" "$rc" > "$WORK_ROOT/$id.meta.json"

  # 4. 成果物を java-ledger/<id>/ へ（既存は削除。失敗していても部分成果物は回収する）
  case "$dest" in
    "$LEDGER_DIR"/?*) rm -rf "$dest" ;;
    *) say "ERROR: 不正な dest: $dest"; return 1 ;;
  esac
  mkdir -p "$dest"
  rsync -a \
    --exclude 'build/' --exclude '.gradle/' --exclude '.claude/' \
    --exclude '/spec.md' --exclude '/run.json' \
    "$work/" "$dest/" 2>> "$log" || { say "WARN: rsync 失敗"; rc=1; }
  if [ -s "$work/run.json" ]; then
    cp "$work/run.json" "$dest/run.json"
  else
    say "WARN: run.json が空または無し"
    rc=1
  fi

  finalize_log "$id" "$log" "$dest"
  return "$rc"
}

# ログを java-ledger/<id>/generate.log へ置く。
finalize_log() {
  local id="$1" log="$2" dest="$3"
  mkdir -p "$dest"
  cp "$log" "$dest/generate.log"
}

main() {
  local -a ids=()
  if [ "$#" -eq 0 ]; then
    for id in $ALL_IDS; do ids+=("$id"); done
  else
    ids=("$@")
  fi

  # 先に id を検証（高コスト処理の途中で typo に気付かないため）
  local id
  for id in "${ids[@]}"; do
    lookup_config "$id" || { echo "unknown id: $id (valid: $ALL_IDS)" >&2; exit 2; }
  done

  local failed=""
  for id in "${ids[@]}"; do
    if run_one "$id"; then
      echo "OK: $id"
    else
      echo "FAILED: $id (ログ: $LEDGER_DIR/$id/generate.log)" >&2
      failed="$failed $id"
    fi
  done

  if [ -n "$failed" ]; then
    echo "失敗した構成:$failed" >&2
    exit 1
  fi
}

resolve_java_home
main "$@"
