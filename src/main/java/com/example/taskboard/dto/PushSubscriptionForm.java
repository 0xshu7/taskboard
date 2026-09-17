package com.example.taskboard.dto;

/**
 * ブラウザから届いた「通知の届け先」を受け取る入れ物。
 *
 * 画面の JavaScript が、この3つを JSON で送ってくる。
 * Spring が JSON の項目名とフィールド名を突き合わせて、自動で入れてくれる。
 *
 * 名前が一致していないと、黙って null のままになる。
 * エラーは出ないので、保存したのに空、という形で気づくことになる。
 *
 * domain ではなく dto に置いている。
 * 画面とやりとりするための形であって、データベースの形ではないため。
 * 変わる理由が違うものは、同じ場所に置かない。
 */
public class PushSubscriptionForm {

	// ===== フィールド =====

	/** 届け先のURL。ブラウザの提供元が用意する窓口 */
	private String endpoint;

	/** 本文を暗号化するための公開鍵 */
	private String p256dh;

	/** 同じく暗号化に使う値 */
	private String auth;

	// ===== メソッド =====
	// Spring が値を入れるために setter を、読み出すために getter を使う。
	// どちらか欠けると受け取れない

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getP256dh() {
		return p256dh;
	}

	public void setP256dh(String p256dh) {
		this.p256dh = p256dh;
	}

	public String getAuth() {
		return auth;
	}

	public void setAuth(String auth) {
		this.auth = auth;
	}
}
