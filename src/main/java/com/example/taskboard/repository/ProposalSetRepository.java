package com.example.taskboard.repository;

// ===== インポート =====
// java.util.Optional … 「あるかもしれないし、無いかもしれない」入れ物
//
// 以下は Spring Data JPA の部品。
// JpaRepository … 保存・取得・削除の基本的な処理をまとめて用意してくれる
//
// 以下はこのアプリの部品。
// ProposalSet    … 提案1セット
// ProposalStatus … セットの状態

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskboard.domain.ProposalSet;
import com.example.taskboard.domain.ProposalStatus;

/**
 * 提案セットの出し入れ。
 *
 * クラスではなくインターフェース。中身は1行も書かない。
 * Spring が起動時にメソッド名を読み取り、対応する SQL を自動で組み立てる。
 *
 * 自前で持っているのは1つだけ。作りおきを1件だけ探すもの。
 * 残りの取り出しは JpaRepository が最初から持っている findById で足りる。
 */
public interface ProposalSetRepository extends JpaRepository<ProposalSet, Long> {

	/**
	 * まだ使われていない（OPEN のままの）セットのうち、いちばん新しいものを1件取る。
	 *
	 * SELECT * FROM proposal_sets
	 * WHERE user_id = ? AND status = ?
	 * ORDER BY id DESC
	 * LIMIT 1
	 *
	 * findFirst を付けると「最初の1件だけ」という意味になる。
	 * OrderByIdDesc と組み合わせているので、いちばん新しいものが取れる。
	 *
	 * 1件に絞るのは、スタート画面を開くたびに作りおきが増える可能性があるため。
	 * 複数あっても使うのは1つなので、最新のものだけを取る。
	 *
	 * 戻り値が Optional なのは、作りおきが無いこともあるため。
	 * 無ければその場で作る、という分岐を呼ぶ側で書く。
	 *
	 * @param userId 利用者のID
	 * @param status 探す状態。OPEN を渡す
	 * @return 見つかったセット。無ければ空
	 */
	Optional<ProposalSet> findFirstByUserIdAndStatusOrderByIdDesc(
			Long userId, ProposalStatus status);

	/**
	 * 3択から選んだ回数を数える。実績画面のレベルに使う。
	 *
	 * SELECT COUNT(*) FROM proposal_sets WHERE user_id = ? AND status = ?
	 *
	 * カードではなくセットの側を数えている。
	 * カードは利用者のIDを持っていないが、セットは持っているため。
	 *
	 * @param userId 利用者のID
	 * @param status 対象の状態。CHOSEN を渡す
	 * @return 選んだ回数
	 */
	long countByUserIdAndStatus(Long userId, ProposalStatus status);
}