package com.example.taskboard.repository;

// ===== インポート =====
// java.util.List     … リストの型
// java.util.Optional … 「あるかもしれないし、無いかもしれない」入れ物
//
// 以下は Spring Data JPA の部品。
// JpaRepository … 保存・取得・削除の基本的な処理をまとめて用意してくれる
//
// 以下はこのアプリの部品。
// PushSubscription … 通知の届け先1件

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskboard.domain.PushSubscription;

/**
 * 通知の届け先の出し入れ。
 *
 * クラスではなくインターフェース。中身は1行も書かない。
 * Spring が起動時にメソッド名を読み取り、対応する SQL を自動で組み立てる。
 *
 * 条件に書く項目名は、PushSubscription のフィールド名と一致させる。
 * 列名（user_id）ではなくフィールド名（userId）で書く。
 *
 * 呼ぶのは PushSubscriptionService だけにしてある。
 * 以前は controller から直接呼んでいる箇所が2つあったが、
 * 「controller は service だけを呼ぶ」という形に合わせて寄せた。
 */
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

	/**
	 * ある利用者の届け先を全部取る。
	 *
	 * SELECT * FROM push_subscriptions WHERE user_id = ?
	 *
	 * 1人が複数の端末で許可していることがあるので、リストで返る。
	 * 送るときは、この全部に送る。
	 *
	 * @param userId 利用者のID
	 * @return 届け先の一覧。1件も無ければ空のリスト
	 */
	List<PushSubscription> findByUserId(Long userId);

	/**
	 * この利用者の届け先が1件でもあるかを返す。
	 *
	 * SELECT COUNT(*) > 0 FROM push_subscriptions WHERE user_id = ?
	 *
	 * 設定画面のスイッチを入で表示するかどうかの判定に使う。
	 * 通知の入切を表す列は作っていないので、行の有無がそのまま入切を表す。
	 *
	 * findByUserId で取ってきて件数を見ることもできるが、
	 * それだと端末の数だけ行を読み込むことになる。
	 * 知りたいのは「あるか無いか」だけなので、こちらで足りる。
	 *
	 * @param userId 利用者のID
	 * @return 1件でもあれば true
	 */
	boolean existsByUserId(Long userId);

	/**
	 * 届け先のURLで1件探す。
	 *
	 * SELECT * FROM push_subscriptions WHERE endpoint = ?
	 *
	 * すでに登録済みかどうかを確かめるために使う。
	 * 同じ端末で許可を取り直したときに、行が増えないようにする。
	 *
	 * @param endpoint 届け先のURL
	 * @return 見つかった届け先。無ければ空
	 */
	Optional<PushSubscription> findByEndpoint(String endpoint);

	/**
	 * 届け先のURLで削除する。
	 *
	 * DELETE FROM push_subscriptions WHERE endpoint = ?
	 *
	 * 送ってみて「もう無効です」（404 / 410）と返ってきたときに使う。
	 * 消さずに残すと、毎回失敗する送信を繰り返すことになる。
	 *
	 * こちらは所有者を見ない。
	 * 本人の操作ではなく、相手のサービスが「もう無い」と答えた届け先なので、
	 * 誰のものであっても残す理由がない。
	 *
	 * deleteBy... で始まるメソッドは、呼ぶ側に @Transactional が要る。
	 * 付けずに呼ぶと、起動はするが実行時に失敗する。
	 *
	 * 戻り値を void ではなく long にしておくと、何件消えたかが分かる。
	 *
	 * @param endpoint 届け先のURL
	 * @return 消した件数
	 */
	long deleteByEndpoint(String endpoint);

	/**
	 * 自分の届け先だけを、URLで削除する。
	 *
	 * DELETE FROM push_subscriptions WHERE endpoint = ? AND user_id = ?
	 *
	 * 画面から通知を切ったときに使う。
	 * endpoint だけを見て消すこともできるが、それだと
	 * 他人の endpoint を知っている人が、その通知を切れてしまう。
	 *
	 * 推測できる長さの文字列ではないので危険は低い。
	 * ただ「自分のものだけ操作できる」という線を、
	 * タスクと同じようにここでも引いておく。
	 *
	 * こちらも呼ぶ側に @Transactional が要る。
	 *
	 * @param endpoint 届け先のURL
	 * @param userId   利用者のID
	 * @return 消した件数。他人のものを指していれば0
	 */
	long deleteByEndpointAndUserId(String endpoint, Long userId);
}