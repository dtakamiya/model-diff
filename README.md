# model-diff

同じ仕様を異なるモデル／構成の Claude に実装させ、成果物を比較するリポジトリ。

公開ページ: https://dtakamiya.github.io/model-diff/

## 構成（6 パターン）
`solo-{haiku,sonnet,opus}`（単独）と `conductor-{opus-haiku,opus-sonnet,sonnet-haiku}`（指揮役＋実装役）。

## 比較 1: Three.js パーティクルモーフ
- 仕様: [`spec.md`](spec.md)
- 成果物: 各構成ディレクトリの `particle-morph.html`
- 比較ページ: [`compare.html`](https://dtakamiya.github.io/model-diff/compare.html)

## 比較 2: Java CLI 家計簿 `ledger`
- 仕様: [`java-ledger/spec.md`](java-ledger/spec.md)
- 成果物: `java-ledger/<構成>/`（Gradle プロジェクト）
- 比較ページ: [`java-ledger/compare.html`](https://dtakamiya.github.io/model-diff/java-ledger/compare.html)（ビルド・受け入れテスト結果と、ブラインドのコードレビュー評価）
- 受け入れテスト: [`java-ledger/acceptance/`](java-ledger/acceptance/README.md)（Python 3 標準ライブラリのみ、49 ケース）
- スクリプト（Bash 3.2 互換）:
  - `java-ledger/scripts/generate.sh [id ...]` — 6 構成に実装させる（`claude -p` を使うため高コスト）
  - `java-ledger/scripts/collect.sh` — ビルド・テスト・受け入れ検査を行い `results.json` を出力（gradle / java / python3 が必要）
- データ: `results.json`（計測結果）、`reviews.json`（レビュー評価）

## 公開
GitHub Pages を `main` ブランチ直下から配信。ルートの [`index.html`](index.html) が各比較ページへのリンク。
