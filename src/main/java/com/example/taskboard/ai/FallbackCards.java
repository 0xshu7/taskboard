package com.example.taskboard.ai;

// ===== インポート =====
// java.util.ArrayList … リストの実装
// java.util.List      … リストの型
//
// 以下はこのアプリの部品。
// Difficulty   … 難易度
// Granularity  … タスクの粒度
// ProposalCard … 提案カード1枚

import java.util.ArrayList;
import java.util.List;

import com.example.taskboard.domain.Difficulty;
import com.example.taskboard.domain.Granularity;
import com.example.taskboard.domain.ProposalCard;

/**
 * AI が使えないときに出す、決まった3案。
 *
 * 使われるのは2つの場面。
 * 1. 開発中（MockProposalGenerator から）
 * 2. AI の呼び出しに失敗したとき（ProposalService から）
 *
 * 「AI が落ちてもアプリは止まらない」ための最後の受け皿になっている。
 * 応答が遅い、形式が崩れる、混み合って断られる、1日の上限に達する。
 * 外のサービスを呼ぶ以上、こうしたことは必ず起きる。
 * そのとき画面にエラーを出すのではなく、3案を出して先へ進めるようにしている。
 *
 * 3案の中身は、誰にでも当てはまるものにしてある。
 * その人のことを知らずに出すので、外しようのないものだけを並べる。
 *
 * detail の最後の行は、必ず「終わり：」で始まる。
 * AI が返すものと同じ形にしておかないと、
 * フォールバックしたときだけ画面の見え方が変わってしまう。
 */
public class FallbackCards {

	// ===== コンストラクタ =====

	/**
	 * 外から作れないようにする。
	 *
	 * このクラスは値を持たず、static のメソッドを呼ぶためだけにある。
	 * new しても意味がないので、コンストラクタを private にして塞いでいる。
	 *
	 * 書かないと、Java が「引数なしのコンストラクタ」を勝手に用意してしまい、
	 * new FallbackCards() と書けてしまう。
	 */
	private FallbackCards() {
	}

	// ===== メソッド =====

	/**
	 * 固定の3案を作って返す。
	 *
	 * static が付いているので、FallbackCards.create() のようにクラス名から呼ぶ。
	 * 値を持たないクラスなので、インスタンスを作る必要がない。
	 *
	 * 呼ばれるたびに新しく作っている。
	 * 作っておいたものを使い回すと、呼び出した側が中身を書き換えたときに、
	 * 次に呼んだ人まで影響を受ける。
	 * 実際、選ばれたカードには chosen の印が付くので、使い回してはいけない。
	 *
	 * @return 3枚のカード
	 */
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

	/**
	 * カードを1枚作る。
	 *
	 * private なので、このクラスの中からしか呼べない。
	 * 同じ7行を3回書かずに済ませるための、内部だけの道具。
	 *
	 * 引数が7つと多いが、カードの項目がそのまま並んでいるだけなので、
	 * 上の呼び出しと見比べれば対応が分かる。
	 *
	 * \n は改行を表す。detail の行の区切りに使っている。
	 * 文字列の中に改行を埋め込む書き方で、画面側で改行として表示される。
	 *
	 * @param title       見出し
	 * @param category    種類
	 * @param granularity 粒度
	 * @param difficulty  難易度
	 * @param minutes     かかる時間の目安
	 * @param reason      勧める理由
	 * @param detail      手順と終わりの線
	 * @return できあがったカード
	 */
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