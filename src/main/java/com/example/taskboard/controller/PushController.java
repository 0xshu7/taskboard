package com.example.taskboard.controller;

// ===== インポート =====
// 以下は Spring の部品。
// PostMapping    … そのURLへ送信されたときに動くメソッドの印
// RequestBody    … 送られてきた JSON を Java の入れ物に変換して受け取る
// RestController … HTMLではなく、そのまま値を返すクラスです、という印
//
// 以下はこのアプリの部品。
// PushSubscriptionForm    … 画面から届いた届け先を受け取る入れ物
// NotificationService     … 通知を送る処理
// PushSubscriptionService … 届け先の出し入れ

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskboard.dto.PushSubscriptionForm;
import com.example.taskboard.service.NotificationService;
import com.example.taskboard.service.PushSubscriptionService;

/**
 * 通知の届け先を受け付ける。
 *
 * 他のコントローラと違い、HTML を返さない。
 * ブラウザの JavaScript が裏で呼ぶだけで、画面は切り替わらない。
 * そのため @Controller ではなく @RestController を使う。
 *
 * 扱うURLは3つ。
 *   POST /push/subscribe   … 通知を許可したとき。届け先を保存する
 *   POST /push/unsubscribe … 通知を切ったとき。届け先を消す
 *   POST /push/status      … この端末が登録済みかを返す
 *   POST /push/test        … 届くかどうかをその場で確かめる
 *
 * どれも fetch で呼ばれるので、CSRF の合言葉をヘッダーに載せる必要がある。
 * それは push.js が受け持っている。
 *
 * 中身の判断はすべて service 側にある。
 * 以前はここが repository を直接呼び、@Transactional も持っていたが、
 * 「controller は service だけを呼ぶ」という形に合わせて PushSubscriptionService へ移した。
 * 受け取って渡すだけなので、このクラスには分岐が1つも残っていない。
 */

// ===== アノテーション =====
// @RestController … 返した値がそのまま応答になる。
//                    @Controller だと "ok" をテンプレート名として探しにいってしまう。

@RestController
public class PushController {

	// ===== フィールド =====

	/** 届け先の出し入れ */
	private final PushSubscriptionService pushSubscriptionService;

	/** 通知を送る処理。テスト送信で使う */
	private final NotificationService notificationService;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public PushController(PushSubscriptionService pushSubscriptionService,
			NotificationService notificationService) {

		this.pushSubscriptionService = pushSubscriptionService;
		this.notificationService = notificationService;
	}

	// ===== メソッド =====

	/**
	 * 通知の届け先を登録する。
	 *
	 * @param form ブラウザから届いた届け先
	 * @return 結果を示す文字列（ok / already / invalid）
	 */
	@PostMapping("/push/subscribe")
	public String subscribe(@RequestBody PushSubscriptionForm form) {
		return pushSubscriptionService.register(
				form.getEndpoint(), form.getP256dh(), form.getAuth());
	}

	/**
	 * 通知の届け先を削除する。
	 *
	 * 消さずに残すと、切ったつもりでも届き続ける。
	 *
	 * @param form ブラウザから届いた届け先
	 * @return 結果を示す文字列（ok / invalid）
	 */
	@PostMapping("/push/unsubscribe")
	public String unsubscribe(@RequestBody PushSubscriptionForm form) {
		return pushSubscriptionService.unsubscribe(form.getEndpoint());
	}

	/**
	 * この端末が登録済みかを返す。設定画面を開いたときに呼ばれる。
	 *
	 * 届け先は端末ごとに違うので、ブラウザが持っているものを送ってもらう。
	 * サーバーからは、どの端末からの要求かを見分けられないため。
	 *
	 * GET ではなく POST にしているのは、届け先を URL に載せないため。
	 * URL は履歴やログに残る。
	 *
	 * @param form ブラウザが持っている届け先
	 * @return 登録されていれば yes、そうでなければ no
	 */
	@PostMapping("/push/status")
	public String status(@RequestBody PushSubscriptionForm form) {
		return pushSubscriptionService.isRegistered(form.getEndpoint()) ? "yes" : "no";
	}

	/**
	 * いますぐ通知を送る。設定の ON/OFF も時刻も見ない。
	 *
	 * 「許可したのに本当に届くのか」を、待たずに確かめるためのもの。
	 * 締切が今日か明日のタスクが1件も無いときは、その旨の本文が届く。
	 *
	 * @return 成功したことを示す文字列
	 */
	@PostMapping("/push/test")
	public String test() {
		notificationService.sendNow();
		return "ok";
	}
}