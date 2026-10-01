package com.example.taskboard.service;

// ===== インポート =====
// java.time.LocalDateTime … 日付＋時刻。閉じた時刻を入れる
// java.util.List          … リストの型
//
// 以下は Spring の部品。
// Service       … 処理のまとまりです、という印。Spring が管理下に置く
// Transactional … メソッドの途中で失敗したら、その中の保存をすべて取り消す
//
// 以下はこのアプリの部品。
// ProposalCard           … 提案カード1枚
// ProposalSet            … 提案1セット
// ProposalStatus         … セットの状態
// ProposalCardRepository … カードの出し入れ
// ProposalSetRepository  … セットの出し入れ

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskboard.domain.ProposalCard;
import com.example.taskboard.domain.ProposalSet;
import com.example.taskboard.domain.ProposalStatus;
import com.example.taskboard.repository.ProposalCardRepository;
import com.example.taskboard.repository.ProposalSetRepository;

/**
 * 提案の書き込みだけを受け持つ。
 *
 * 【このクラスを作った理由】
 *
 * 3案を作る流れは、途中で AI を呼ぶ。応答までに3〜5秒かかる。
 *
 * 以前は ProposalService.start と retopic に @Transactional が付いていたので、
 * その3〜5秒のあいだ、データベースの接続を握ったままだった。
 * 握っているあいだ、その接続は他の画面に回せない。
 * 利用者が増えると、待っている人の数だけ接続が止まることになる。
 *
 * かといって、ProposalService の中で保存だけを別メソッドに切り出しても効かない。
 * **同じクラスの中から自分のメソッドを呼ぶと @Transactional は無視される。**
 * Spring は外から呼ばれたときだけ横取りできるため。@Async が効かなかったのと同じ理由。
 *
 * そこで、書き込みだけを別のクラスに出した。
 * ProposalService は接続を握らずに AI を呼び、
 * 保存する一瞬だけ、このクラスを通して握る。
 *
 * 【読み出しは置いていない】
 *
 * 取り出しは ProposalService の findSet / findCard に残してある。
 * あの2つは所有者を確かめる役目を持っていて、
 * そこを通らずにデータへ手が届く道を増やさないため。
 *
 * このクラスが持つのは、所有者の確認を済ませたあとの書き込みだけ。
 */
@Service
public class ProposalStore {

	// ===== フィールド =====

	/** 提案セットの出し入れ */
	private final ProposalSetRepository setRepository;

	/** 提案カードの出し入れ */
	private final ProposalCardRepository cardRepository;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public ProposalStore(ProposalSetRepository setRepository,
			ProposalCardRepository cardRepository) {

		this.setRepository = setRepository;
		this.cardRepository = cardRepository;
	}

	// ===== メソッド =====

	/**
	 * セットと3枚のカードを、まとめて保存する。
	 *
	 * セットを先に保存するのは、カードが持つ proposalSetId を決めるために
	 * セットの id が先に必要になるため。
	 *
	 * 2つを1つのトランザクションに入れているのは、
	 * カードの無いセットが残らないようにするため。
	 * 途中で失敗したら、セットの保存ごと取り消される。
	 *
	 * 番号や題名はすでに入った状態で渡される。
	 * ここでするのは保存だけで、中身の判断はしない。
	 *
	 * @param set   まだ保存していないセット
	 * @param cards 3枚のカード。位置と題名は入っている
	 * @return 保存したセット（idが入っている）
	 */
	@Transactional
	public ProposalSet save(ProposalSet set, List<ProposalCard> cards) {

		ProposalSet saved = setRepository.save(set);

		for (ProposalCard card : cards) {
			card.setProposalSetId(saved.getId());
		}

		// 1枚ずつではなく、まとめて1回で保存する
		cardRepository.saveAll(cards);

		return saved;
	}

	/**
	 * セットを閉じる。選ばれたときと、別の案を見るときの両方から呼ばれる。
	 *
	 * 閉じた時刻を入れるのは、押してから決めるまでの秒数を出すため。
	 *
	 * 閉じても消さない。
	 * 何を出して、何が選ばれなかったかは、提案の質を測る材料になる。
	 *
	 * @param set    閉じるセット。所有者の確認は済んでいる
	 * @param status CHOSEN（選ばれた）か RETOPIC（別の案を見た）
	 */
	@Transactional
	public void close(ProposalSet set, ProposalStatus status) {

		set.setStatus(status);
		set.setDecidedAt(LocalDateTime.now());

		setRepository.save(set);
	}

	/**
	 * カードを保存する。選ばれた印と、できたタスクのIDを入れたあとに呼ばれる。
	 *
	 * @param card 保存するカード
	 */
	@Transactional
	public void saveCard(ProposalCard card) {
		cardRepository.save(card);
	}
}