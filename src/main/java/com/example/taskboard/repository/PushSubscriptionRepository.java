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
	 * @return 届け先の一覧
	 */
	List<PushSubscription> findByUserId(Long userId);

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
	 * 通知を切ったときや、送って「もう無効です」と返ってきたときに使う。
	 * 消さずに残すと、毎回失敗する送信を繰り返すことになる。
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
}