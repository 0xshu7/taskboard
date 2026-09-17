package com.example.taskboard.config;

// ===== インポート =====
// java.security.Security … 暗号の部品を登録する窓口
//
// 以下は Spring の部品。
// Value         … application.yml の値を受け取る
// Bean          … このメソッドの戻り値を Spring に管理させる印
// Configuration … 設定を書くクラスです、という印
//
// 以下は外部のライブラリ。
// BouncyCastleProvider … 暗号の計算をする部品。Web Push の暗号化に要る
// PushService          … 通知を実際に送る係。web-push ライブラリのクラス

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import nl.martijndwars.webpush.PushService;

/**
 * Web Push の送信係を1つ用意する。
 *
 * PushService は web-push ライブラリのクラスで、
 * 「VAPIDの鍵で署名する」「本文を暗号化する」「窓口へ送る」をまとめてやってくれる。
 *
 * 作るのに鍵が要るので、起動時に1回だけ作って Spring に預けておく。
 * 使う側（NotificationService）は、できあがったものを受け取るだけでよい。
 *
 * 鍵は application.yml 経由で環境変数から読む。
 * コードにも yml にも値は書かない。
 * 秘密鍵が漏れると、他人がこのアプリを名乗って通知を送れる。
 */

// ===== アノテーション =====
// @Configuration … 設定を書くクラスです、という印。起動時に1回だけ読まれる。

@Configuration
public class PushConfig {

	// ===== フィールド =====

	/** VAPIDの公開鍵。ブラウザにも同じものを渡す */
	@Value("${push.public-key}")
	private String publicKey;

	/** VAPIDの秘密鍵。サーバーだけが持つ */
	@Value("${push.private-key}")
	private String privateKey;

	/** 送り主を示す連絡先。mailto: か https: で書く決まり */
	@Value("${push.subject}")
	private String subject;

	// ===== メソッド =====

	/**
	 * 通知を送る係を作る。
	 *
	 * Security.addProvider は、暗号の計算をする部品を Java に登録する処理。
	 * Web Push が使う方式は Java に最初から入っていないので、これが要る。
	 *
	 * 2回登録しても害はないが、起動時に1回だけ動くこのメソッドに置いておけば、
	 * 「いつ登録されるのか」を追いやすい。
	 *
	 * @return 通知を送る係
	 * @throws Exception 鍵の形が正しくないとき
	 */
	@Bean
	public PushService pushService() throws Exception {

		Security.addProvider(new BouncyCastleProvider());

		return new PushService(publicKey, privateKey, subject);
	}
}