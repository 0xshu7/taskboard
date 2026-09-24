package com.example.taskboard.ai;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.taskboard.domain.Task;
import com.example.taskboard.logic.Situation;

/**
 * AI に送る文章を組み立てる。
 *
 * 【この版について】
 *
 * 別に作ったモック（Cloudflare Workers AI）のプロンプトに、できる限り合わせている。
 * 見出しの名前、プロフィールの形、ルールの順番と書き方、手順の形、
 * 出力例の載せ方まで、変える理由が無いところは、そのまま同じにしている。
 *
 * 【v3.2 で戻したこと】
 *
 * v3.1 のルール1に「未完了の一覧にあるタスクから切り出して作る」と書いていた。
 * これは間違いだった。WorkLog（Day 5）に、こう決めてある。
 *
 * 　・禁止リスト（散歩・ゴミ捨てを禁じる）は作らない
 * 　・ゴミ捨ては成功なので、禁じると害になる
 * 　・このアプリの目的は、止まっている人が動き出すこと。
 * 　　動けたなら、それがゴミ捨てでも成功になる
 *
 * 「一覧から切り出せ」は、一覧に無いこと（コーヒーを淹れる、外を歩く）を
 * まとめて禁止する。名前は違うが、作らないと決めた禁止リストそのものだった。
 *
 * 3案が3件とも勉強に寄ったのは、この指示が原因。
 * 一覧が勉強中心のとき、1件目も2件目も一覧から作れば、3件とも勉強になる。
 *
 * 【タスクの定義も戻した】
 *
 * 3条件の3つ目を「この人がかかえていることが目に見えて前に進む」と書いていたが、
 * 記録では「終わったあとに何かが残る」。版を重ねるうちに条件が強くなっていた。
 *
 * 　WorkLog（Day 5）　　終わったあとに何かが残る
 * 　発表資料 v1　　　　 やると、何かが前に進む
 * 　PromptBuilder v1.6　やると、かかえていることが目に見えて前に進む
 *
 * 最後の形は、一覧に紐づかないものを全部落とす。これも上と同じ効果を持っていた。
 * 記録の文面に戻してある。
 *
 * 【さんたく側の事情で、モックに合わせられなかったもの】
 *
 * 1. 3件の内訳
 *    モックは「既存2＋新規1」で、どれを選ぶかを AI が決める。
 *    さんたくは「新規2＋既存1」で、既存の1件はアプリがくじ引きで決めてから渡す。
 *    仕様そのものなので変えていない。
 *
 * 2. kind / task_id のフィールド
 *    モックは AI に「これは既存/新規」を申告させるが、
 *    さんたくは3件目が既存だとアプリ側で決まっているので、申告してもらう必要が無い。
 *
 * 3. 最近完了したタスク・除外リスト
 *    モックはこの2つを材料に入れている。
 *    さんたくは buildProposal がそれを受け取っていないので、いまは渡せない。
 *    プロンプトだけでは足せない（引数と取得処理が要る）。
 *    この穴は埋めていない。埋めようとして書いたのが v3.1 のルール1で、
 *    別の材料で代用しようとした結果、上に書いた不具合になった。
 *    必要なら、材料そのものを渡せるようにする（引数を足す）のが筋。
 *
 * 4. detail の250字制限
 *    モックには無い。さんたくは列の上限がこれなので外せない。
 *
 * 5. estimatedMinutes / category / granularity / difficulty
 *    さんたくにしか無い項目。モックには対応するものが無いので、そのまま残している。
 *
 * 6. プロフィールが固定であること
 *    モックは初回インタビューの回答が入る。
 *    さんたくは同じ仕組み（F-02）がまだ無いので、下の3行はコードに書いた固定値。
 */
@Component
public class PromptBuilder {

	/** プロンプトの版。直したら必ず上げる */
	private static final String VERSION = "v3.2";

	/** 日付の書き方。モックの「2026-09-24 14時ごろ」に合わせている */
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/**
	 * 本人の情報。初回ヒアリング（F-02）を作るまでの仮置き。
	 *
	 * モックの3項目と同じ形にしてある。選べる値もモックに合わせた。
	 * 　主な用途　　　　：しごと / べんきょう / くらし・プライベート
	 * 　好きなすすめ方　：かんたんなものから / 締切がちかいものから / だいじなものから
	 * 　1回の集中時間　 ：15分くらい / 30分〜1時間 / がっつり長時間
	 *
	 * ここに「自炊」「休日」のような話題を書き足さないこと。
	 * 書いた瞬間、AI はタスク一覧を見ずにそこから中身を作りはじめる。
	 * 「キャベツを一枚ちぎって洗う」「米を洗う」は、前の版の「自炊」の行から出ていた。
	 */
	private static final String PROFILE = """
			- 主な用途: べんきょう
			- 好きなすすめ方: かんたんなものから
			- 1回の集中時間: 30分〜1時間
			""";

	/**
	 * 3案を出してもらうための文章を組み立てる。
	 *
	 * 組み立ての順番は、プロンプト設計書 1.1 に合わせている。
	 * 　役割 → 材料 → 指示 → 出力ルール
	 * Gemini は後ろに書いた指示ほど守るので、出力ルールを最後に置く。
	 *
	 * @param situation   いまの状況。SituationJudge が時刻から判断したもの
	 * @param activeTasks いま未完了のタスク。提案の材料になる
	 * @param pickedTask  3件目にそのまま使うタスク。1件も無ければ null
	 * @return AI に送る文章
	 */
	public String buildProposal(Situation situation, List<Task> activeTasks, Task pickedTask) {

		LocalDateTime now = LocalDateTime.now();
		StringBuilder p = new StringBuilder();

		// --- 役割 ---

		p.append("# prompt ").append(VERSION).append("\n\n");

		p.append("あなたは日本語のタスク管理アプリのやさしいコーチです。");
		p.append("ユーザーが「今やるべきタスク」を3件、JSONで提案してください。\n\n");

		// --- 材料 ---

		p.append("## ユーザーのプロフィール\n");
		p.append(PROFILE).append("\n");

		p.append("## いま(日本時間)\n");
		p.append(now.format(DATE_FORMAT)).append(" ").append(now.getHour()).append("時ごろ\n");
		p.append("時間帯: ").append(situation.getTimeOfDay()).append("\n");
		p.append("方針: ").append(situation.getNote()).append("\n\n");

		p.append("## 未完了のタスク一覧\n");
		if (activeTasks.isEmpty()) {
			p.append("(未完了のタスクはありません)\n\n");
		} else {
			for (Task task : activeTasks) {
				p.append("- ").append(task.getTitle());
				p.append(" | 締切:").append(task.getDueDate() != null ? task.getDueDate() : "なし");
				p.append("\n");
			}
			p.append("\n");
		}

		p.append("## 3件目に使うタスク(アプリが決定ずみ)\n");
		p.append(pickedTask != null ? pickedTask.getTitle() : "(なし)").append("\n\n");

		// --- 指示 ---
		//
		// 定義をルールより先に置いている。
		// 「タスクとは何か」が決まっていないと、重さや形を指定しても意味がないため。
		//
		// 3つ目の条件は「終わったあとに何かが残る」。
		// WorkLog（Day 5）の文面そのまま。これ以上強くしない。
		// 強くすると、一覧に紐づかないものが全部落ちて、3案が同じ話題に寄る。
		//
		// 「買いに行く・移動する・待つ」は、独立した禁止ではなく条件2の中に置いた。
		// 定義から出てくるものなら、禁止リストを増やしたことにならない。

		p.append("## このアプリでの「タスク」の定義\n");
		p.append("次の3つを全部満たすものだけを、タスクと呼びます。\n");
		p.append("1. 書いてあることだけをやれば「終わった」と言い切れる。\n");
		p.append("　 どこまでやれば終わりかが、文に書いてある。\n");
		p.append("2. 今いる場所で、何も決めず、何も探さずに始められる。\n");
		p.append("　 買いに行く・どこかへ移動する・誰かの返事を待つ、が必要なものは出さない。\n");
		p.append("3. 終わったあとに、何かが残る。\n\n");

		p.append("## ルール\n");
		p.append("1. 1件目は estimatedMinutes 3〜10分の「新しい提案」。");
		p.append("今日はもう動けないと思っている人でも手が出るもの。");
		p.append("未完了の一覧に無いことでよい。\n");
		p.append("2. 2件目は estimatedMinutes 20〜60分の「気分がかわる新しい提案」。");
		p.append("1件目とも3件目とも違う話題にする。");
		p.append("いまの時間帯に合う、そのユーザーらしい具体的な内容にする。");
		p.append("ありきたりにしない。未完了の一覧に無いことでよい。\n");
		p.append("3. 3件目は上の「3件目に使うタスク」。title は一字も変えずに使い、detail だけ書く。");
		p.append("「(なし)」のときは3件目も新しい提案にする。\n");
		p.append("4. reasonは「なぜ今これか」を日本語20文字程度で、タスクの内容にふれて書く。\n");
		p.append("5. detailは**そのタスクの内容に合わせた具体的な手順**。");
		p.append("「✓」で始まる行を3行、最後に「おわりの目安: 〜」の行（この行に✓は付けない）。");
		p.append("改行は\\n。全体250字以内。");
		p.append("「まず手をつける」「小さく分ける」のような一般論だけの手順は禁止。");
		p.append("例えば「クリーニング」なら「✓出す服をまとめて袋に入れる」");
		p.append("「✓営業時間をしらべる」のように書く。\n");
		p.append("6. titleは、やることがそのまま分かる短い一文(30字以内)。");
		p.append("「整える」「見直す」「がんばる」のような、どこで終わりか分からない言葉は使わない。\n");
		p.append("7. 3件は話題を別々にする。");
		p.append("category のラベルだけ変えて、中身が同じ話題なのは違反。\n");
		p.append("8. 出力はJSONだけ。説明文・コードフェンス禁止。\n\n");

		// --- 出力ルール ---

		p.append("## 出力形式\n");
		p.append("category は 生活・勉強・食事・休日 のいずれか。");
		p.append("granularity は FIVE_MIN(10分以内)・TODAY(今日中)・GROUP(複数回)。");
		p.append("difficulty は LOW・MID・HIGH。\n");
		p.append("{\"cards\":[{\"title\":\"タスク名\",\"category\":\"勉強\",");
		p.append("\"granularity\":\"FIVE_MIN\",\"difficulty\":\"LOW\",\"estimatedMinutes\":5,");
		p.append("\"reason\":\"締切が近いから\",");
		p.append("\"detail\":\"✓手順1\\\\n✓手順2\\\\n✓手順3\\\\nおわりの目安: 〜になったら完了\"}]}\n");

		return p.toString();
	}
}