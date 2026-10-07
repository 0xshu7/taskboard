package com.example.taskboard.logic;

// ===== インポート =====
// java.util.List … リストの型

import java.util.List;

/**
 * 分野の名前を1か所にまとめる。
 *
 * このアプリの分野は3つ。
 * 　べんきょう / せいかつ / リラックス
 *
 * 【なぜ列挙型（enum）にしていないか】
 *
 * 分野は proposal_cards.category に文字列で保存している。
 * 列挙型にすれば打ち間違いをコンパイル時に見つけられるが、
 * そのぶんデータベースの列と Java の型を結び直す仕組みが要る。
 *
 * 分野は3つしかなく、増やしたり言い換えたりする可能性のあるもの。
 * 列挙型にすると、言い換えるたびに列の中身と型の両方を触ることになる。
 * 文字列のままにしておけば、ここを直すだけで済む。
 *
 * 打ち間違いへの備えは別に用意してある。
 * 想定外の分野が入らないようにしているのは2段。
 * AI には responseSchema の enum でこの一覧しか返させず、
 * 1件目と2件目は ProposalService.numberCards がアプリの決めた分野を入れ直す。
 * 固定3案の側は FallbackCards.normalize がリラックスに寄せ、ログに残す。
 *
 * 【定数にした理由】
 *
 * 以前は SituationJudge と FallbackCards がそれぞれ同じ一覧を持ち、
 * PromptBuilder の switch にも同じ文字が直に書かれていた。
 * 言い換えようとすると3か所を探すことになり、
 * どれか1つを直し忘れても、動かしてみるまで分からない。
 *
 * 定数は switch の case にもそのまま書ける。
 * final で、中身が文字そのものなら、コンパイル時に値が決まるため。
 */
public final class Categories {

	// ===== 定数 =====

	/** べんきょう */
	public static final String STUDY = "べんきょう";

	/** せいかつ */
	public static final String LIFE = "せいかつ";

	/** リラックス */
	public static final String RELAX = "リラックス";

	/**
	 * 3つの分野。点数を付けるときと、残りの分野を求めるときに使う。
	 *
	 * List.of で作ったものは中身を変えられない。
	 * 共有しても、どこかで足されたり消されたりすることがない。
	 *
	 * なお、この一覧は contains(null) を呼ぶと例外を投げる。
	 * null が来うる場所では、先に null を弾いておくこと。
	 */
	public static final List<String> ALL = List.of(STUDY, LIFE, RELAX);

	// ===== コンストラクタ =====

	/**
	 * 外から作れないようにする。
	 *
	 * このクラスは定数を置くためだけにある。new しても意味がない。
	 */
	private Categories() {
	}
}