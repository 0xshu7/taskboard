package com.example.taskboard.service;

// ===== インポート =====
// java.util.List … リストの型
//
// 以下は Spring の部品。
// Service       … 処理のまとまりです、という印。Spring が管理下に置く
// Transactional … メソッドの途中で失敗したら、その中の保存をすべて取り消す
//
// 以下はこのアプリの部品。
// PushSubscription           … 通知の届け先1件
// PushSubscriptionRepository … 届け先の出し入れ

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskboard.domain.PushSubscription;
import com.example.taskboard.repository.PushSubscriptionRepository;

/**
 * 通知の届け先を出し入れする。
 *
 * 【このクラスを作った理由】
 *
 * 届け先の削除には @Transactional が要る（deleteBy... で始まるメソッドのため）。
 * だが削除が必要になるのは、通知を送ってみて 404 や 410 が返ったときで、
 * それは外部への送信の途中にあたる。
 *
 * NotificationService の中で削除まで済ませようとすると、2つのうち一方になる。
 *
 * 1. 送信するメソッドに @Transactional を付ける
 *    → 相手の応答を待っているあいだ、データベースの接続を握ったままになる。
 *      通知の件数だけ繰り返すので、待ち時間が積み上がる。
 *
 * 2. 削除だけを別メソッドに切り出して、同じクラスの中から呼ぶ
 *    → **効かない。** Spring は外から呼ばれたときだけ横取りできるので、
 *      自分のメソッドを自分で呼ぶと @Transactional が無視される。
 *      @Async が効かなかったのと同じ理由。
 *
 * そこで別のクラスにした。外から呼ばれる形になるので、@Transactional が効く。
 * 通信をしているあいだは接続を握らず、消すときだけ短く握る。
 *
 * 【届け先に触る処理は、すべてここを通す】
 *
 * 以前は PushController と SettingsController が repository を直接呼んでいた。
 * 「controller は service だけを呼ぶ」という形から、その2つだけ外れていた。
 *
 * 出し入れを1か所にまとめてあるので、
 * 「届け先をどう扱うか」を変えたいとき、探す場所がここだけで済む。
 *
 * 利用者IDはブラウザから受け取らない。
 * userService から取るので、他人の届け先として登録することができない。
 */
@Service
public class PushSubscriptionService {

	// ===== フィールド =====

	/** 届け先の出し入れ */
	private final PushSubscriptionRepository subscriptionRepository;

	/** いまログインしている利用者を教えてくれる係 */
	private final UserService userService;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public PushSubscriptionService(PushSubscriptionRepository subscriptionRepository,
			UserService userService) {

		this.subscriptionRepository = subscriptionRepository;
		this.userService = userService;
	}

	// ===== メソッド =====

	/**
	 * ある利用者の届け先をすべて返す。
	 *
	 * readOnly を付けているのは、読むだけだと分かる形にするため。
	 * 書き込みの準備をしないので、わずかだが軽くもなる。
	 *
	 * 引数で利用者IDを受け取るのは、毎朝の送信から呼ばれるため。
	 * そのときはログインしている人がいないので、userService からは取れない。
	 *
	 * @param userId 利用者のID
	 * @return 届け先。1件も無ければ空のリスト
	 */
	@Transactional(readOnly = true)
	public List<PushSubscription> findFor(Long userId) {
		return subscriptionRepository.findByUserId(userId);
	}

	/**
	 * 届け先が1件でもあるかを返す。設定画面のスイッチの状態になる。
	 *
	 * 通知の入切を表す列は作っていない。
	 * 届け先の行があるかどうか、そのものが入切を表している。
	 * 同じことを2か所に持つと、必ずどこかで食い違うため。
	 *
	 * findFor で取ってきて件数を見ることもできるが、
	 * それだと端末の数だけ行を読み込むことになる。
	 * 知りたいのは「あるか無いか」だけなので、こちらで足りる。
	 *
	 * @param userId 利用者のID
	 * @return 1件でもあれば true
	 */
	@Transactional(readOnly = true)
	public boolean existsFor(Long userId) {
		return subscriptionRepository.existsByUserId(userId);
	}

	/**
	 * その届け先が、いまの利用者のものとして登録されているかを返す。
	 *
	 * 設定画面のスイッチを、端末ごとの状態に合わせるために使う。
	 *
	 * existsFor は利用者ごとなので、2台で使うと、片方で入にしただけで
	 * もう片方も入に見える。サーバーは端末を見分けられないため、
	 * 「この端末の届け先」をブラウザ側から渡してもらって照合する。
	 *
	 * 他人の届け先を渡されたときは false を返す。
	 * 登録の有無を外から調べられないようにしている。
	 *
	 * @param endpoint ブラウザが持っている届け先
	 * @return 自分のものとして登録されていれば true
	 */
	@Transactional(readOnly = true)
	public boolean isRegistered(String endpoint) {

		if (isBlank(endpoint)) {
			return false;
		}

		return subscriptionRepository.findByEndpoint(endpoint)
				.filter(subscription -> subscription.getUserId().equals(userService.currentId()))
				.isPresent();
	}

	/**
	 * 届け先を登録する。
	 *
	 * 同じ届け先がすでにあれば何もしない。
	 * ブラウザは、許可済みの状態で画面を開くたびに同じ届け先を送ってくる。
	 * 毎回保存すると、同じ通知が何度も届くことになる。
	 *
	 * 中身が空のときも何もしない。
	 * ブラウザ側で組み立てに失敗すると、空のまま送られてくることがある。
	 * 保存してしまうと、送信のたびに失敗する行が残り続ける。
	 *
	 * @param endpoint 届け先
	 * @param p256dh   暗号化の鍵
	 * @param auth     暗号化の鍵
	 * @return 結果を示す文字列（ok / already / invalid）
	 */
	@Transactional
	public String register(String endpoint, String p256dh, String auth) {

		if (isBlank(endpoint) || isBlank(p256dh) || isBlank(auth)) {
			return "invalid";
		}

		// すでに登録済みなら、そのまま終わる
		if (subscriptionRepository.findByEndpoint(endpoint).isPresent()) {
			return "already";
		}

		subscriptionRepository.save(new PushSubscription(
				userService.currentId(), endpoint, p256dh, auth));

		return "ok";
	}

	/**
	 * 届け先を削除する。通知を切ったときに呼ばれる。
	 *
	 * 自分の届け先だけを消す。
	 * endpoint だけを見て消すこともできるが、それだと
	 * 他人の endpoint を知っている人が、その通知を切れてしまう。
	 *
	 * 推測できる長さの文字列ではないので危険は低い。
	 * ただ「自分のものだけ操作できる」という線を、
	 * タスクと同じようにここでも引いておく。
	 *
	 * @param endpoint 消す届け先
	 * @return 結果を示す文字列（ok / invalid）
	 */
	@Transactional
	public String unsubscribe(String endpoint) {

		if (isBlank(endpoint)) {
			return "invalid";
		}

		subscriptionRepository.deleteByEndpointAndUserId(endpoint, userService.currentId());
		return "ok";
	}

	/**
	 * 使えなくなった届け先を削除する。
	 *
	 * 送信したときに 404 や 410 が返った届け先に対して、NotificationService から呼ばれる。
	 * 本人の操作ではないので、所有者は確かめない。
	 * 相手のサービスが「もう無い」と答えた届け先なので、残す理由がない。
	 *
	 * 通信の途中から呼ばれるため、ここだけが短く接続を握る形になっている。
	 *
	 * @param endpoint 消す届け先
	 */
	@Transactional
	public void removeDead(String endpoint) {
		subscriptionRepository.deleteByEndpoint(endpoint);
	}

	// ===== メソッド（内部で使うもの） =====

	/**
	 * 中身が無い文字かどうかを判定する。
	 *
	 * isBlank は「空文字か、空白だけか」を見る。
	 * isEmpty だと、空白1文字が通ってしまう。
	 *
	 * @param text 判定する文字
	 * @return 中身が無ければ true
	 */
	private boolean isBlank(String text) {
		return text == null || text.isBlank();
	}
}