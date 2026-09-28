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
 * 【v4.1 について】
 *
 * v4.0（v1.6 を土台に戻した版）に、材料を2つ足した版。
 *
 * ■ 足したもの：最近やったこと・今回は出さないもの
 *
 * v4.0 まで、AI に渡していたのは「未完了の一覧・時間帯・3つ目に使うタスク」だけだった。
 * 完了したタスクも、前回出した3案も渡していない。
 * つまり AI は過去を知らないまま「新しい案」を作れと言われていた。
 *
 * その結果、前にやったものと同じ内容が「あたらしい」として出てきた。
 * プロンプトの書き方の問題ではなく、材料が足りていなかった。
 *
 * これで「別の案を見る」も動く。
 * 前は同じ材料で呼び直すだけだったので、似たものが返ってきて当然だった。
 *
 * 【v4.0 で戻したもの：時間帯の語彙】
 *
 * v1.5 と v1.6 には「title の付け方」の節に、時間帯ごとの言葉があった。
 * v2 の書き直しで消えていた。消したことに気づいたのは 9/25 で、
 * proposal_sets.prompt_text に v1.6 が残っていたので復元できた。
 *
 * 版番号を先頭に書いて保存する仕組みが、そのまま復元の手段になった。
 *
 * v1.6 との違いは1点。あちらは4時間帯ぶんを全部渡していたが、
 * 朝に「今夜のメインイベント」が見えている必要はないので、
 * いまの時間帯のぶんだけを渡す。
 *
 * 【v4.0 で直したもの1：プロフィールを外した】
 *
 * v1.6 には「専門学校生」「自炊が面倒」など4行が書いてあった。
 * これは初回の3択（F-02）に本人が答えた結果を入れる場所で、
 * その画面がまだ無いので仮の値を置いていた。
 *
 * 仮の値は、AI にとって本人の答えと区別がつかない。
 * 材料が他に無いとき（タスク0件のとき）ほど、この数行だけが残って強く効く。
 * 答えが無いなら渡さない。3択の画面ができたら、そのとき戻す。
 *
 * 【v4.0 で直したもの2：detail の形】
 *
 * v1.6 は最後の行を「終わり：」で始めていたが、いまは
 * 「✓」で始まる3行＋「おわりの目安: 」の1行に変わっている。
 * responseSchema の description 側も同じ文言に揃っていること。
 * ここが食い違うと、description のほうが勝つ。
 *
 * 【まだ渡せていない材料】
 *
 * ・迷い時間（押してから決めるまでの秒数）。いまはログに出しているだけ。
 */
@Component
public class PromptBuilder {

	/** プロンプトの版。直したら必ず上げる */
	private static final String VERSION = "v4.1";

	/** 日付の書き方 */
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/**
	 * 3案を出してもらうための文章を組み立てる。
	 *
	 * 組み立ての順番は、プロンプト設計書 1.1 に合わせている。
	 * 　役割 → 材料 → 指示 → 出力ルール
	 * Gemini は後ろに書いた指示ほど守るので、出力ルールを最後に置く。
	 *
	 * @param situation     いまの状況。SituationJudge が時刻から判断したもの
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

		p.append("【3つの案】\n");
		p.append("estimatedMinutes は、次の範囲を必ず守ってください。\n");
		p.append("1つ目：3〜10分。今日はもう動けないと思っている人でも手が出るもの。\n");
		p.append("2つ目：20〜60分。ちょっとの時間をつくればできること。\n");
		p.append("　　　　1つ目とも3つ目とも違う話題にする。\n");
		p.append("3つ目：上に指定したタスクです。title は一字も変えずにそのまま使い、\n");
		p.append("　　　　detail（完了のヒント）だけを書いてください。\n");
		p.append("　　　　estimatedMinutes と difficulty は、その内容から見積もってください。\n");
		p.append("　　　　「（なし）」のときは、3つ目も新しい案にしてください。\n\n");
		p.append("3案のうち1案は外出や運動など活動的な内容を提案する。\n\n");

		// title の付け方。
		// 前置きの言葉はアプリが用意する。AI が決めるのは後ろの行動だけ。
		//
		// 例として見せているのではなく、選択肢として渡している。
		// そのまま使われて構わない。むしろ揃っているほうが読みやすい。

		p.append("【title の付け方】\n");
		p.append("title は一文にしてください。中黒（・）や記号で言葉をつなげてはいけません。\n");
		p.append("例：ランチ後の短時間ウォーキング\n");
		p.append("　　朝の集中力アップのためにコーヒーを飲む\n\n");

		p.append(situation.getTimeOfDay()).append("の言葉\n");
		p.append(wordsFor(situation.getTimeOfDay())).append("\n\n");

		p.append("上の言葉は変えて構いません。\n");
		p.append("3案は必ず違うタスクにしてください。\n");
		p.append("時間帯の言葉とスムーズにつながる内容にしてください。\n");
		p.append("時間帯の言葉とスムーズに言葉がつながらないわかりにくいタスクは禁止。\n\n");

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
		p.append("cards という配列に3件。1つ目・2つ目・3つ目は上の重さの順に並べる。\n");
		p.append("各件は次の項目を持つ。\n");
		p.append("title（30字以内）／category（生活・勉強・食事・休日 のいずれか）／\n");
		p.append("granularity（FIVE_MIN・TODAY・GROUP）／difficulty（LOW・MID・HIGH）／\n");
		p.append("estimatedMinutes（整数）／reason（40字以内）／detail（上の書き方）\n");
		p.append("出力はJSONだけ。説明文・コードフェンス禁止。\n");

		return p.toString();
	}

	/**
	 * その時間帯に使う前置きの言葉を返す。
	 *
	 * v1.6 では4時間帯ぶんを全部渡していたが、
	 * 朝に「今夜のメインイベント」が見えている必要はないので、
	 * いまの時間帯のぶんだけにしている。
	 *
	 * 言葉を足したり削ったりするのは、ここを直すだけで済む。
	 *
	 * @param timeOfDay SituationJudge が決めた時間帯。朝・昼・夜・深夜
	 * @return 前置きの候補
	 */
	private String wordsFor(String timeOfDay) {

		return switch (timeOfDay) {

			// 立ち上げ・スイッチオン
			case "朝" -> """
					　朝の集中力アップのため／出かける前にサクッと／気持ちよく1日を始めるため""";

			// 仕切り直し・リフレッシュ
			case "昼" -> """
					　午後のリフレッシュ／午後の集中力アップ／ランチ後の""";

			// アクティブ・集中・自分の時間
			case "夜" -> """
					　夜活スタート／夜の集中力復活のために／夜のギア上げ""";

			// レビュー・労い・ログ
			case "深夜" -> """
					　夜のreview／今日のごほうび／寝る前のリラックス""";

			// 想定外の値が来たとき。止めずに続ける。
			// SituationJudge は4つしか返さないので、ここは通らないはず。
			// それでも default が無いと switch 式はコンパイルできない
			default -> "　（いまの時間帯に合う言葉を、考えて提案）";
		};
	}
}