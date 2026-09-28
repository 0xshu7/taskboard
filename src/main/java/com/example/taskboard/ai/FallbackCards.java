package com.example.taskboard.ai;

// ===== インポート =====
// java.util.ArrayList … リストの実装
// java.util.List      … リストの型
//
// 以下はこのアプリの部品。
// Difficulty   … 難易度
// Granularity  … タスクの粒度
// ProposalCard … 提案カード1枚
// Task         … 3件目にそのまま使うタスク
// Situation    … いまの状況と、出す分野。SituationJudge が決めたもの

import java.util.ArrayList;
import java.util.List;

import com.example.taskboard.domain.Difficulty;
import com.example.taskboard.domain.Granularity;
import com.example.taskboard.domain.ProposalCard;
import com.example.taskboard.domain.Task;
import com.example.taskboard.logic.Situation;

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
 * 【v5.1 に合わせた形】
 *
 * 以前はどの場面でも同じ3案（生活・勉強・休日／3つとも5分）を返していた。
 * 分野を3つに整理したあともここが残っていたため、
 * AI が落ちるたびに「生活」という4つ目の分野が記録に混ざる状態だった。
 * 報酬の集計は category で数えるので、保険が統計を壊すことになる。
 *
 * いまは AI が返すものと同じ形にしてある。
 * 　1件目 … 重み1位の分野、3〜10分
 * 　2件目 … 重み2位の分野、少し手応えのあるもの
 * 　3件目 … 一覧から拾ったタスクそのまま
 * 前置きの言葉も PromptBuilder.wordsFor から取っている。
 * ここに同じ言葉を書き写すと、片方だけ直したときに食い違う。
 *
 * 【3件目に detail を書かない理由】
 *
 * 拾ってきたタスクの中身を、このクラスは知らない。
 * 知らないまま手順を書くと「まず手をつける」のような一般論になり、
 * それは自分でプロンプトに禁止と書いたものになる。
 * detail が空のときは画面が
 * 「ヒントはまだありません。編集から書けます」と出すので、そこに任せる。
 *
 * 【中身の決め方】
 *
 * その人のことを知らずに出すので、外しようのないものだけを並べる。
 * 部屋に何があるかも決めつけない（机・コップなど特定の物に触れない）。
 *
 * リラックスに「外に出て歩く」を置いているのは意図してのこと。
 * 「やっても何かが進むわけではないもの」を禁止しない、と決めているため
 * （WorkLog Day 5：ゴミ捨ては成功なので、禁じると害になる）。
 * 動けたなら、それで成功になる。
 *
 * detail の形は、AI が返すものと同じにしてある。
 * 　・動作の行は「✓」で始める
 * 　・最後の1行は「おわりの目安: 」で始める
 * プロンプト（PromptBuilder）と responseSchema の書き方を変えたら、ここも一緒に直すこと。
 * 実際、「終わり：」から「おわりの目安: 」へ変えたとき、3か所のうち
 * ここと responseSchema が残り、直したはずの表記が戻って見えた。
 */
public class FallbackCards {

	// ===== 定数 =====

	/** 2件目の分数。保険なので代表値を1つだけ持つ */
	private static final int LONG_MINUTES = 30;

	/** 3件目に見積もりが無いときの分数 */
	private static final int DEFAULT_MINUTES = 15;

	/** 分野の一覧。3件目に使う「残りの分野」を求めるために持つ */
	private static final List<String> CATEGORIES =
			List.of("べんきょう", "せいかつ", "リラックス");

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
	 * static が付いているので、FallbackCards.create(...) のようにクラス名から呼ぶ。
	 * 値を持たないクラスなので、インスタンスを作る必要がない。
	 *
	 * 呼ばれるたびに新しく作っている。
	 * 作っておいたものを使い回すと、呼び出した側が中身を書き換えたときに、
	 * 次に呼んだ人まで影響を受ける。
	 * 実際、選ばれたカードには chosen の印が付くので、使い回してはいけない。
	 *
	 * @param situation  いまの状況。モックから呼ぶときは null（昼として扱う）
	 * @param pickedTask 3件目に使うタスク。1件も無ければ null
	 * @return 3枚のカード
	 */
	public static List<ProposalCard> create(Situation situation, Task pickedTask) {

		// モックは時間帯を持たないので、昼として扱う。
		// 開発中の見た目を確かめるためのものなので、どの時間帯でも用は足りる。
		String timeOfDay = situation != null ? situation.getTimeOfDay() : "昼";
		String first = situation != null ? situation.getFirstCategory() : "べんきょう";
		String second = situation != null ? situation.getSecondCategory() : "せいかつ";

		List<ProposalCard> cards = new ArrayList<>();
		cards.add(shortCard(timeOfDay, first));
		cards.add(longCard(timeOfDay, second));

		if (pickedTask != null) {
			cards.add(existingCard(pickedTask, rest(first, second)));
		} else {
			cards.add(longCard(timeOfDay, rest(first, second)));
		}
		return cards;
	}

	/**
	 * 1件目。3〜10分で、今日はもう動けないと思っている人でも手が出るもの。
	 *
	 * @param timeOfDay 朝・昼・夜・深夜
	 * @param category  出す分野
	 * @return カード1枚
	 */
	private static ProposalCard shortCard(String timeOfDay, String category) {

		String words = PromptBuilder.wordsFor(timeOfDay, category);

		return switch (category) {
			case "べんきょう" -> card(
					words + "教材の目次を眺める", category,
					Granularity.FIVE_MIN, Difficulty.LOW, 5,
					"読まなくてよいと思えば、開くだけならできるので",
					"✓教材を手元に出す\n✓目次のページを開く\n"
							+ "✓見出しを上から下まで目で追う\n"
							+ "おわりの目安: 目次を最後まで見終わっている");
			case "せいかつ" -> card(
					words + "出しっぱなしのものを3つ戻す", category,
					Granularity.FIVE_MIN, Difficulty.LOW, 5,
					"まず手を動かすと、次に進みやすいので",
					"✓いま出しっぱなしのものを見わたす\n"
							+ "✓置き場所が決まっているものを3つ選ぶ\n"
							+ "✓その3つを元の場所に戻す\n"
							+ "おわりの目安: 出しっぱなしのものが3つ減っている");
			default -> card(
					words + "深呼吸を10回する", category,
					Granularity.FIVE_MIN, Difficulty.LOW, 5,
					"息を長く吐くと、体のほうから落ち着くので",
					"✓背もたれから背中を離して座る\n✓鼻から4つ数えて吸う\n"
							+ "✓口から8つ数えて吐くのを10回くり返す\n"
							+ "おわりの目安: 10回吐き終わっている");
		};
	}

	/**
	 * 2件目。ちょっとの時間をつくればできること。
	 *
	 * @param timeOfDay 朝・昼・夜・深夜
	 * @param category  出す分野
	 * @return カード1枚
	 */
	private static ProposalCard longCard(String timeOfDay, String category) {

		String words = PromptBuilder.wordsFor(timeOfDay, category);

		return switch (category) {
			case "べんきょう" -> card(
					words + "教材を1つの章だけ進める", category,
					Granularity.TODAY, Difficulty.MID, LONG_MINUTES,
					"範囲を先に決めると、終わりが見えるので",
					"✓きょう進める章を1つ決める\n✓その章を最後まで読む\n"
							+ "✓分からなかった行に印を付ける\n"
							+ "おわりの目安: 決めた章の最後のページまで進んでいる");
			case "せいかつ" -> card(
					words + "1か所だけ片づける", category,
					Granularity.TODAY, Difficulty.MID, LONG_MINUTES,
					"場所を1つに絞ると、終わりが決まるので",
					"✓片づける場所を1か所だけ決める\n"
							+ "✓そこにあるものを全部いったん外に出す\n"
							+ "✓使うものだけ戻す\n"
							+ "おわりの目安: 決めた1か所に、使うものだけが並んでいる");
			default -> card(
					words + "外に出て歩く", category,
					Granularity.TODAY, Difficulty.LOW, LONG_MINUTES,
					"体を動かすと、気持ちの向きが変わることがあるので",
					"✓靴をはいて玄関を出る\n✓同じ方向に15分歩く\n"
							+ "✓向きを変えて戻ってくる\n"
							+ "おわりの目安: 家に着いている");
		};
	}

	/**
	 * 3件目。一覧から拾ったタスクを、そのまま1枚にする。
	 *
	 * title は一字も変えない。AI に頼むときと同じ決まり。
	 * detail は書かない（中身を知らないため。クラスの説明に理由を書いた）。
	 *
	 * 分野は「残りの分野」を入れている。
	 * 拾ってきたタスクがどの分野なのかは、このクラスには分からない。
	 * 3分野のうち1件目・2件目で使っていないものを充てておけば、
	 * 記録の分野が3つの中に収まる。
	 *
	 * 見積りと難易度は、そのタスクに入っている値を使う。
	 * 入っていなければ既定値。null をそのまま入れると、
	 * 画面側で難易度が空のまま描かれる。
	 *
	 * @param task     拾ってきたタスク
	 * @param category 残りの分野
	 * @return カード1枚
	 */
	private static ProposalCard existingCard(Task task, String category) {

		int minutes = task.getEstimatedMinutes() != null
				? task.getEstimatedMinutes()
				: DEFAULT_MINUTES;

		Difficulty difficulty = task.getDifficulty() != null
				? task.getDifficulty()
				: Difficulty.MID;

		return card(task.getTitle(), category,
				Granularity.TODAY, difficulty, minutes,
				"リストに残っているので、ここで片づけておくと軽くなります",
				null);
	}

	/**
	 * 1件目・2件目で使っていない分野を返す。
	 *
	 * 3分野のうち2つが決まっているので、残りは必ず1つ。
	 * 最後の return は、万一2つとも同じ分野が渡ってきたときの逃げ道。
	 *
	 * @param first  1件目の分野
	 * @param second 2件目の分野
	 * @return 残りの分野
	 */
	private static String rest(String first, String second) {

		for (String category : CATEGORIES) {
			if (!category.equals(first) && !category.equals(second)) {
				return category;
			}
		}
		return CATEGORIES.get(0);
	}

	/**
	 * カードを1枚作る。
	 *
	 * private なので、このクラスの中からしか呼べない。
	 * 同じ7行を何度も書かずに済ませるための、内部だけの道具。
	 *
	 * \n は改行を表す。detail の行の区切りに使っている。
	 * 文字列の中に改行を埋め込む書き方で、画面側で改行として表示される。
	 *
	 * @param title       見出し
	 * @param category    分野
	 * @param granularity 粒度
	 * @param difficulty  難易度
	 * @param minutes     かかる時間の目安
	 * @param reason      勧める理由
	 * @param detail      手順とおわりの目安。書けないときは null
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