package com.example.taskboard.ai;

import java.util.ArrayList;
import java.util.List;

import com.example.taskboard.domain.Difficulty;
import com.example.taskboard.domain.Granularity;
import com.example.taskboard.domain.ProposalCard;

// AI が使えないときに出す、決まった3案。
// モック（開発用）と、AI失敗時のフォールバックの両方で使う。
// 「AIが落ちてもアプリは止まらない」ための最後の受け皿
public class FallbackCards {

	// インスタンスを作る必要がないクラスなので、コンストラクタを隠す
	private FallbackCards() {
	}

	public static List<ProposalCard> create() {

		List<ProposalCard> cards = new ArrayList<>();

		cards.add(card("机の上のものを3つ、元の場所に戻す", "生活",
				Granularity.FIVE_MIN, Difficulty.LOW, 5,
				"まず手を動かすと、次に進みやすいので",
				"机の上にあるものを見わたす\n置き場所が決まっているものを3つ選ぶ\nその3つを元の場所に戻す\n終わり：机の上から3つ減っている"));

		cards.add(card("参考書を開いて、見出しだけ眺める", "勉強",
				Granularity.FIVE_MIN, Difficulty.LOW, 5,
				"読まなくてよいと思えば、開くだけならできるので",
				"参考書を机に出す\n目次のページを開く\n見出しを上から下まで目で追う\n終わり：目次を最後まで見終わっている"));

		cards.add(card("外に出て5分だけ歩く", "休日",
				Granularity.FIVE_MIN, Difficulty.LOW, 5,
				"体を動かすと、気持ちの向きが変わることがあるので",
				"靴をはいて玄関を出る\n同じ方向に2分半だけ歩く\n向きを変えて戻ってくる\n終わり：家に着いている"));

		return cards;
	}

	private static ProposalCard card(String title, String category,
			Granularity granularity, Difficulty difficulty,
			int minutes, String reason, String detail) {

		ProposalCard c = new ProposalCard();
		c.setTitle(title);
		c.setCategory(category);
		c.setGranularity(granularity);
		c.setDifficulty(difficulty);
		c.setEstimatedMinutes(minutes);
		c.setReason(reason);
		c.setDetail(detail);
		return c;
	}
}