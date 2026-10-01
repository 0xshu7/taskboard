package com.example.taskboard.ai;

// ===== インポート =====
// java.util.ArrayList … リストの実装
// java.util.List      … リストの型
//
// 以下はログの部品。Spring Boot に最初から入っているので、pom.xml に足すものは無い。
// Logger        … ログを出す道具
// LoggerFactory … そのクラス用の Logger を作る
//
// 以下はこのアプリの部品。
// Difficulty   … 難易度
// Granularity  … タスクの粒度
// ProposalCard … 提案カード1枚
// Task         … 3件目にそのまま使うタスク
// Categories   … 分野の名前
// Situation    … いまの状況と、出す分野。SituationJudge が決めたもの

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.taskboard.domain.Difficulty;
import com.example.taskboard.domain.Granularity;
import com.example.taskboard.domain.ProposalCard;
import com.example.taskboard.domain.Task;
import com.example.taskboard.logic.Categories;
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
 * 【detail の形】
 *
 * 　1行目 … すぐ手が出る入口
 * 　2行目 … このタスクの中心。ここが終われば「やった」と言える
 * 　3行目 … 2行目をやっている間にできる、ほんの少しの追加
 * 　おわりの目安 … 2行目が終わる時点。3行目は待たない
 *
 * 終わりの線を、やることの総量より手前に置いている。
 * 3行目はできたら儲けもの。動けたなら、それで成功。
 *
 * プロンプトと responseSchema の書き方を変えたら、ここも一緒に直すこと。
 * 直す場所は3か所ある。
 *
 * 【v5.1 で直したこと】
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
 * title に時間帯の言葉を書かないこと。前置きが二重になる。
 *
 * 分野の名前も同じ考えで、Categories が持っているものを使う。
 * 以前はここと SituationJudge が同じ一覧をそれぞれ書いていた。
 *
 * 【想定外の分野が来たとき】
 *
 * 分野は3つと決めているが、呼び出し側が変われば4つ目が来ることもありうる。
 * そのときは normalize でリラックスに寄せ、ログに残す。
 * ここで例外を投げてはいけない。AI が落ちたときの逃げ道が落ちると、
 * 「落ちても止まらない」が成り立たなくなる。
 * 黙って通すと、上の v5.1 と同じことがもう一度起きる。
 *
 * 【3件目に detail を書かない理由】
 *
 * 拾ってきたタスクの中身を、このクラスは知らない。
 * 知らないまま手順を書くと「まず手をつける」のような一般論になる。
 * detail が空のときは画面が
 * 「ヒントはまだありません。編集から書けます」と出すので、そこに任せる。
 *
 * 【中身の決め方】
 *
 * その人のことを知らずに出すので、外しようのないものだけを並べる。
 *
 * リラックスに「おさんぽ」を置いているのは意図してのこと。
 * 「やっても何かが進むわけではないもの」を禁止しない、と決めているため
 * （WorkLog Day 5：ゴミ捨ては成功なので、禁じると害になる）。
 * 動けたなら、それで成功になる。
 */
public class FallbackCards {

	// ===== 定数 =====

	/**
	 * ログを出す道具。
	 *
	 * このクラスのメソッドはすべて static なので、こちらも static にする。
	 * 他のクラスと違い、インスタンスが作られないため。
	 */
	private static final Logger log = LoggerFactory.getLogger(FallbackCards.class);

	/** 2件目の分数。保険なので代表値を1つだけ持つ */
	private static final int LONG_MINUTES = 30;

	/** 3件目に見積もりが無いときの分数 */
	private static final int DEFAULT_MINUTES = 15;

	/** 想定外の分野が来たときの寄せ先 */
	private static final String FALLBACK_CATEGORY = Categories.RELAX;

	// ===== コンストラクタ =====

	/**
	 * 外から作れないようにする。
	 *
	 * このクラスは値を持たず、static のメソッドを呼ぶためだけにある。
	 * new しても意味がないので、コンストラクタを private にして塞いでいる。
	 */
	private FallbackCards() {
	}

	// ===== メソッド =====

	/**
	 * 固定の3案を作って返す。
	 *
	 * 呼ばれるたびに新しく作っている。
	 * 選ばれたカードには chosen の印が付くので、使い回してはいけない。
	 *
	 * @param situation  いまの状況。モックから呼ぶときは null（昼として扱う）
	 * @param pickedTask 3件目に使うタスク。1件も無ければ null
	 * @return 3枚のカード
	 */
	public static List<ProposalCard> create(Situation situation, Task pickedTask) {

		// モックは時間帯を持たないので、昼として扱う。
		// 開発中の見た目を確かめるためのものなので、どの時間帯でも用は足りる。
		String timeOfDay = situation != null ? situation.getTimeOfDay() : "昼";
		String first = situation != null ? situation.getFirstCategory() : Categories.STUDY;
		String second = situation != null ? situation.getSecondCategory() : Categories.LIFE;

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
	 * 決めた3つの分野に収める。
	 *
	 * 一覧に無いものが来たら、リラックスに寄せてログに残す。
	 * 寄せた値はカードの category にもそのまま入るので、
	 * 記録に4つ目の分野が混ざることはない。
	 *
	 * 寄せたこと自体は起きてほしくないことなので warn で出す。
	 * 普通に動いているときは1行も出ない。
	 *
	 * List.of で作った一覧は contains(null) で例外を投げるため、
	 * null の判定を先に置いている。
	 *
	 * @param category 呼び出し側から渡された分野
	 * @return 3つのうちのどれか
	 */
	private static String normalize(String category) {

		if (category != null && Categories.ALL.contains(category)) {
			return category;
		}
		log.warn("想定外の分野: {} → {} として返す", category, FALLBACK_CATEGORY);
		return FALLBACK_CATEGORY;
	}

	/**
	 * 1件目。3〜10分で、今日はもう動けないと思っている人でも手が出るもの。
	 *
	 * 分野は normalize を通してから使う。
	 * そのため default に来るのはリラックスだけになる。
	 *
	 * @param timeOfDay 朝・昼・夜・深夜
	 * @param category  出す分野
	 * @return カード1枚
	 */
	private static ProposalCard shortCard(String timeOfDay, String category) {

		String target = normalize(category);
		String words = PromptBuilder.wordsFor(timeOfDay, target);

		return switch (target) {
			case Categories.STUDY -> card(
					words + "サクッと1問クリア！", target,
					Granularity.FIVE_MIN, Difficulty.LOW, 5,
					"1問解くだけで、脳のやる気スイッチON！",
					"✓一番解けそうな1問をロックオン！\n"
							+ "✓集中して一気にアタック\n"
							+ "✓花マルをつけて「できた！」を実感\n"
							+ "おわりの目安: 1問解けたらクエスト達成！");
			case Categories.LIFE -> card(
					words + "すきな1杯でごほうびタイム", target,
					Granularity.FIVE_MIN, Difficulty.LOW, 5,
					"1杯味わうだけで、気分スッキリリセット！",
					"✓とっておきの飲みものを用意する\n"
							+ "✓ゆったり座って、一口ずつ味わう\n"
							+ "✓ボーッと外を眺めて頭をからっぽに\n"
							+ "おわりの目安: 飲み終わったらチャージ完了！");
			default -> card(
					words + "深呼吸10回でパワーチャージ！", target,
					Granularity.FIVE_MIN, Difficulty.LOW, 5,
					"息を吐ききるたび、体が軽～くなる！",
					"✓いすに深く座って、背筋をピン！\n"
							+ "✓鼻から吸って、口から「ふぅ～」と長く吐く\n"
							+ "✓肩の力をストーンと落とす\n"
							+ "おわりの目安: 10回終わったら完全復活！");
		};
	}

	/**
	 * 2件目。ちょっとの時間をつくればできること。
	 *
	 * 3行の役割と、おわりの目安の置き方は shortCard と同じ。
	 * 分野の扱いも shortCard と同じ。
	 *
	 * @param timeOfDay 朝・昼・夜・深夜
	 * @param category  出す分野
	 * @return カード1枚
	 */
	private static ProposalCard longCard(String timeOfDay, String category) {

		String target = normalize(category);
		String words = PromptBuilder.wordsFor(timeOfDay, target);

		return switch (target) {
			case Categories.STUDY -> card(
					words + "今日のゴールまで一気に駆け抜ける！", target,
					Granularity.TODAY, Difficulty.MID, LONG_MINUTES,
					"ゴールを決めれば、あとは走るだけ！",
					"✓今日のウイニングゾーン（範囲）を決定！\n"
							+ "✓タイマーをセットしてノリノリで進める\n"
							+ "✓迷った所は「レベルアップの宝（印）」にする\n"
							+ "おわりの目安: 決めた範囲が終わったらゴールイン！");
			case Categories.LIFE -> card(
					words + "1か所ピカピカ大作戦！", target,
					Granularity.TODAY, Difficulty.MID, LONG_MINUTES,
					"1か所キレイにするだけで、空間も気分も大変身！",
					"✓「ここだけキレイにする！」とターゲットロック\n"
							+ "✓リズムよくサクッと片付ける\n"
							+ "✓いらないものを手放して視界スッキリ\n"
							+ "おわりの目安: 1か所ピカッとしたら完全攻略！");
			default -> card(
					words + "気ままに冒険おさんぽタイム", target,
					Granularity.TODAY, Difficulty.LOW, LONG_MINUTES,
					"外の空気を吸うだけで、アイデアがひらめく！",
					"✓お気に入りの靴をはいて出発！\n"
							+ "✓風を感じながら15分気ままに歩く\n"
							+ "✓あえて知らない曲がり角を曲がってみる\n"
							+ "おわりの目安: 家に着いたら冒険完了！");
		};
	}

	/**
	 * 3件目。一覧から拾ったタスクを、そのまま1枚にする。
	 *
	 * title は一字も変えない。AI に頼むときと同じ決まり。
	 * detail は書かない（中身を知らないため）。
	 *
	 * 分野は「残りの分野」を入れている。
	 * 拾ってきたタスクがどの分野なのかは、このクラスには分からない。
	 * 3分野のうち1件目・2件目で使っていないものを充てておけば、
	 * 記録の分野が3つの中に収まる。
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

		return card(task.getTitle(), normalize(category),
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

		for (String category : Categories.ALL) {
			if (!category.equals(first) && !category.equals(second)) {
				return category;
			}
		}
		return Categories.ALL.get(0);
	}

	/**
	 * カードを1枚作る。
	 *
	 * private なので、このクラスの中からしか呼べない。
	 * 同じ7行を何度も書かずに済ませるための、内部だけの道具。
	 *
	 * \n は改行を表す。detail の行の区切りに使っている。
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