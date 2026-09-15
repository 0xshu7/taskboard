/**
 * アプリ全体の設定を置く場所。
 *
 * 画面や処理の本体ではなく、「起動時にどう組み立てるか」を決めるところ。
 *
 * ここに置くもの：
 * ・どのURLに認証が要るか（SecurityConfig）
 * ・非同期や定時実行の仕組みを有効にする（AsyncConfig / SchedulingConfig）
 * ・起動時に一度だけ動かす処理（DevDataLoader）
 *
 * @Configuration が付いたクラスは、起動時に1回だけ読まれる。
 * その中の @Bean が付いたメソッドの戻り値は、Spring が保管して
 * 必要なところへ渡す。自分で new しないのはそのため。
 *
 * 開発用の設定には @Profile("dev") を付ける。
 * 発表用や公開時に、意図しないものが動かないようにするため。
 */
package com.example.taskboard.config;