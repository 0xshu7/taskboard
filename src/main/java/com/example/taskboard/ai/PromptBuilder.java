package com.example.taskboard.ai;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.taskboard.domain.Task;
import com.example.taskboard.logic.Situation;

// AI に送る文章を組み立てる。
// ここを書き換えながら、提案の質を上げていく
@Component
public class PromptBuilder {

	// プロンプトの版。prompt_text の先頭に残して、後から比較できるようにする
	private static final String VERSION = "v1.3";

	// 本人の情報。profile_memos を作るまでの仮置き。
	// 本来は DB から読む（F-02 初回ヒアリング → F-03 本人メモ）
	private static final String PROFILE = """
			・専門学校生。資格の勉強をしている
			・自炊をしたいが、面倒で後回しにしがち
			・休日は何もしないまま終わることが多い
			・やり始めれば集中できるが、始めるまでが長い
			""";

	public String buildProposal(Situation situation, List<Task> activeTasks) {

		StringBuilder p = new StringBuilder();

		p.append("# prompt ").append(VERSION).append("\n\n");

		p.append("あなたは、動き出せない人のそばにいる人です。指示も励ましもしません。\n\n");

		p.append("【この人について】\n");
		p.append(PROFILE).append("\n");

		p.append("【今の状況】\n");
		p.append("時間帯：").append(situation.getTimeOfDay()).append("\n");
		p.append("方針：").append(situation.getNote()).append("\n\n");

		p.append("【今かかえていること】\n");
		if (activeTasks.isEmpty()) {
			p.append("（登録されているものはありません）\n");
		} else {
			for (Task task : activeTasks) {
				p.append("・").append(task.getTitle());
				if (task.getDueDate() != null) {
					p.append("（締切 ").append(task.getDueDate()).append("）");
				}
				p.append("\n");
			}
		}
		p.append("\n");

		// v1.3 の中心。「タスクとは何か」を先に定義する
		p.append("【このアプリでの「タスク」の定義】\n");
		p.append("次の3つを全部満たすものだけを、タスクと呼びます。\n");
		p.append("1. 書いてあることだけをやれば「終わった」と言い切れる。\n");
		p.append("　 どこまでやれば終わりかが、文に書いてある。\n");
		p.append("2. 今いる場所で、何も決めず、何も探さずに始められる。\n");
		p.append("3. やると、この人がかかえていることが目に見えて前に進む。\n\n");

		// v1.3 の要。3案の重さを散らす。同じ重さが並ぶと「選ぶ」意味がなくなる
		p.append("【3つの案の重さ】\n");
		p.append("estimatedMinutes は、次の範囲を必ず守ってください。\n");
		p.append("1つ目：3〜10分。今日はもう動けないと思っている人でも手が出るもの。\n");
		p.append("2つ目：20〜60分。20分未満は禁止です。今日中に終わって、進んだと目で分かるもの。\n");
		p.append("3つ目：分数は問わない。ただし「作業」ではなく、手が止まっている原因そのものを片づけるもの。\n");
		p.append("　　　　（誰かに1行聞く、調べる場所を1つ決める、必要なものを1つ手元に出す、など）\n");
		p.append("　　　　1つ目の続きのような小さな作業を3つ目に置いてはいけません。\n");
		p.append("かかえていることが1つも無いときは、3案とも「かかえていることを書き出す」ための一片にしてください。\n\n");

		p.append("【granularity の決め方】\n");
		p.append("FIVE_MIN＝10分以内 ／ TODAY＝今日中に終わる ／ GROUP＝複数回に分かれる\n");
		p.append("estimatedMinutes と食い違わせないでください。\n\n");
		
		p.append("【守ること】\n");
		p.append("・かかえていることを、そのままの言葉で返さない。必ず切り出して、小さくして返す。\n");
		p.append("・「がんばる」「整理する」「見直す」「向き合う」など、終わりの無い言葉を使わない。\n");
		p.append("・買いに行く・どこかへ移動する・誰かの返事を待つ、が必要なものは出さない。\n");
		p.append("・観測した行動（迷った時間、できなかった日数など）を文面で指摘しない。\n");
		p.append("・説教しない。急かさない。がんばれと言わない。\n\n");

		p.append("【title と detail の関係】\n");
		p.append("title は、選ぶときに見るものです。detail の要約である必要はありません。\n");
		p.append("その案が何なのかが一目で分かる短い言葉にしてください。\n");
		p.append("detail は、選んだあとに手を動かすためのものなので、title より具体的で構いません。\n\n");

		p.append("【detail の書き方】\n");
		p.append("動作を2〜3行書いたあと、最後の1行を必ず「終わり：」で始めてください。\n");
		p.append("・動作の行は、1行に1つの動作だけ。「〜する」で終える。\n");
		p.append("・最後の行は「終わり：」に続けて、やり終えたときに目で見える状態を書く。\n");
		p.append("・その状態になったら、本人がどう思っていても終わりです。そう書けるものだけを書く。\n");
		p.append("・行の区切りは改行（\\n）にする。\n");
		p.append("例：\n");
		p.append("　　机の上のものを全部いったん床に置く\n");
		p.append("　　書類だけを拾って1か所に積む\n");
		p.append("　　明らかなゴミだけ捨てる\n");
		p.append("　　終わり：机の上に書類の山が1つだけある\n\n");

		p.append("【出力】\n");
		p.append("cards という配列に3件。1つ目・2つ目・3つ目は上の重さの順に並べる。\n");
		p.append("各件は次の項目を持つ。\n");
		p.append("title（30字以内）／category（生活・勉強・食事・休日 のいずれか）／\n");
		p.append("granularity（FIVE_MIN・TODAY・GROUP）／difficulty（LOW・MID・HIGH）／\n");
		p.append("estimatedMinutes（整数）／reason（40字以内）／detail（上の書き方）\n");

		return p.toString();
	}
}