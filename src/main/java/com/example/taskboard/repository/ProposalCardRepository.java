package com.example.taskboard.repository;

// ===== インポート =====
// java.time.LocalDateTime … 数えはじめる時刻の型
// java.util.List          … リストの型。3枚まとめて受け取るのに使う
//
// 以下は Spring Data JPA の部品。
// JpaRepository … 保存・取得・削除の基本的な処理をまとめて用意してくれる
// Query         … メソッド名では書けない検索を、SQL で直接書くための印
// Param         … SQL の中の :名前 と、引数を結びつける
//
// 以下はこのアプリの部品。
// ProposalCard … 提案カード1枚

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.taskboard.domain.ProposalCard;

/**
 * 提案カードの出し入れ。
 *
 * クラスではなくインターフェース。中身は1行も書かない。
 * Spring が起動時にメソッド名を読み取り、対応する SQL を自動で組み立てる。
 *
 * 独自の検索は2つ。保存と1件取り出しは JpaRepository が持っている。
 *
 * カードは利用者のIDを持っていない。
 * どのセットのものかだけを持ち、利用者はセット側がたどる形にしている。
 * 同じ情報を2か所に持つと、食い違う余地が生まれるため。
 * そのため、本人のぶんだけを数える集計でも proposal_sets を結ぶ必要がある。
 */
public interface ProposalCardRepository extends JpaRepository<ProposalCard, Long> {

	/**
	 * 1セット分のカードを、1→2→3 の順で取り出す。
	 *
	 * SELECT * FROM proposal_cards
	 * WHERE proposal_set_id = ?
	 * ORDER BY position ASC
	 *
	 * 並び順を指定しているのは、position が表示順であると同時に
	 * 重さの役割も表しているため。
	 * 1枚目が数分で終わるもの、2枚目が今日中に終わるもの、
	 * 3枚目が手が止まっている原因を片づけるもの、という並びになる。
	 *
	 * 指定しないと取り出す順番は保証されない。
	 * データベースは行の順番を約束しないので、並びが要るなら必ず書く。
	 *
	 * @param proposalSetId セットのID
	 * @return 1番目から順に並んだカード
	 */
	List<ProposalCard> findByProposalSetIdOrderByPositionAsc(Long proposalSetId);

	/**
	 * 分野ごとに「出した数」と「実際に終わった数」を数える。
	 *
	 * 報酬の材料。ProposalService がこの結果から完了率を出し、
	 * SituationJudge の点数に足す。
	 *
	 * 1行が「分野 / 出した数 / 終わった数」の3つ組で返る。
	 *
	 * 【なぜ SQL を直接書くのか】
	 *
	 * メソッド名から自動で組み立てられるのは、1つのテーブルを引く検索まで。
	 * ここは3つのテーブルにまたがる。
	 * 　proposal_cards … 分野（category）と、生まれたタスクのID
	 * 　proposal_sets  … 誰のものか（user_id）と、いつ出したか
	 * 　tasks          … そのタスクが終わったか（status）
	 *
	 * 【LEFT JOIN にしている理由】
	 *
	 * 選ばれなかったカードは result_task_id が空なので、
	 * ふつうの JOIN だと行ごと消えてしまう。
	 * 消えると「出した数」が数えられない。
	 * 出したけれど選ばれなかったことも、立派な記録なので残す。
	 *
	 * 【CASE WHEN にしている理由】
	 *
	 * MySQL の IF は H2 で動かない。
	 * dev は H2、prod は MySQL なので、両方で動く書き方にする。
	 *
	 * 【since より後だけ数える理由】
	 *
	 * 3つの質問に答えた時刻（users.profile_set_at）を渡している。
	 * 答え直すと起点が動くので、そこで評価もやりなおる。
	 * これが無いと、答えを変えても過去の記録が残り続け、
	 * 「やりなおせます」と書いてあるのに提案が変わらないことになる。
	 *
	 * 【時間帯で割っていない理由】
	 *
	 * time_of_day も持っているので、時間帯 × 分野で数えることもできる。
	 * ただし記録が 2026-09-28 から始まっていて、12マスに割ると1マスが数件になる。
	 * 時間帯の効きは SituationJudge の点数表が持っているので、ここは分野だけにした。
	 * 増えてきたら GROUP BY に s.time_of_day を足せばよい。
	 *
	 * @param userId 利用者のID
	 * @param since  この時刻より後に出したものだけを数える
	 * @return 「分野 / 出した数 / 終わった数」の並び
	 */
	@Query(value = """
			SELECT c.category,
			       COUNT(*) AS shown,
			       SUM(CASE WHEN t.status = 'DONE' THEN 1 ELSE 0 END) AS done
			FROM proposal_cards c
			JOIN proposal_sets s ON s.id = c.proposal_set_id
			LEFT JOIN tasks t ON t.id = c.result_task_id
			WHERE s.user_id = :userId
			  AND s.requested_at >= :since
			GROUP BY c.category
			""", nativeQuery = true)
	List<Object[]> countByCategory(@Param("userId") Long userId,
			@Param("since") LocalDateTime since);
}