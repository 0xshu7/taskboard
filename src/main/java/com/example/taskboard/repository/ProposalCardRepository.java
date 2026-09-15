package com.example.taskboard.repository;

// ===== インポート =====
// java.util.List … リストの型。3枚まとめて受け取るのに使う
//
// 以下は Spring Data JPA の部品。
// JpaRepository … 保存・取得・削除の基本的な処理をまとめて用意してくれる
//
// 以下はこのアプリの部品。
// ProposalCard … 提案カード1枚

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskboard.domain.ProposalCard;

/**
 * 提案カードの出し入れ。
 *
 * クラスではなくインターフェース。中身は1行も書かない。
 * Spring が起動時にメソッド名を読み取り、対応する SQL を自動で組み立てる。
 *
 * 独自の検索は1つだけ。保存と1件取り出しは JpaRepository が持っている。
 *
 * カードは利用者のIDを持っていない。
 * どのセットのものかだけを持ち、利用者はセット側がたどる形にしている。
 * 同じ情報を2か所に持つと、食い違う余地が生まれるため。
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
}