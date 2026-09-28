package com.example.taskboard.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.taskboard.domain.ProposalCard;
import com.example.taskboard.domain.Task;
import com.example.taskboard.logic.Situation;

/**
 * FallbackCards のテスト。
 *
 * ここは「AI が落ちたときだけ」動く場所なので、
 * ふだんの操作では壊れていても気づけない。
 * 実際、分野を3つに整理したあとも 生活 / 勉強 / 休日 のまま残っていた。
 *
 * デモ当日に AI が落ちたときに出るのはこの3案なので、
 * 形が本番とそろっているかをここで確かめる。
 */
class FallbackCardsTest {

	/** 分野は3つだけ。ここに無い言葉が混ざると、記録の集計が壊れる */
	private static final List<String> CATEGORIES =
			List.of("べんきょう", "せいかつ", "リラックス");

	/** 昼・1件目はべんきょう・2件目はせいかつ、という状況 */
	private Situation situation() {
		return new Situation("昼", "日中なので、今日中に終えられる程度のものがよい",
				"べんきょう", "せいかつ", "20〜60分");
	}

	@Test
	@DisplayName("3枚返る")
	void 三枚返る() {
		assertEquals(3, FallbackCards.create(situation(), null).size());
	}

	@Test
	@DisplayName("分野は3つのうちのいずれかで、1件目と2件目は違う")
	void 分野が正しい() {

		List<ProposalCard> cards = FallbackCards.create(situation(), null);

		for (ProposalCard card : cards) {
			assertTrue(CATEGORIES.contains(card.getCategory()),
					"知らない分野が混ざった: " + card.getCategory());
		}

		assertNotEquals(cards.get(0).getCategory(), cards.get(1).getCategory());
	}

	@Test
	@DisplayName("状況どおりの分野が入る")
	void 指定した分野が入る() {

		List<ProposalCard> cards = FallbackCards.create(situation(), null);

		assertEquals("べんきょう", cards.get(0).getCategory());
		assertEquals("せいかつ", cards.get(1).getCategory());
	}

	@Test
	@DisplayName("題名に時間帯の前置きが付く")
	void 前置きが付く() {

		List<ProposalCard> cards = FallbackCards.create(situation(), null);

		// 昼 × べんきょう の前置き。PromptBuilder と同じものを使っている
		assertTrue(cards.get(0).getTitle().startsWith("午後の集中力アップに"),
				"前置きが付いていない: " + cards.get(0).getTitle());
	}

	@Test
	@DisplayName("完了のヒントが、本番と同じ形になっている")
	void ヒントの形() {

		ProposalCard card = FallbackCards.create(situation(), null).get(0);

		String[] lines = card.getDetail().split("\n");

		assertEquals(4, lines.length, "手順3行＋おわりの目安 になっていない");

		for (int i = 0; i < 3; i++) {
			assertTrue(lines[i].startsWith("✓"), "✓ で始まっていない: " + lines[i]);
		}

		assertTrue(lines[3].startsWith("おわりの目安: "),
				"最後の行が おわりの目安 で始まっていない: " + lines[3]);
	}

	@Test
	@DisplayName("3件目は、渡したタスクをそのまま使う")
	void 三件目は既存タスク() {

		Task task = new Task();
		task.setTitle("レポートの下書きを書く");

		ProposalCard card = FallbackCards.create(situation(), task).get(2);

		// 題名は一字も変えない
		assertEquals("レポートの下書きを書く", card.getTitle());

		// 中身を知らないので、手順は書かない
		assertNull(card.getDetail());
	}

	@Test
	@DisplayName("タスクが1件も無くても3枚返る")
	void タスクが無くても三枚() {

		List<ProposalCard> cards = FallbackCards.create(situation(), null);

		assertEquals(3, cards.size());
		assertNotEquals(cards.get(1).getCategory(), cards.get(2).getCategory());
	}

	@Test
	@DisplayName("状況が無くても落ちない（モックから呼ばれる形）")
	void 状況が無くても動く() {

		List<ProposalCard> cards = FallbackCards.create(null, null);

		assertEquals(3, cards.size());
		assertEquals("べんきょう", cards.get(0).getCategory());
	}
}