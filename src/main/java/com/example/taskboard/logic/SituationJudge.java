package com.example.taskboard.logic;

// ===== インポート =====
// java.time.DayOfWeek     … 曜日。土日かどうかの判定に使う
// java.time.LocalDateTime … 日付＋時刻。時間帯と曜日の両方が要るため
// java.util.ArrayList     … 並べ替えるためのリスト
// java.util.HashMap       … 分野ごとの点数を入れる
// java.util.List          … リストの型
// java.util.Map           … 「分野 → 点数」の組
// java.util.Random        … 同じ組み合わせが続かないようにする
//
// 以下は Spring の部品。
// Component … Spring に管理させるクラスです、という印

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.stereotype.Component;

/**
 * 「いま何を出すか」を決める。
 *
 * 決めるのは2つ。
 * 　1. 時間帯とその一言（朝・昼・夜・深夜）
 * 　2. 1件目と2件目に出す分野、2件目の目安時間
 *
 * 【なぜアプリが分野を決めるのか】
 *
 * 時間帯によって、動ける量は変わる。
 * 朝はこれから1日が始まるので、軽いもので勢いがつく。
 * 夜は疲れているので、重いものを出しても続かない。
 * 深夜に30分の作業を勧めれば、翌日に響く。
 *
 * これは誰にとっても大きくは変わらないので、AIに任せず、こちらで決める。
 * AIに渡すのは「その枠の中で何をやるか」だけにする。
 *
 * 分野を先に決めてしまえば、「3案は違う話題に」と頼む必要がなくなる。
 * 1位と2位を使うので、必ず違う分野になる。
 *
 * 【点数の付け方】
 *
 * 基本は3分野とも 1.0。そこに足したり引いたりして、上位2つを取る。
 * 数字は最初の当て推量なので、偏るようなら直す。
 *
 * 【ゆらぎ】
 *
 * 点数だけで決めると、同じ時間帯では毎回同じ組み合わせになる。
 * 0〜0.4 の幅でゆらぎを足して、僅差のときだけ入れ替わるようにしている。
 * 0.5 以上の差は覆らないので、時間帯や主な用途の意図は残る。
 *
 * 外部とのやりとりもデータベースへの保存も行わない。
 */
@Component
public class SituationJudge {

	// ===== フィールド =====

	/** 分野は3つ。提案カードの category にもこの言葉を使う */
	private static final List<String> CATEGORIES =
			List.of("べんきょう", "せいかつ", "リラックス");

	/** ゆらぎの幅。僅差のときだけ順位が入れ替わる大きさにしてある */
	private static final double JITTER = 0.4;

	/**
	 * 「別の話題にする」で外された分野に付ける点数。
	 *
	 * 主な用途の +1.5 より大きくしてある。
	 * 押したときは必ず分野が変わるようにするため。
	 */
	private static final double AVOID = -2.0;

	/**
	 * ゆらぎ用のくじ引き。
	 * 呼ぶたびに new すると、短い間に呼ばれたとき同じ値が続くことがある。
	 */
	private final Random random = new Random();

	// ===== メソッド =====

	/**
	 * いまの状況と、出す分野を決める。
	 *
	 * 引数で日時を受け取り、中で LocalDateTime.now() を呼んでいない。
	 * 中で現在時刻を取ると、テストのときに時間帯を指定できなくなる。
	 *
	 * @param now           いまの日時
	 * @param mainUse       主な用途。べんきょう / せいかつ / リラックス。未回答なら null
	 * @param focusLevel    1回の集中時間。SHORT / MIDDLE / LONG。未回答なら null
	 * @param avoidCategory 今回は外したい分野。「別の話題にする」のとき前回の1件目が入る
	 * @return 判断した状況
	 */
	public Situation judge(LocalDateTime now, String mainUse, String focusLevel,
			String avoidCategory) {

		String timeOfDay = timeOfDay(now.getHour());

		List<String> ranked = ranked(timeOfDay, now.getDayOfWeek(), mainUse, avoidCategory);

		return new Situation(
				timeOfDay,
				noteFor(timeOfDay),
				ranked.get(0),
				ranked.get(1),
				minutesFor(focusLevel));
	}

	// ===== メソッド（内部で使うもの） =====

	/**
	 * 時刻から時間帯の名前を決める。
	 *
	 *  5時〜11時 … 朝
	 * 11時〜17時 … 昼
	 * 17時〜22時 … 夜
	 * それ以外    … 深夜
	 *
	 * @param hour 時（0〜23）
	 * @return 時間帯の名前
	 */
	private String timeOfDay(int hour) {

		if (hour >= 5 && hour < 11) {
			return "朝";
		}
		if (hour >= 11 && hour < 17) {
			return "昼";
		}
		if (hour >= 17 && hour < 22) {
			return "夜";
		}
		return "深夜";
	}

	/**
	 * 時間帯ごとの一言を返す。
	 *
	 * 同じ軽さでも、渡す一言が違えばAIの提案は変わる。
	 *
	 * @param timeOfDay 時間帯の名前
	 * @return AIに伝える一言
	 */
	private String noteFor(String timeOfDay) {

		return switch (timeOfDay) {
			case "朝" -> "朝なので、短時間で終わって勢いがつくものがよい";
			case "昼" -> "日中なので、今日中に終えられる程度のものがよい";
			case "夜" -> "夜なので、疲れていても手が動く軽いものがよい";
			default   -> "深夜なので、明日に響かないものだけにする";
		};
	}

	/**
	 * 分野に点数を付けて、高い順に並べる。
	 *
	 * @param timeOfDay     時間帯の名前
	 * @param dayOfWeek     曜日
	 * @param mainUse       主な用途。未回答なら null
	 * @param avoidCategory 外したい分野。無ければ null
	 * @return 点数の高い順に並んだ分野
	 */
	private List<String> ranked(String timeOfDay, DayOfWeek dayOfWeek,
			String mainUse, String avoidCategory) {

		Map<String, Double> score = new HashMap<>();

		// 基本は横並び
		for (String category : CATEGORIES) {
			score.put(category, 1.0);
		}

		// 主な用途。いちばん大きく効かせる
		if (mainUse != null && score.containsKey(mainUse)) {
			score.merge(mainUse, 1.5, Double::sum);
		}

		// 時間帯。深夜のべんきょうは下げて、出にくくする
		switch (timeOfDay) {
			case "朝" -> {
				score.merge("せいかつ", 1.0, Double::sum);
				score.merge("リラックス", 0.5, Double::sum);
			}
			case "昼" -> score.merge("べんきょう", 1.0, Double::sum);
			case "夜" -> {
				score.merge("リラックス", 1.0, Double::sum);
				score.merge("べんきょう", 0.5, Double::sum);
			}
			default -> {
				score.merge("リラックス", 1.5, Double::sum);
				score.merge("べんきょう", -1.0, Double::sum);
			}
		}

		// 土日。平日と同じ並びにならないようにする
		if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
			score.merge("リラックス", 1.0, Double::sum);
		}

		// 「別の話題にする」で外された分野。
		// 外すのは1つだけ。分野は3つしかないので、2つ外すと選ぶ余地が消える
		if (avoidCategory != null && score.containsKey(avoidCategory)) {
			score.merge(avoidCategory, AVOID, Double::sum);
		}

		// ゆらぎ。僅差のときだけ入れ替わる
		for (String category : CATEGORIES) {
			score.merge(category, random.nextDouble() * JITTER, Double::sum);
		}

		List<String> ranked = new ArrayList<>(CATEGORIES);

		// 点数の高い順。b と a の順に比べると降順になる
		ranked.sort((a, b) -> Double.compare(score.get(b), score.get(a)));

		return ranked;
	}

	/**
	 * 集中時間の答えから、2件目の目安時間を決める。
	 *
	 * 未回答のときは真ん中にしておく。
	 *
	 * @param focusLevel SHORT / MIDDLE / LONG。未回答なら null
	 * @return 「20〜60分」のような文字列
	 */
	private String minutesFor(String focusLevel) {

		if ("SHORT".equals(focusLevel)) {
			return "10〜20分";
		}
		if ("LONG".equals(focusLevel)) {
			return "60〜120分";
		}
		return "20〜60分";
	}
}