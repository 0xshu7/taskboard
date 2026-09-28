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
 * 【v5.1 について】
 *
 * 前置きの言葉も、アプリが決めるようにした版。
 *
 * v5.0 では、時間帯ごとの候補を3つ渡して AI に選ばせていた。
 * その結果こうなった。
 *
 * 　・1件目と2件目が、同じ「午後のリフレッシュに」で始まった
 * 　・「午後のリフレッシュに単語帳を5頁めくる」
 * 　　（リフレッシュと言いながら勉強している）
 *
 * v5.0 で「3案は違うタスクに」を消したとき、
 * 「3案それぞれ違う言葉を使う」も一緒に消えていた。削りすぎ。
 *
 * ただ、その指示を戻すより、アプリが割り当てるほうが確実。
 * 分野はもう決めているので、前置きも組で決めてしまえば、
 * 重複も、前置きと中身のずれも、起きようがない。
 *
 * 「午後のリフレッシュ」は リラックス 用、
 * 「午後の集中力アップ」は べんきょう 用、というふうに対応を固定した。
 *
 * 【v5.0 について】
 *
 * 分野をアプリが決めるようになった版。
 *
 * ■ 消したルール3つ
 *
 * 　・3案は必ず違うタスクにしてください
 * 　・2つ目は1つ目とも3つ目とも違う話題にする
 * 　・3案のうち1案は外出や運動など活動的な内容を提案する
 *
 * どれも「どの枠を出すか」の話で、文章の書き方ではなかった。
 * AI にお願いしても守られないことがあり、実際に3案とも同じ話題になった。
 *
 * SituationJudge が1件目と2件目の分野を決めるようにしたので、
 * 守る／守らないの問題が消えた。1位と2位なので、必ず違う分野になる。
 * 「外出や運動」も「リラックス」という分野として扱える。
 *
 * AI が決めるのは、その枠の中で何をやるかと、どう書くかだけになった。
 *
 * ■ 分野を3つにした
 *
 * 生活・勉強・食事・休日 → べんきょう・せいかつ・リラックス。
 *
 * 前の4つは軸が混ざっていた。休日は「いつやるか」であって内容ではない。
 * 時間の軸は Situation が別に持っているので、内容の軸だけにした。
 * 生活と食事は「身のまわり」として1つにまとめた。
 *
 * 3択のアプリなので、分野も3つ。最初の質問の3択とそのまま対応する。
 *
 * 【v4.1 から引き継いでいるもの】
 *
 * ・【最近やったこと】と【今回は出さないもの】
 * 　これが無いと、AI は過去を知らないまま「新しい案」を作ることになり、
 * 　前にやったものと同じ内容が出てくる
 * ・時間帯の語彙（v1.6 から復元したもの）
 * ・detail の形（✓3行＋「おわりの目安: 」）。
 * 　responseSchema の description と同じ文言に揃えること。
 * 　食い違うと description のほうが勝つ
 *
 * 【まだ渡せていない材料】
 *
 * ・迷い時間（押してから決めるまでの秒数）。いまはログに出しているだけ。
 */
@Component
public class PromptBuilder {

	/** プロンプトの版。直したら必ず上げる */
	private static final String VERSION = "v5.1";

	/** 日付の書き方 */
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/**
	 * 3案を出してもらうための文章を組み立てる。
	 *
	 * 組み立ての順番は、プロンプト設計書 1.1 に合わせている。
	 * 　役割 → 材料 → 指示 → 出力ルール
	 * Gemini は後ろに書いた指示ほど守るので、出力ルールを最後に置く。
	 *
	 * @param situation     いまの状況と、出す分野。SituationJudge が決めたもの
	 * @param activeTasks   いま未完了のタスク。提案の材料になる
	 * @param pickedTask    3件目にそのまま使うタスク。1件も無ければ null
	 * @param doneTasks     最近やったこと。新しい順。繰り返しを避けるための材料
	 * @param excludeTitles 今回は出さないもの。「別の案を見る」のとき前回の3件が入る
	 * @return AI に送る文章
	 */
	public String buildProposal(Situation situation, List<Task> activeTasks, Task pickedTask,
			List<Task> doneTasks, List<String> excludeTitles) {

		LocalDateTime now = LocalDateTime.now();
		StringBuilder p = new StringBuilder();

		// --- 役割 ---

		p.append("# prompt ").append(VERSION).append("\n\n");

		p.append("あなたは、動き出せない人のそばにいる人です。");
		p.append("動けるような励ましのタスクを作成してください。\n\n");

		// --- 材料 ---

		p.append("【今の状況】\n");
		p.append(now.format(DATE_FORMAT)).append(" ").append(now.getHour()).append("時ごろ\n");
		p.append("時間帯：").append(situation.getTimeOfDay()).append("\n");
		p.append("方針：").append(situation.getNote()).append("\n\n");

		p.append("【今かかえていること】\n");
		if (activeTasks.isEmpty()) {
			p.append("（まだ1件もありません）\n\n");
		} else {
			for (Task task : activeTasks) {
				p.append("・").append(task.getTitle());
				if (task.getDueDate() != null) {
					p.append("（締切 ").append(task.getDueDate()).append("）");
				}
				p.append("\n");
			}
			p.append("\n");
		}

		// 完了したものも材料に入れる。
		// 未完了だけだと「まだ手を付けていないもの」に偏るうえ、
		// AI は過去を知らないまま「新しい案」を作るので、前と同じものが出てくる。

		p.append("【最近やったこと】\n");
		if (doneTasks.isEmpty()) {
			p.append("（まだありません）\n\n");
		} else {
			for (Task task : doneTasks) {
				p.append("・").append(task.getTitle()).append("\n");
			}
			p.append("\n");
		}

		// 「別の案を見る」を押したときだけ中身が入る。
		// 前回の3件を控えずに呼び直すと、同じ材料・同じ指示なので似たものが返る。

		p.append("【今回は出さないもの】\n");
		if (excludeTitles.isEmpty()) {
			p.append("（特になし）\n\n");
		} else {
			for (String title : excludeTitles) {
				p.append("・").append(title).append("\n");
			}
			p.append("\n");
		}

		p.append("【3つ目に使うタスク】\n");
		p.append(pickedTask != null ? pickedTask.getTitle() : "（なし）").append("\n\n");

		// --- 指示 ---
		//
		// 定義をルールより先に置いている。
		// 「タスクとは何か」が決まっていないと、重さや形を指定しても意味がないため。

		p.append("【このアプリでの「タスク」の定義】\n");
		p.append("次の3つを全部満たすものだけを、タスクと呼びます。\n");
		p.append("1. 書いてあることだけをやれば「完了した」と言い切れる。\n");
		p.append("　 どこまでやれば完了かが、文に書いてある。\n");
		p.append("2. 今いる場所で、すぐに始められる。\n");
		p.append("3. 終わったあとに、何かをやった感じが残る。\n\n");

		// 分野・前置き・分数はアプリが決めている。AI が選ぶものではない。
		// ここで決まるので、「3案は違う話題に」と頼む必要がなくなった。

		p.append("【3つの案】\n");

		p.append("1つ目：分野は「").append(situation.getFirstCategory()).append("」。");
		p.append("titleの前置きは「");
		p.append(wordsFor(situation.getTimeOfDay(), situation.getFirstCategory()));
		p.append("」。");
		p.append("estimatedMinutes は 3〜10分。");
		p.append("今日はもう動けないと思っている人でも手が出るもの。\n");

		p.append("2つ目：分野は「").append(situation.getSecondCategory()).append("」。");
		p.append("titleの前置きは「");
		p.append(wordsFor(situation.getTimeOfDay(), situation.getSecondCategory()));
		p.append("」。");
		p.append("estimatedMinutes は ").append(situation.getSecondMinutes()).append("。");
		p.append("ちょっとの時間をつくればできること。\n");

		p.append("3つ目：上に指定したタスクです。title は一字も変えずにそのまま使い、\n");
		p.append("　　　　detail（完了のヒント）だけを書いてください。\n");
		p.append("　　　　estimatedMinutes と difficulty は、その内容から見積もってください。\n");
		p.append("　　　　「（なし）」のときは、3つ目も新しい案にしてください。\n\n");

		p.append("分野・前置き・分数は決まっています。変えないでください。\n\n");

		p.append("【title の付け方】\n");
		p.append("title は「前置き＋やること」の一文にしてください。\n");
		p.append("中黒（・）や記号で言葉をつなげてはいけません。\n");
		p.append("前置きと中身が噛み合うようにしてください。\n");
		p.append("例：午後の集中力アップに単語帳を5ページめくる\n");
		p.append("　　寝る前のリラックスに深呼吸を10回する\n\n");

		p.append("【granularity の決め方】\n");
		p.append("FIVE_MIN＝10分以内 ／ TODAY＝今日中に終わる ／ GROUP＝複数回に分かれる\n");
		p.append("estimatedMinutes と食い違わせないでください。\n\n");

		p.append("【守ること】\n");
		p.append("・やってみたいと思われるようなタスクを、");
		p.append("プロのタスクコーチとして考えてください。\n");
		p.append("・短時間とか集中とか、タスクをしやすくなる言葉を入れる。\n");
		p.append("・【最近やったこと】と【今回は出さないもの】に並んでいるものと、\n");
		p.append("　同じ内容・同じ道具・同じ場所を使う案は出さない。\n\n");

		p.append("【title と detail の関係】\n");
		p.append("title は、選ぶときに見るものです。detail の要約である必要はありません。\n");
		p.append("その案が何なのかが分かるシンプルな言葉にしてください。\n");
		p.append("detail は、選んだあとに手を動かすためのものなので、");
		p.append("title より具体的にしてください。\n\n");

		p.append("【detail の書き方】\n");
		p.append("「✓」で始まる行を3行書いたあと、最後の1行を");
		p.append("「おわりの目安: 」で始めてください（この行に✓は付けません）。\n");
		p.append("・✓の行は、1行に1つの動作だけ。「〜する」で終える。\n");
		p.append("・そのタスクの内容に合わせた具体的な手順にする。");
		p.append("「まず手をつける」「小さく分ける」のような一般論だけの手順は禁止。\n");
		p.append("・最後の行は、やり終えたときに目で見える状態を書く。\n");
		p.append("・その状態になったら、本人がどう思っていても終わりです。");
		p.append("そう書けるものだけを書く。\n");
		p.append("・行の区切りは改行（\\n）にする。\n");
		p.append("・全体で250字を超えないようにする。保存できる上限が決まっているため。\n");
		p.append("例：\n");
		p.append("　　✓近くの公園をさがす\n");
		p.append("　　✓10分間ウォーキングする\n");
		p.append("　　✓新しいことを見つける\n");
		p.append("　　おわりの目安: 10分たったら\n\n");

		// --- 出力ルール ---

		p.append("【出力】\n");
		p.append("cards という配列に3件。1つ目・2つ目・3つ目は上の順に並べる。\n");
		p.append("各件は次の項目を持つ。\n");
		p.append("title（30字以内）／category（べんきょう・せいかつ・リラックス のいずれか。");
		p.append("1つ目と2つ目は上で指定したものをそのまま入れる）／\n");
		p.append("granularity（FIVE_MIN・TODAY・GROUP）／difficulty（LOW・MID・HIGH）／\n");
		p.append("estimatedMinutes（整数）／reason（40字以内）／detail（上の書き方）\n");
		p.append("出力はJSONだけ。説明文・コードフェンス禁止。\n");

		return p.toString();
	}

	/**
	 * その時間帯・その分野に使う前置きを1つ返す。
	 *
	 * 以前は時間帯ごとに候補を3つ渡して、AI に選ばせていた。
	 * すると同じ言葉が2案に並んだり、
	 * 「午後のリフレッシュに単語帳をめくる」のように
	 * 前置きと中身が噛み合わないものが出た。
	 *
	 * 分野はアプリが決めているので、前置きも組で決めてしまう。
	 * 選ばせる余地を無くせば、ずれようがない。
	 *
	 * 言葉を直したいときは、ここ1か所を見ればよい。
	 *
	 * @param timeOfDay 朝・昼・夜・深夜
	 * @param category  べんきょう・せいかつ・リラックス
	 * @return 前置きの言葉
	 */
	private String wordsFor(String timeOfDay, String category) {

		return switch (timeOfDay) {

			// 立ち上げ・スイッチオン
			case "朝" -> switch (category) {
				case "べんきょう" -> "朝の集中力アップのために";
				case "せいかつ"   -> "出かける前にサクッと";
				default           -> "気持ちよく1日を始めるために";
			};

			// 仕切り直し・リフレッシュ
			case "昼" -> switch (category) {
				case "べんきょう" -> "午後の集中力アップに";
				case "せいかつ"   -> "ランチ後のひと動きに";
				default           -> "午後のリフレッシュに";
			};

			// アクティブ・集中・自分の時間
			case "夜" -> switch (category) {
				case "べんきょう" -> "夜の集中力復活のために";
				case "せいかつ"   -> "寝る前のひと片づけに";
				default           -> "夜活スタートに";
			};

			// レビュー・労い・ログ
			default -> switch (category) {
				case "べんきょう" -> "明日の自分をラクにするために";
				case "せいかつ"   -> "今日のおしまいに";
				default           -> "寝る前のリラックスに";
			};
		};
	}
}