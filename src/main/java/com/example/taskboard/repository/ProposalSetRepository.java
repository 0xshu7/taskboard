package com.example.taskboard.repository;

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