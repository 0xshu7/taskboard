package com.example.taskboard.repository;

// ===== インポート =====
// 以下は Spring Data JPA の部品。
// JpaRepository … 保存・取得・削除の基本的な処理をまとめて用意してくれる
//
// 以下はこのアプリの部品。
// ProposalSet … 提案1セット

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskboard.domain.ProposalSet;

/**
 * 提案セットの出し入れ。
 *
 * クラスではなくインターフェース。中身は1行も書かない。
 * Spring が起動時にメソッド名を読み取り、対応する SQL を自動で組み立てる。
 *
 * 【自前のメソッドが1つも無い】
 *
 * このアプリで提案セットに対してすることは、保存と1件の取り出しだけ。
 * どちらも JpaRepository が最初から持っている save と findById で足りる。
 *
 * 以前は2つ持っていたが、どちらも使わなくなった。
 *
 * 　1. 未使用のセットを1件探すもの
 * 　　 スタート画面を開いた時点で3案を作っておき、押されたらそれを出す
 * 　　 仕組みに使っていた。その仕組みをやめたので要らなくなった。
 *
 * 　2. 選んだ回数を数えるもの
 * 　　 実績画面のレベル表示に使っていた。画面ごと削除したので要らなくなった。
 *
 * 呼ばれないメソッドが残っていると、読んだ人が
 * 「どこかで使われているはずだ」と探すことになる。
 * 使わなくなったものは消す。
 *
 * 分野ごとの集計は、こちらではなく ProposalCardRepository.countByCategory が持つ。
 * 数えたいのは分野ごとの出した数と終わった数で、どちらもカード側の情報のため。
 */
public interface ProposalSetRepository extends JpaRepository<ProposalSet, Long> {
}