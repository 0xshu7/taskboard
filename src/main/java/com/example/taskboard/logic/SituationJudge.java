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
 * 　主な用途   +1.5
 * 　時間帯     朝  せいかつ +1.0 / リラックス +0.5
 * 　　　　　　 昼  べんきょう +1.0
 * 　　　　　　 夜  リラックス +1.0 / べんきょう +0.5
 * 　　　　　　 深夜 リラックス +1.5 / べんきょう -1.0
 * 　土日       リラックス +1.0
 * 　完了率     0.0〜1.0（記録から。下記）
 * 　ゆらぎ     0〜0.4
 *
 * 「別の話題にする」で外された分野は、点数ではなく並びから取り除く（下記）。
 *
 * 【完了率（記録からの重み）】
 *
 * 分野ごとに「出した数」と「実際に終わった数」を数え、
 * (終わった数 + 1) / (出した数 + 3) を点数に足している。
 * 計算そのものは ProposalService が行い、ここは受け取って足すだけ。
 *
 * 終わらせている分野が出やすくなる。
 * 選ばれた数ではなく終わった数を見るのは、選択だけを報酬にすると
 * 選びやすいだけの提案に寄ってしまうため。
 *
 * これは時間帯より弱く効く。
 * 完了率の差は最大でも 0.86 程度で、時間帯の 1.0〜1.5 を覆さない。
 * そのおかげで「合わない分野の出番は減るが、その分野が向いている
 * 時間帯には出る」という形に収まり、分野が完全に消えることはない。
 *
 * 記録が1件も無い分野は 1/3（0.33）として扱う。
 * 3分野とも同じ値になるので、使い始めは差が出ない。
 *
 * 【「必ず」は重みで表さない】
 *
 * 「別の話題にする」で外す分野は、以前は -2.0 の重みで下げていた。
 * これはテストで不具合が見つかった。
 *
 * 　昼・主な用途が べんきょう の人が押した場合
 * 　　1.0 + 1.5（主な用途）+ 1.0（昼）- 2.0 = 1.5
 * 　　他の分野は 1.0 なので、外したはずの分野が1位のまま残る。
 *
 * 定数を大きくしても直らない。
 * 深夜・土日・完了率が重なると 6.0 近くまで上がるので、
 * 必要な値が状況によって変わってしまう。
 *
 * 必ず守りたいことは、重みではなく除外で表す。
 * 分野は3つなので、1つ外しても2つ残り、1件目と2件目は埋まる。
 *
 * 【ゆらぎ】
 *
 * 点数だけで決めると、同じ時間帯では毎回同じ組み合わせになる。
 * 0〜0.4 の幅でゆらぎを足して、僅差のときだけ入れ替わるようにしている。
 * 0.5 以上の差は覆らないので、時間帯や主な用途の意図は残る。
 *
 * ゆらぎは「ときどき別の分野も試す」役も兼ねている。
 * 完了率が低い分野も、下駄と合わせて点数が 0 にならないので、
 * 見捨てられて二度と出なくなることがない。
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
	 * 記録が1件も無い分野の完了率。
	 *
	 * (0 + 1) / (0 + 3) と同じ値にしてある。
	 * 数えた分野と数えていない分野を、同じ計算の上に並べるため。
	 * 3分野とも記録が無ければ全部これになるので、差は出ない。
	 */
	private static final double NO_RECORD = 1.0 / 3.0;

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
	 * 完了率も引数で受け取る。
	 * ここでデータベースを見に行くと、このクラスが記録の形を知ることになり、
	 * テストでも数字を指定できなくなる。
	 * 「数えるのは ProposalService、使うのはここ」と分けている。
	 *
	 * @param now           いまの日時
	 * @param mainUse       主な用途。べんきょう / せいかつ / リラックス。未回答なら null
	 * @param focusLevel    1回の集中時間。SHORT / MIDDLE / LONG。未回答なら null
	 * @param avoidCategory 今回は外したい分野。「別の話題にする」のとき前回の1件目が入る
	 * @param bonus         分野ごとの完了率。記録が無い分野は入っていなくてよい
	 * @return 判断した状況
	 */
	public Situation judge(LocalDateTime now, String mainUse, String focusLevel,
			String avoidCategory, Map<String, Double> bonus) {

		String timeOfDay = timeOfDay(now.getHour());

		List<String> ranked = ranked(timeOfDay, now.getDayOfWeek(), mainUse, avoidCategory, bonus);

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
	 * 外したい分野があるときは、並べたあとに取り除く。
	 * そのため、返ってくるのは3つのときと2つのときがある。
	 * どちらでも1件目と2件目は取れる。
	 *
	 * @param timeOfDay     時間帯の名前
	 * @param dayOfWeek     曜日
	 * @param mainUse       主な用途。未回答なら null
	 * @param avoidCategory 外したい分野。無ければ null
	 * @param bonus         分野ごとの完了率
	 * @return 点数の高い順に並んだ分野
	 */
	private List<String> ranked(String timeOfDay, DayOfWeek dayOfWeek,
			String mainUse, String avoidCategory, Map<String, Double> bonus) {

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

		// 完了率。終わらせている分野を出やすくする。
		// 記録が無い分野は 0.33 で並ぶので、使い始めは差にならない。
		// 最大でも 1.0 なので、時間帯の重みを覆さない
		for (String category : CATEGORIES) {
			score.merge(category, bonus.getOrDefault(category, NO_RECORD), Double::sum);
		}

		// ゆらぎ。僅差のときだけ入れ替わる。
		// 完了率の低い分野も点数が 0 にならないので、見捨てられずに時々出る
		for (String category : CATEGORIES) {
			score.merge(category, random.nextDouble() * JITTER, Double::sum);
		}

		List<String> ranked = new ArrayList<>(CATEGORIES);

		// 点数の高い順。b と a の順に比べると降順になる
		ranked.sort((a, b) -> Double.compare(score.get(b), score.get(a)));

		// 「別の話題にする」で外された分野は、並びそのものから取り除く。
		//
		// 以前は -2.0 の重みで下げていたが、それでは足りなかった。
		// 昼に主な用途が べんきょう の人だと
		// 　1.0 + 1.5（主な用途）+ 1.0（昼）- 2.0 = 1.5
		// となり、1.0 の他の分野より高いまま1位に残る。テストで見つかった。
		//
		// 定数を大きくしても直らない。深夜・土日・完了率が重なると
		// 6.0 近くまで上がるので、必要な値が状況によって変わる。
		//
		// 必ず守りたいことは、重みではなく除外で表す。
		// 外すのは1つだけ。分野は3つなので、2つ残って1件目と2件目は埋まる。
		if (avoidCategory != null) {
			ranked.remove(avoidCategory);
		}

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