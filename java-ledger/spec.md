# 依頼：Java 製 CLI 家計簿 `ledger`（Gradle プロジェクト）

## ゴール
収入・支出を記録して一覧・集計できるコマンドラインの家計簿を、Java 17 + Gradle のプロジェクトとして作ってください。
データは CSV ファイルに保存し、コマンドを実行するたびに読み書きします（常駐しない）。
出力書式・終了コードは下記で固定されており、外部の受け入れテストが標準出力・終了コードを厳密に比較します。
仕様の例と一字一句ずれないように実装してください。

## 環境・ビルド
- Java 17、Gradle 9.6.1（ローカルの `gradle` コマンドを使う。Gradle wrapper は不要）
- 外部ライブラリは JUnit 5（テスト用）のみ。実行時の依存ライブラリは使わない
- プロジェクトはカレントディレクトリ直下に作る（サブディレクトリを掘らない）：
  - `settings.gradle`：`rootProject.name = 'ledger'`
  - `build.gradle`：`application` プラグインを使う
- `gradle test installDist` が成功し、`build/install/ledger/bin/ledger` が生成されること
- Gradle 9 の注意：`useJUnitPlatform()` に加えて、`testRuntimeOnly 'org.junit.platform:junit-platform-launcher'` が必要
  （無いと `Failed to load JUnit Platform` で test タスクが失敗する）

## データ保存
- 保存先はグローバルオプション `--data <path>` で指定する。無指定時は `~/.ledger.csv`
  （Java の `System.getProperty("user.home")` 直下の `.ledger.csv`）
- ファイルが存在しない場合、および 0 バイトのファイルは「データなし（次の ID は 1）」として扱う。読み取りだけのコマンド（`list` / `summary`）はファイルを作らなくてよい
- 文字コードは UTF-8（標準出力・ファイルともに、実行環境の既定文字コードに依存せず UTF-8 を明示すること）
- ファイル形式（LF 改行）：
  - 1 行目：`#next_id=<次に採番する ID>`
  - 2 行目以降：1 件 1 行、`id,date,type,category,amount,memo` の 6 フィールド（ヘッダー行なし）
  - `amount` は常に小数 2 桁（例 `1200.50`）
  - `category` と `memo` は RFC 4180 風にクォートする：カンマまたはダブルクォートを含む場合は全体を `"` で囲み、中の `"` は `""` にする（それ以外はクォートしない）
  - 例：
    ```
    #next_id=4
    1,2026-01-10,expense,food,1200.50,"lunch, with ""Taro"""
    2,2026-01-05,income,salary,300000.00,
    3,2026-01-10,expense,transport,300.00,train
    ```
- ID は 1 始まりの連番で、削除しても再利用しない（末尾の ID を削除した後の `add` も、全件削除した後の `add` も、削除前の続きの番号になる）。そのため `#next_id` を保存・復元すること
- 読み込めない・壊れているファイル（形式不正、ディレクトリを指している等）は IO エラー（終了コード 1）

## コマンド
書式：`ledger [--data <path>] <command> [args...]`
`--data` は command の前にだけ置く（command の後ろに現れたら未知のオプションとして入力エラー）。

### `add <yyyy-MM-dd> <income|expense> <category> <amount> [memo]`
- 採番した ID を標準出力に 1 行で出力する（例 `1`）
- 引数は 4 個または 5 個ちょうど（`memo` は省略可で、省略時は空文字）。それ以外は入力エラー
- `memo` が空文字 `""` で渡された場合も有効（省略と同じ扱い）
- 検証ルール（違反はすべて終了コード 2、データは一切変更しない）：
  - 日付：`yyyy-MM-dd`（4 桁-2 桁-2 桁）かつ実在する暦日（グレゴリオ暦。`2026-02-30`・`2026-13-01`・`2026/01/05`・`2026-1-5` は不正、`2024-02-29` は有効）
  - type：小文字の `income` または `expense` のみ
  - category：1 文字以上。タブ・CR・LF を含まない（カンマ・ダブルクォート・空白・日本語は可。前後の空白もそのまま保持し trim しない。大文字小文字は区別する）
  - amount：正の数。整数、または小数点以下 1〜2 桁（`1200`、`1200.5`、`1200.50` は有効）。整数部は 1〜12 桁。正規表現で言うと `[0-9]{1,12}(\.[0-9]{1,2})?` に一致し、かつ値が 0 より大きい。
    次は不正：`0`、`0.00`、`-5`、`12.345`、`abc`、`1e3`、`1,200`、`1200.`、`.5`、`+5`
  - memo：タブ・CR・LF を含まない（カンマ・ダブルクォート・日本語は可）

### `list [--month yyyy-MM]`
- 登録済みの全件（`--month` 指定時はその月の分だけ）を、日付昇順・同日は ID 昇順で出力する
- 1 行目は常にヘッダー行。続けて 1 件 1 行。列区切りはタブ（`\t`）で、常に 6 列（memo が空でも行末のタブを省略しない）
- 列：`id` `date` `type` `category` `amount` `memo`。`amount` は常に小数 2 桁・3 桁区切りなし。`category` / `memo` は CSV のクォートを外した元の文字列をそのまま出す
- 該当 0 件のときもヘッダー行は出力する
- `--month` の値は `yyyy-MM`（月は 01〜12）。不正は終了コード 2。`--month` に値が無い、`--month` 以外の引数がある場合も終了コード 2

### `delete <id>`
- 成功時は標準出力に `deleted <id>` を出力する（例 `deleted 2`）
- `<id>` は先頭 0 なしの正の整数（正規表現 `[1-9][0-9]*`、かつ `9223372036854775807` 以下）。`abc`、`0`、`-1`、`01` などは入力エラー（終了コード 2）。引数は 1 個ちょうど
- 該当する ID が存在しない場合は終了コード 3（データは変更しない）

### `summary --month yyyy-MM`
- `--month` は必須（無い・不正は終了コード 2）
- 指定月のデータを集計して、次の形式で出力する（区切りはタブ。残高がマイナスなら `-500.00` のように先頭に `-` を付ける）：
  ```
  month	2026-01
  income	300000.00
  expense	1500.50
  balance	298499.50
  expense_by_category
  food	1200.50
  transport	300.00
  ```
  - `income`：その月の収入合計、`expense`：支出合計、`balance`：収入合計 − 支出合計
  - `expense_by_category` の行は常に出力し、続けてカテゴリ別の支出合計を 1 カテゴリ 1 行（`<category>\t<amount>`）で出す。並びは金額の降順、同額は category の文字列昇順（Java の `String.compareTo` と同じ UTF-16 コードユニット順）
  - 収入のカテゴリは `expense_by_category` に含めない。支出が無ければ `expense_by_category` の後ろは空
  - 該当データが無い月は `income` `expense` `balance` がすべて `0.00`

## 金額の扱い
- 浮動小数（`double` / `float`）は使わない。`BigDecimal` か、最小単位（銭）の整数で計算する
- 出力は常に小数 2 桁（`1200` → `1200.00`、`1200.5` → `1200.50`）、3 桁区切りなし
- 例：`0.10` を 3 回 `income` で登録した月の `summary` は `income\t0.30`

## 出力・エラー・終了コード
- 正常時の出力は標準出力だけ。エラーは標準エラー出力に **`error: ` で始まる 1 行**（本文は自由。日本語でも英語でもよい）を出す。エラー時は標準出力に何も出さない
- 終了コード：

  | 終了コード | 意味 |
  |---|---|
  | 0 | 正常 |
  | 1 | ファイル IO エラー（読み書きできない、データファイル破損、保存先ディレクトリが無い等） |
  | 2 | 入力・引数エラー（コマンド未指定、未知のコマンド、引数の過不足、各項目の検証違反、`--data` の値なし） |
  | 3 | `delete` で存在しない ID を指定した |

- 引数の検証をデータファイルの読み込みより先に行う。引数が不正なら、データファイルの状態に関係なく終了コード 2
- 保存時に親ディレクトリは作らない（存在しなければ終了コード 1）

## 使用例
```
$ ledger --data /tmp/l.csv add 2026-01-10 expense food 1200.5 "lunch, with \"Taro\""
1
$ ledger --data /tmp/l.csv add 2026-01-05 income salary 300000
2
$ ledger --data /tmp/l.csv add 2026-01-10 expense transport 300 train
3
$ ledger --data /tmp/l.csv list
id	date	type	category	amount	memo
2	2026-01-05	income	salary	300000.00	
1	2026-01-10	expense	food	1200.50	lunch, with "Taro"
3	2026-01-10	expense	transport	300.00	train
$ ledger --data /tmp/l.csv summary --month 2026-01
month	2026-01
income	300000.00
expense	1500.50
balance	298499.50
expense_by_category
food	1200.50
transport	300.00
$ ledger --data /tmp/l.csv delete 3
deleted 3
$ ledger --data /tmp/l.csv add 2026-02-01 expense food 0 ; echo $?
error: ...（標準エラー出力）
2
$ ledger --data /tmp/l.csv delete 99 ; echo $?
error: ...（標準エラー出力）
3
```
（上の `list` の 2 行目は memo が空なので行末にタブが 1 つある。表示上のタブは実際のタブ文字）

## 品質要件
- 自前の JUnit 5 テストを書くこと（テストの数・内容は自由）。`gradle test` が通ること
- 受け入れテストの通過だけでなく、設計（責務の分離）・エラー処理・可読性も評価対象
- コードは読みやすく整理し、主要部分に日本語コメントを付ける
- 頼まれていない機能（編集コマンド、エクスポート、色付き出力など）は足さない

## 完了条件
1. `gradle test installDist` が成功し、`build/install/ledger/bin/ledger` が生成される
2. 上記の使用例どおりに動く（出力書式・終了コードが一致する）
3. 自前の JUnit テストが存在し、通る

作業の最後に、ビルド手順と動作確認のコマンド例を簡潔に教えてください。
