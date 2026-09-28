package com.example.taskboard.logic;

/**
 * 「いまどんな状況か」と「何を出すか」をまとめて持つ入れ物。
 *
 * SituationJudge が作り、PromptBuilder が読む。
 * 作る人と読む人が1つずつに決まっている。
 *
 * 【分野を持つようになった理由】
 *
 * 以前は「3案は違う話題にしてください」とプロンプトでお願いしていたが、
 * 守られないことがあった。3案とも同じ話題になる。
 *
 * どの分野を出すかをアプリが決めてしまえば、守る／守らないの問題が消える。
 * AI が決めるのは中身と言葉だけになる。
 * 設計書 3.2.1「状況判断」の手順4にあたる。
 *
 * 【Granularity を外した】
 *
 * 以前は勧めたい重さ（FIVE_MIN など）も持っていたが、誰も読んでいなかった。
 * 分数を直接持つようにしたので、役目も無くなった。
 *
 * すべてのフィールドが final で、setter を持たない。
 * 判断の結果は、あとから誰かが書き換えてよいものではない。
 */
public class Situation {

	// ===== フィールド =====

	/** 朝 / 昼 / 夜 / 深夜。プロンプトにそのまま書き込む */
	private final String timeOfDay;

	/** AIに伝える一言。「夜なので軽いものを」など */
	private final String note;

	/** 1件目に出す分野。べんきょう / せいかつ / リラックス */
	private final String firstCategory;

	/** 2件目に出す分野。1件目とは必ず違う */
	private final String secondCategory;

	/** 2件目の目安時間。「20〜60分」のように、そのまま書ける形で持つ */
	private final String secondMinutes;

	// ===== コンストラクタ =====

	/**
	 * 判断の結果をまとめて受け取る。
	 *
	 * setter が無いので、作るときに全部渡す必要がある。
	 * 途中まで埋まった中途半端な状態が存在しない。
	 *
	 * @param timeOfDay      時間帯の呼び名
	 * @param note           AIに伝える一言
	 * @param firstCategory  1件目の分野
	 * @param secondCategory 2件目の分野
	 * @param secondMinutes  2件目の目安時間
	 */
	public Situation(String timeOfDay, String note,
			String firstCategory, String secondCategory, String secondMinutes) {

		this.timeOfDay = timeOfDay;
		this.note = note;
		this.firstCategory = firstCategory;
		this.secondCategory = secondCategory;
		this.secondMinutes = secondMinutes;
	}

	// ===== メソッド =====
	// 読み出すだけ。setter は用意しない。

	/** 時間帯の呼び名を返す */
	public String getTimeOfDay() {
		return timeOfDay;
	}

	/** AIに伝える一言を返す */
	public String getNote() {
		return note;
	}

	/** 1件目に出す分野を返す */
	public String getFirstCategory() {
		return firstCategory;
	}

	/** 2件目に出す分野を返す */
	public String getSecondCategory() {
		return secondCategory;
	}

	/** 2件目の目安時間を返す */
	public String getSecondMinutes() {
		return secondMinutes;
	}
}