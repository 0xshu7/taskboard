package com.example.taskboard.config;

// ===== インポート =====
// 以下は Spring の部品。
// Bean          … このメソッドの戻り値を Spring に管理させる印
// Configuration … 設定を書くクラスです、という印
//
// 以下は Spring Security の部品。
// HttpSecurity        … どのURLに何を要求するかを組み立てる道具
// SecurityFilterChain … 組み立てた結果。すべての要求がここを通る

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 認証と保護の設定。
 *
 * Googleアカウントでログインした人だけがアプリを使える。
 * 誰が使っているかが決まらないと、タスクを誰のものとして保存するかも決まらない。
 *
 * Googleに要求しているのは openid だけなので、受け取るのは識別子（sub）1つ。
 * メールアドレスも名前も受け取らない。
 *
 * ログイン画面は自前で用意している（templates/login.html）。
 * 認証先が Google 1つだけの場合、Spring Security は
 * 「選ばせる画面に意味がない」と判断して画面を作らず、
 * いきなり Google へ送る。そのため /login という画面が存在しない。
 * ログアウトしたあとの行き先が無くなるので、自分で用意している。
 *
 * ログインなしで通すのは3種類だけ。
 *   静的ファイル … 画面を描くのに必要。ログイン画面自身もこれを読む
 *   /login       … ログイン画面そのもの。ここを閉じるとログインできない
 *   /error       … 失敗したときの表示。閉じると原因が見えなくなる
 *
 * 残っている課題が1つある。
 *
 * H2コンソール（/h2）を、ログインなしで開けるようにしている。
 * 開発中に自分でデータベースを見るための入口で、
 * application-dev.yml でしか有効にしていないため、
 * 公開するときは入口ごと存在しなくなる。
 * ただし開発中は、同じ端末を使える人なら誰でも開ける状態になっている。
 */

// ===== アノテーション =====
// @Configuration … 設定を書くクラスです、という印。起動時に1回だけ読まれる。

@Configuration
public class SecurityConfig {

	// ===== メソッド =====

	/**
	 * 保護の設定を組み立てて返す。
	 *
	 * @Bean が付いているので、起動時に1回だけ呼ばれ、
	 * 戻り値を Spring Security が受け取る。
	 * ブラウザからの要求は、すべてここで組み立てたものを通ってから
	 * コントローラへ届く。
	 *
	 * auth -> auth... の書き方はラムダ式。
	 * 「渡されたものに対して、こう設定する」を並べていく形になっている。
	 *
	 * 並べる順番には意味がある。
	 * 先に書いた条件から順に当てはめ、最初に合ったところで決まる。
	 * anyRequest() を先頭に書くと、あとの行が一切効かなくなる。
	 *
	 * oauth2Login の defaultSuccessUrl に true を渡しているのは、
	 * 「どこから来たかに関わらず、ログイン後は必ずここへ」という指定。
	 * false にすると、ログイン前に開こうとした画面へ戻る。
	 * 途中の画面から始まると迷うので、必ずスタート画面から始める。
	 *
	 * logout はブラウザから /logout に POST すると動く。
	 * GET で動かないのは、リンクを踏ませるだけで勝手にログアウトさせられないため。
	 * 画面側はボタン（フォーム）で置く必要がある。
	 *
	 * ログアウトしても消えるのはこのアプリのログイン状態だけで、
	 * Google 側のログインは残る。行き先を / にすると、
	 * ログインが要る画面なので Google へ送られ、何も聞かれずに戻ってきてしまう。
	 * そのため、ログインの要らない /login へ戻している。
	 *
	 * CSRF は有効にしている。
	 * 別のサイトに仕込まれた仕掛けから、利用者のふりをして操作を送られる攻撃を防ぐ。
	 * 送信のたびに合言葉（トークン）が要るようになる。
	 * th:action を使ったフォームには Thymeleaf が自動で入れてくれるが、
	 * fetch で送っている削除だけは、自分でヘッダーに載せている（delete.js）。
	 * H2コンソールは Spring の作りではないので合言葉を付けられない。そこだけ除外する。
	 *
	 * throws Exception が付いているのは、組み立ての途中で
	 * 失敗する可能性があると宣言されているため。
	 * 起動時に1回動くだけなので、失敗すればアプリが起動しない。
	 *
	 * @param http 保護の設定を組み立てる道具
	 * @return 組み立てた設定
	 * @throws Exception 組み立てに失敗したとき
	 */
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

		http.authorizeHttpRequests(auth -> auth
				// 画面を描くのに必要なファイル。ログイン画面もこれを読む
				.requestMatchers("/css/**", "/js/**", "/sounds/**",
						"/favicon.ico", "/*.png", "/site.webmanifest").permitAll()
				// ログイン画面と、失敗したときの表示
				.requestMatchers("/login", "/error").permitAll()
				// 開発中だけ使う。H2コンソール
				.requestMatchers("/h2/**").permitAll()
				// 上のどれにも当てはまらないものは、ログインした人だけ
				.anyRequest().authenticated())

			.oauth2Login(login -> login
					.loginPage("/login")
					.defaultSuccessUrl("/", true))

			.logout(logout -> logout
					.logoutSuccessUrl("/login?logout"))

			// H2コンソールだけ CSRF の確認から外す。
			// Spring の作りではないので、合言葉を付ける手段がない
			.csrf(csrf -> csrf
					.ignoringRequestMatchers("/h2/**"))

			// H2コンソールは画面を枠の中に埋め込む作りなので、禁止したままだと表示できない。
			// disable() にすると、どのサイトからでもこのアプリを枠に埋め込めてしまう。
			// sameOrigin なら「同じアプリの中からだけ」許すので、H2は動き、外からは埋め込めない
			.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

		return http.build();
	}
}