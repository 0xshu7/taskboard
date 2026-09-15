package com.example.taskboard.ai;

// ===== インポート =====
// java.util.List … リストの型。3枚まとめて返すのに使う
//
// 以下はこのアプリの部品。
// ProposalCard … 提案カード1枚

import java.util.List;

import com.example.taskboard.domain.ProposalCard;

/**
 * 提案カードを作る役目。
 *
 * 呼び出す側（ProposalService）は、このインターフェースだけを見る。
 * 中身が固定の3案なのか、実際に AI を呼ぶのかは知らないまま使える。
 *
 * 中身は application.yml の ai.mock で入れ替わる。
 * true なら MockProposalGenerator、false なら RealProposalGenerator が渡される。
 * 呼び出す側のコードは1文字も変わらない。
 *
 * この形にしたことで、AI をつなぐ前に
 * 画面・保存・タスク化・実績まで作りきることができた。
 * 本物に切り替えたとき、確かめることが「3案がちゃんと返るか」の1つだけになる。
 *
 * MailSender と同じ作りになっている。
 * 「外の世界とやりとりするところは、インターフェースを挟んで差し替えられるようにする」
 * は、このアプリで繰り返し使っている形。
 *
 * 別の AI サービスに乗り換えたくなったときも、
 * このインターフェースを実装したクラスを1つ足すだけで済む。
 */
public interface ProposalGenerator {

	/**
	 * プロンプトを渡すと、カードを3枚返す。
	 *
	 * 返ってくるカードは、まだデータベースに保存されていない状態。
	 * position と proposalSetId も入っていない。
	 * それらは ProposalService が保存するときに設定する。
	 *
	 * 作ることと保存することを分けてあるので、
	 * このインターフェースの実装はデータベースを知らなくてよい。
	 *
	 * 失敗したときは例外を投げる。
	 * 空のリストを返す形にすると、呼び出す側が
	 * 「失敗したのか、本当に0件だったのか」を区別できない。
	 *
	 * @param prompt AI に送る文章。モックでは使われない
	 * @return 3枚のカード
	 */
	List<ProposalCard> generate(String prompt);

	/**
	 * この実装が何者かを表す文字を返す。
	 *
	 * ProposalSet.source に記録され、
	 * あとから「どの経路で作られた3案か」を数えられる。
	 *
	 * MOCK     … 開発用の固定3案
	 * AI       … 実際に AI が返した3案
	 * FALLBACK … AI に失敗して固定3案に切り替えた
	 *
	 * FALLBACK はこのインターフェースの実装ではなく、
	 * ProposalService が失敗を受け止めたときに直接書き込んでいる。
	 *
	 * @return 実装を表す文字
	 */
	String sourceName();
}