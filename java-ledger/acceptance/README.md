# 受け入れテスト

`../spec.md` の仕様を検証する共通ブラックボックステスト（Python 3 標準ライブラリのみ、49 ケース）。

## 使い方
```
python3 run_acceptance.py <path/to/build/install/ledger/bin/ledger>
```
各ケースは一時ディレクトリのデータファイル（`--data`）で独立に実行する（1 回 20 秒でタイムアウト）。

## 出力形式
標準出力は JSON 配列のみ。終了コードは常に 0。
```
[{"id":"A01","name":"短い説明","pass":true,"detail":""}]
```
- `detail`：失敗時は期待値と実際値の要約、成功時は空文字
- bin が無い・実行できない場合は全ケース `pass:false` で出力する
