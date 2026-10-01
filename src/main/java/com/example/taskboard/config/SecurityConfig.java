package com.example.taskboard.config;

// ===== インポート =====
// 以下は Spring の部品。
// Value         … application.yml に書いた値を受け取る
// Bean          … このメソッドの戻り値を Spring に管理させる印
// Configuration … 設定を書くクラスです、という印
//
// 以下は Spring Security の部品。
// HttpSecurity        … どのURLに何を要求するかを組み立てる道具
// SecurityFilterChain … 組み立てた結果。すべての要求がここを通る

import org.springframework.beans.factory.annotation.Value;
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
 * 【H2コンソールに関わる設定は、1つの値から決める】
 *
 * コンソールのために緩めている設定が3つある。
 *
 * 　1. /h2/** をログイン無しで通す
 * 　2. /h2/** を CSRF の確認から外す
 * 　3. 画面を枠の中に埋め込むことを許す（frameOptions）
 *
 * どれも「コンソールを使うため」だけの緩和なので、
 * コンソールが無効なら1つも要らない。
 *
 * 以前は 1 と 2 だけ条件を付けて、3 は無条件に緩めていた。
 * コンソールを使わない本番でも、画面を枠に埋め込める状態が残っていた。
 *
 * 「コンソールを出すかどうか」と「緩めるかどうか」が別々の場所にあると、
 * 片方だけ変えたときに、ちぐはぐな組み合わせが作れてしまう。
 * 同じ値から3つとも決めることで、その組み合わせが起きないようにしている。
 */

// ===== アノテーション =====
// @Configuration … 設定を書くクラスです、という印。起動時に1回だけ読まれる。

@Configuration
public class SecurityConfig {

	// ===== フィールド =====

	/**
	 * H2コンソールが有効かどうか。
	 *
	 * application-dev.yml では true、application.yml（prod）では false。
	 * 末尾の :false は「設定が無ければ false」という既定値で、
	 * 書き忘れたときに開放される側へ倒れないようにしてある。
	 */
	@Value("${spring.h2.console.enabled:false}")
	private boolean h2ConsoleEnabled;

	// ===== メソッド =====

	/**
	 * 保護の設定を組み立てて返す。
	 *
	 * @Bean が付いているので、起動時に1回だけ呼ばれ、
	 * 戻り値を Spring Security が受け取る。
	 * ブラウザからの要求は、すべてここで組み立てたものを通ってから
	 * コントローラへ届く。
	 *
	 * auth -> { ... } の書き方はラムダ式。
	 * 条件によって行を足したり足さなかったりするので、
	 * 1つずつ書く形（auth.requestMatchers(...)）にしてある。
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

		http.authorizeHttpRequests(auth -> {

				// 画面を描くのに必要なファイル。ログイン画面もこれを読む
				auth.requestMatchers("/css/**", "/js/**", "/sounds/**", "/sw.js",
						"/favicon.ico", "/*.png", "/site.webmanifest").permitAll();

				// ログイン画面と、失敗したときの表示
				auth.requestMatchers("/login", "/error").permitAll();

				// H2コンソール。コンソール自体が有効なときだけ開放する
				if (h2ConsoleEnabled) {
					auth.requestMatchers("/h2/**").permitAll();
				}

				// 上のどれにも当てはまらないものは、ログインした人だけ
				auth.anyRequest().authenticated();
			})

			.oauth2Login(login -> login
					.loginPage("/login")
					.defaultSuccessUrl("/", true))

			.logout(logout -> logout
					.logoutSuccessUrl("/login?logout"))

			// H2コンソールだけ CSRF の確認から外す。
			// Spring の作りではないので、合言葉を付ける手段がない。
			// コンソールが無効なら、除外する対象も無い
			.csrf(csrf -> {
					if (h2ConsoleEnabled) {
						csrf.ignoringRequestMatchers("/h2/**");
					}
				})

			// 画面を枠（iframe）の中に埋め込めるかどうか。
			//
			// H2コンソールは枠を使う作りなので、禁止したままだと表示できない。
			// sameOrigin にすると「同じアプリの中からだけ」許す形になり、
			// コンソールは動き、外のサイトからは埋め込めない。
			//
			// 既定は「どこにも埋め込ませない」。
			// コンソールが無効なら緩める理由が無いので、既定のままにしておく。
			// 枠に埋め込めると、見えない形で重ねたボタンを押させる手口が成り立つ
			.headers(headers -> {
					if (h2ConsoleEnabled) {
						headers.frameOptions(frame -> frame.sameOrigin());
					}
				});

		return http.build();
	}
}