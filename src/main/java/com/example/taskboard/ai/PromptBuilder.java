package com.example.taskboard.ai;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.taskboard.domain.Task;
import com.example.taskboard.logic.Categories;
import com.example.taskboard.logic.Situation;

/**
 * AI に送る文章を組み立てる。
 *
 * 【v5.4 について】
 *
 * FallbackCards と世界観を統一。
 * ゲームやクエストのような軽快なテンポ感とワクワク感を出しつつ、
 * 「すぐ手が出る軽さ」と「達成感」を両立させたプロンプトに改善。
 *
 * 【detail の3行の役割】
 *  1行目 … さいしょの一歩（今すぐ手が出る軽い入口）
 *  2行目 … このタスクの中心（ここが終われば「やった」と言える）
 *  3行目 … ついでにできること（できなくてもOK）
 *  おわりの目安 … 2行目が終わる時点。
 *
 * 【分野の名前は書かない】
 *
 * 「べんきょう」などの文字を直に書くと、言い換えたいときに
 * ここと SituationJudge と FallbackCards の3か所を探すことになる。
 * Categories が持っているものを使う。
 *
 * 出力の決まりに並べる一覧も、同じところから組み立てている。
 * 文章の中に書き写すと、増やしたときに古い3つが残る。
 */
@Component
public class PromptBuilder {

	/** プロンプトの版。直したら必ず上げる */
	private static final String VERSION = "v5.4";

	/** 日付の書き方 */
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/**
	 * 3案を出してもらうための文章を組み立てる。
	 *
	 * @param situation     いまの状況と、出す分野。SituationJudge が決めたもの
	 * @param activeTasks   いま未完了のタスク。提案の材料になる
	 * @param pickedTask    3件目にそのまま使うタスク。1件も無ければ null
	 * @param doneTasks     最近やったこと。新しい順
	 * @param excludeTitles 今回は出さないもの。「別の案を見る」のとき前回の3件が入る
	 * @return AI に送る文章
	 */
	public String buildProposal(Situation situation, List<Task> activeTasks, Task pickedTask,
			List<Task> doneTasks, List<String> excludeTitles) {

		LocalDateTime now = LocalDateTime.now();
		StringBuilder p = new StringBuilder();

		// --- 役割 ---

		p.append("# prompt ").append(VERSION).append("\n\n");

		p.append("あなたは、行動を起こすきっかけをつくるプロのタスクコーチです。");
		p.append("立ち止まっている人が「ちょっとやってみようかな」とワクワクして動き出せて、");
		p.append("終わったときに「できた！」とゲームをクリアしたような爽快感を味わえるタスク提案を作ってください。\n\n");

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

		p.append("【最近やったこと】\n");
		if (doneTasks.isEmpty()) {
			p.append("（まだありません）\n\n");
		} else {
			for (Task task : doneTasks) {
				p.append("・").append(task.getTitle()).append("\n");
			}
			p.append("\n");
		}

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

		p.append("【このアプリでの「タスク」の定義】\n");
		p.append("次の3つを全部満たすものだけを、タスクと呼びます。\n");
		p.append("1. 書いてあることだけをやれば「完了した」と言い切れる。\n");
		p.append("2. 今いる場所で、迷わずすぐに始められる。\n");
		p.append("3. 終わったあとに「スッキリした」「やった！」という手ごたえが残る。\n\n");

		p.append("【3つの案】\n");

		p.append("1つ目：分野は「").append(situation.getFirstCategory()).append("」。");
		p.append("titleの前置きは「");
		p.append(wordsFor(situation.getTimeOfDay(), situation.getFirstCategory()));
		p.append("」。");
		p.append("estimatedMinutes は 3〜10分。");
		p.append("やる気が起きないときでもサクッと手が動く軽いミニミッション。\n");

		p.append("2つ目：分野は「").append(situation.getSecondCategory()).append("」。");
		p.append("titleの前置きは「");
		p.append(wordsFor(situation.getTimeOfDay(), situation.getSecondCategory()));
		p.append("」。");
		p.append("estimatedMinutes は ").append(situation.getSecondMinutes()).append("。");
		p.append("少し集中して動くことで、大きな達成感・クリア感が得られるもの。\n");

		p.append("3つ目：上に指定したタスクです。title は一字も変えずにそのまま使い、\n");
		p.append("    detail（完了のヒント）だけを書いてください。\n");
		p.append("    estimatedMinutes と difficulty は書かなくてかまいません。\n");
		p.append("    「（なし）」のときは、3つ目も新しい案にしてください。\n\n");

		p.append("分野・前置き・分数は決まっています。変えないでください。\n\n");

		p.append("【わくわく感を出す言葉選びのトーン】\n");
		p.append("・日常の中で手軽に挑戦できるポジティブで軽快なトーンにする。\n");
		p.append("・動詞を具体化し、「ロックオン」「一気に」「ピカピカ」「サクッと」「大作戦」など動きたくなるリズム感を出す。\n");
		p.append("・毎回違う、新しいアイデアを出してください。\n\n");

		p.append("【title の付け方】\n");
		p.append("title は「前置き＋やること」の一文にしてください。（30字以内）\n");
		p.append("パッと見て「これなら面白そう」「できそう」と思えるキャッチーな言い方に。\n\n");

		p.append("【reason の書き方】\n");
		p.append("「やるとどうなるか」「終わったあとの良いこと」を言い切りで短く書く。（40字以内）\n\n");

		p.append("【granularity の決め方】\n");
		p.append("FIVE_MIN＝10分以内 ／ TODAY＝今日中に終わる ／ GROUP＝複数回に分かれる\n");
		p.append("estimatedMinutes と食い違わせないでください。\n\n");

		p.append("【title と detail の関係】\n");
		p.append("title は選ぶときに見るもの。detail の要約である必要はありません。\n");
		p.append("detail は選んだあとに手を動かすためのステップです。\n\n");

		p.append("【detail の書き方】\n");
		p.append("✓の行を3行、最後に「おわりの目安: 」の1行。（全体で250字以内）\n");
		p.append("  ✓（すぐ手が出る1秒の入口）\n");
		p.append("  ✓（このタスクの中心。ここが終われば満足できる行動）\n");
		p.append("  ✓（中心をやっている間にできる、ほんの少しのプラスワン）\n");
		p.append("  おわりの目安: （2行目が終わる時点の完了状態。例：〜したらクリア！ など）\n");
		p.append("3行目ができなくてもスッキリ終われるラインにしてください。\n");
		p.append("行の区切りは改行（\\n）。\n\n");

		// --- 出力ルール ---

		p.append("【出力】\n");
		p.append("cards という配列に3件。1つ目・2つ目・3つ目は上の順に並べる。\n");
		p.append("各件は次の項目を持つ。\n");

		// 分野の一覧は Categories から組み立てる。
		// 文章の中に書き写すと、分野を増やしたときに古い3つが残る
		p.append("title（30字以内）／category（");
		p.append(String.join("・", Categories.ALL));
		p.append(" のいずれか。");
		p.append("1つ目と2つ目は上で指定したものをそのまま入れる）／\n");

		p.append("granularity（FIVE_MIN・TODAY・GROUP）／difficulty（LOW・MID・HIGH）／\n");
		p.append("estimatedMinutes（整数）／reason（40字以内）／detail（上の書き方）\n");
		p.append("出力はJSONだけ。説明文・コードフェンス禁止。\n");

		return p.toString();
	}

	/**
	 * その時間帯・その分野に使う前置きを1つ返す。
	 *
	 * 分野の名前は Categories の定数を使っている。
	 * final で中身が文字そのものなので、switch の case にそのまま書ける。
	 *
	 * @param timeOfDay 朝・昼・夜・深夜
	 * @param category  分野
	 * @return title の前置き
	 */
	public static String wordsFor(String timeOfDay, String category) {

		return switch (timeOfDay) {

			// 立ち上げ・スイッチオン
			case "朝" -> switch (category) {
				case Categories.STUDY -> "朝の集中力アップに";
				case Categories.LIFE  -> "出かける前にサクッと";
				default               -> "気持ちよく1日を始めるために";
			};

			// 仕切り直し・リフレッシュ
			case "昼" -> switch (category) {
				case Categories.STUDY -> "午後の集中力アップに";
				case Categories.LIFE  -> "食後にサクッと";
				default               -> "午後のリフレッシュに";
			};

			// アクティブ・集中・自分の時間
			case "夜" -> switch (category) {
				case Categories.STUDY -> "夜の集中タイムに";
				case Categories.LIFE  -> "寝る前のひと整えに";
				default               -> "夜のリフレッシュに";
			};

			// レビュー・労い・ログ
			default -> switch (category) {
				case Categories.STUDY -> "明日の自分をラクにするために";
				case Categories.LIFE  -> "今日のおしまいに";
				default               -> "寝る前のリラックスに";
			};
		};
	}
}