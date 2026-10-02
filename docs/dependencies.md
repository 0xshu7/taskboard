# 依存ライブラリ情報

実体は `pom.xml` です。この表はその一覧です。

| 分類 | artifactId | 版 | 用途 |
|---|---|---|---|
| 親 | `spring-boot-starter-parent` | 4.0.8 | 版の管理。下のスターターは版を書かない |
| Web | `spring-boot-starter-webmvc` | 親に従う | 画面とURL。`RestClient` もここに入っている |
| 画面 | `spring-boot-starter-thymeleaf` | 親に従う | HTMLテンプレート |
| 画面 | `thymeleaf-extras-springsecurity6` | 親に従う | 画面でのログイン状態の表示 |
| 入力 | `spring-boot-starter-validation` | 親に従う | 入力値の検証 |
| DB | `spring-boot-starter-data-jpa` | 親に従う | データベースの出し入れ（Hibernate） |
| DB | `h2` | 親に従う（2.4.240） | 開発用のデータベース |
| DB | `spring-boot-h2console` | 親に従う | H2 のコンソール画面 |
| DB | `mysql-connector-j` | 親に従う | MySQL のドライバ |
| 認証 | `spring-boot-starter-security` | 親に従う | 認証と認可 |
| 認証 | `spring-boot-starter-security-oauth2-client` | 親に従う | Googleログイン |
| 通知 | `web-push` | 5.1.2 | Web Push の送信 |
| 通知 | `bcprov-jdk18on` | 1.78.1 | 暗号。web-push が使う |
| 通知 | `bcpkix-jdk18on` | 1.78.1 | 鍵の扱い。web-push が使う |
| 通知 | `jose4j` | 0.9.6 | VAPID の署名 |
| 通知 | `httpcore` | 4.4.16 | web-push が使う HTTP の部品 |
| 開発 | `spring-boot-devtools` | 親に従う | 保存したら再起動（開発時のみ） |
| テスト | `spring-boot-starter-webmvc-test` ほか5つ | 親に従う | テスト用 |

JSON の読み書きに使う Jackson 3（`tools.jackson.*`）は、Spring Boot に最初から入っているため `pom.xml` には書いていません。

生成AIの呼び出しに SDK は使っていません。Spring Web に入っている `RestClient` で HTTP を送っています。

`web-push` が持ち込む `slf4j-reload4j` は `<exclusions>` で外しています。Spring Boot のログの仕組みと二重になるためです。

## 必要なもの

| 項目 | 版 |
|---|---|
| Java | 21 |
| Maven | 3.9 以降（Eclipse 同梱のものでよい） |
| MySQL | 8 以降（確認は 9.7.2） |
