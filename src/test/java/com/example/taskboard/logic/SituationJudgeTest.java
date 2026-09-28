package com.example.taskboard.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SituationJudge のテスト。
 *
 * Spring は起動しない。new して呼ぶだけなので一瞬で終わる。
 * データベースにもネットワークにもつながらない。
 *
 * 【ゆらぎがあるものをどう試すか】
 *
 * judge は 0〜0.4 の乱数を足すので、毎回同じ答えにはならない。
 * だから「必ずこの分野になる」とは書けない。
 *
 * 代わりに、ゆらぎでは覆らない大きさの差だけを確かめている。
 * 0.5 以上の差は乱数で覆らないので、そこは必ず成り立つ。
 *
 * 何度も呼んでいるのは、たまたま通ったのではないことを確かめるため。
 */
class SituationJudgeTest {

	/** 試す相手 */
	private final SituationJudge judge = new SituationJudge();

	/** 記録がまだ無い状態。3分野とも同じ扱いになる */
	private static final Map<String, Double> NO_BONUS = Map.of();

	/** 何度くり返すか。乱数が絡むので1回では足りない */
	private static final int TIMES = 100;

	/**
	 * 指定した時刻で判断させる。
	 * 2026-09-28 は月曜なので、土日の重みは入らない。
	 */
	private Situation judgeAt(int hour, String mainUse, String avoid) {
		return judge.judge(LocalDateTime.of(2026, 9, 28, hour, 0),
				mainUse, null, avoid, NO_BONUS);
	}

	@Test
	@DisplayName("1件目と2件目は、必ず違う分野になる")
	void 一件目と二件目は違う分野() {

		for (int i = 0; i < TIMES; i++) {
			Situation situation = judgeAt(13, "べんきょう", null);

			assertNotEquals(
					situation.getFirstCategory(),
					situation.getSecondCategory(),
					"1件目と2件目が同じ分野になった");
		}
	}

	@Test
	@DisplayName("深夜は、べんきょうを1件目に出さない")
	void 深夜にべんきょうを出さない() {

		// 深夜の べんきょう は -1.0。
		// 主な用途が べんきょう（+1.5）でも、リラックス（+1.5）に届かない。
		// 差は 1.0 あるので、ゆらぎ 0.4 では覆らない。
		for (int i = 0; i < TIMES; i++) {
			Situation situation = judgeAt(2, "べんきょう", null);

			assertNotEquals("べんきょう", situation.getFirstCategory(),
					"深夜に、べんきょうが1件目に来た");
		}
	}

	@Test
	@DisplayName("別の話題にすると、その分野は1件目に来ない")
	void 別の話題で分野が変わる() {

		// 外された分野は -2.0。主な用途の +1.5 より大きくしてあるので、必ず下がる。
		for (int i = 0; i < TIMES; i++) {
			Situation situation = judgeAt(13, "べんきょう", "べんきょう");

			assertNotEquals("べんきょう", situation.getFirstCategory(),
					"外した分野が、また1件目に来た");
		}
	}

	@Test
	@DisplayName("完了率の高い分野は、出やすくなる")
	void 完了率が効く() {

		// 昼・主な用途はべんきょう。
		//   べんきょう 1.0 + 1.5 + 1.0（昼）＝ 3.5
		//   せいかつ   1.0
		//   リラックス 1.0 + 1.0（完了率）＝ 2.0
		// リラックスが せいかつ より 1.0 高いので、2件目は必ずリラックスになる。
		Map<String, Double> bonus = Map.of("リラックス", 1.0);

		for (int i = 0; i < TIMES; i++) {
			Situation situation = judge.judge(
					LocalDateTime.of(2026, 9, 28, 13, 0),
					"べんきょう", null, null, bonus);

			assertEquals("リラックス", situation.getSecondCategory(),
					"完了率を足したのに、2件目に来なかった");
		}
	}

	@Test
	@DisplayName("集中時間の答えで、2件目の目安時間が変わる")
	void 集中時間で分数が変わる() {

		LocalDateTime now = LocalDateTime.of(2026, 9, 28, 13, 0);

		assertEquals("10〜20分",
				judge.judge(now, null, "SHORT", null, NO_BONUS).getSecondMinutes());

		assertEquals("60〜120分",
				judge.judge(now, null, "LONG", null, NO_BONUS).getSecondMinutes());

		// 未回答のときは真ん中
		assertEquals("20〜60分",
				judge.judge(now, null, null, null, NO_BONUS).getSecondMinutes());
	}

	@Test
	@DisplayName("時刻から時間帯が決まる")
	void 時間帯の境目() {

		assertEquals("朝",   judgeAt(5, null, null).getTimeOfDay());
		assertEquals("朝",   judgeAt(10, null, null).getTimeOfDay());
		assertEquals("昼",   judgeAt(11, null, null).getTimeOfDay());
		assertEquals("昼",   judgeAt(16, null, null).getTimeOfDay());
		assertEquals("夜",   judgeAt(17, null, null).getTimeOfDay());
		assertEquals("夜",   judgeAt(21, null, null).getTimeOfDay());
		assertEquals("深夜", judgeAt(22, null, null).getTimeOfDay());
		assertEquals("深夜", judgeAt(4, null, null).getTimeOfDay());
	}
}