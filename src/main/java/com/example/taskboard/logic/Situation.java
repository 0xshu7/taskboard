package com.example.taskboard.logic;

// ===== インポート =====
// 以下はこのアプリの部品。
// Granularity … タスクの粒度（5分でできる / 今日中 / まとまった時間）

import com.example.taskboard.domain.Granularity;

/**
 * 「いまどんな状況か」をまとめて持つ入れ物。
 *
 * SituationJudge が作り、PromptBuilder が読む。
 * 作る人と読む人が1つずつに決まっている。
 *
 * このクラスは dto には置かない。
 * dto は画面やAPIとの境界を越える入れ物で、外の都合に合わせて型が歪む。
 * こちらはサーバの中だけで完結し、Granularity をそのまま持てる。
 * 判断した結果を持ち運ぶだけなので、判断する側と同じ場所に置いている。
 *
 * すべてのフィールドが final で、setter を持たない。
 * 作ったあと書き換えられないので、渡したあとで値が変わる心配がない。
 * 判断の結果は、あとから誰かが書き換えてよいものではない。
 */
public class Situation {

	// ===== フィールド =====

	/** 朝 / 昼 / 夜 / 深夜。プロンプトにそのまま書き込む */
	private final String timeOfDay;

	/** この時間帯に勧めたい重さ */
	private final Granularity recommended;

	/** AIに伝える一言。「夜なので軽いものを」など */
	private final String note;

	// ===== コンストラクタ =====

	/**
	 * 3つの値をまとめて受け取る。
	 *
	 * setter が無いので、作るときに全部渡す必要がある。
	 * 途中まで埋まった中途半端な状態が存在しない。
	 *
	 * @param timeOfDay   時間帯の呼び名
	 * @param recommended 勧めたい重さ
	 * @param note        AIに伝える一言
	 */
	public Situation(String timeOfDay, Granularity recommended, String note) {
		this.timeOfDay = timeOfDay;
		this.recommended = recommended;
		this.note = note;
	}

	// ===== メソッド =====
	// 読み出すだけ。setter は用意しない。

	/** 時間帯の呼び名を返す */
	public String getTimeOfDay() {
		return timeOfDay;
	}

	/** 勧めたい重さを返す */
	public Granularity getRecommended() {
		return recommended;
	}

	/** AIに伝える一言を返す */
	public String getNote() {
		return note;
	}
}