package com.example.taskboard.controller;

// ===== インポート =====
// 以下は Spring の部品。
// Transactional  … メソッドの途中で失敗したら、その中の保存をすべて取り消す
// PostMapping    … そのURLへ送信されたときに動くメソッドの印
// RequestBody    … 送られてきた JSON を Java の入れ物に変換して受け取る
// RestController … HTMLではなく、そのまま値を返すクラスです、という印
//
// 以下はこのアプリの部品。
// PushSubscription           … 通知の届け先1件
// PushSubscriptionForm       … 画面から届いた届け先を受け取る入れ物
// PushSubscriptionRepository … 届け先の出し入れ
// NotificationService        … 通知を送る処理
// UserService                … 利用者と設定

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskboard.domain.PushSubscription;
import com.example.taskboard.dto.PushSubscriptionForm;
import com.example.taskboard.repository.PushSubscriptionRepository;
import com.example.taskboard.service.NotificationService;
import com.example.taskboard.service.UserService;

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
 *   POST /push/test        … 届くかどうかをその場で確かめる
 *
 * どれも fetch で呼ばれるので、CSRF の合言葉をヘッダーに載せる必要がある。
 * それは push.js が受け持っている。
 */

// ===== アノテーション =====
// @RestController … 返した値がそのまま応答になる。
//                    @Controller だと "ok" をテンプレート名として探しにいってしまう。

@RestController
public class PushController {

	// ===== フィールド =====

	/** 届け先の出し入れ */
	private final PushSubscriptionRepository subscriptionRepository;

	/** いまログインしている利用者を教えてくれる係 */
	private final UserService userService;

	/** 通知を送る処理。テスト送信で使う */
	private final NotificationService notificationService;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public PushController(PushSubscriptionRepository subscriptionRepository,
			UserService userService,
			NotificationService notificationService) {

		this.subscriptionRepository = subscriptionRepository;
		this.userService = userService;
		this.notificationService = notificationService;
	}

	// ===== メソッド =====

	/**
	 * 通知の届け先を登録する。
	 *
	 * 同じ届け先がすでにあれば何もしない。
	 * ブラウザは、許可済みの状態で画面を開くたびに同じ届け先を送ってくる。
	 * 毎回保存すると、同じ通知が何度も届くことになる。
	 *
	 * @param form ブラウザから届いた届け先
	 * @return 画面では使わないが、成功したことを示す文字列
	 */
	@PostMapping("/push/subscribe")
	@Transactional
	public String subscribe(@RequestBody PushSubscriptionForm form) {

		// すでに登録済みなら、そのまま終わる
		if (subscriptionRepository.findByEndpoint(form.getEndpoint()).isPresent()) {
			return "already";
		}

		subscriptionRepository.save(new PushSubscription(
				userService.currentId(),
				form.getEndpoint(),
				form.getP256dh(),
				form.getAuth()));

		return "ok";
	}

	/**
	 * 通知の届け先を削除する。
	 *
	 * 通知を切ったときに呼ばれる。
	 * 消さずに残すと、切ったつもりでも届き続ける。
	 *
	 * deleteBy... で始まるメソッドは、呼ぶ側に @Transactional が要る。
	 * 削除は途中で止まると中途半端な状態が残るため、
	 * 「全部やるか、全部やらないか」を Spring に保証させている。
	 *
	 * @param form ブラウザから届いた届け先
	 * @return 成功したことを示す文字列
	 */
	@PostMapping("/push/unsubscribe")
	@Transactional
	public String unsubscribe(@RequestBody PushSubscriptionForm form) {
		subscriptionRepository.deleteByEndpoint(form.getEndpoint());
		return "ok";
	}

	/**
	 * いますぐ通知を送る。設定の ON/OFF も時刻も見ない。
	 *
	 * 「許可したのに本当に届くのか」を、待たずに確かめるためのもの。
	 * 締切が今日か明日のタスクが1件も無いときは、送るものが無いので何も起きない。
	 *
	 * @return 成功したことを示す文字列
	 */
	@PostMapping("/push/test")
	public String test() {
		notificationService.sendNow();
		return "ok";
	}
}
