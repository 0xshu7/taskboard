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
	private static final String VERSION = "v1.1";

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

		p.append("あなたは、行動を後押しする相棒です。\n");
		p.append("次の人に、今すぐ着手できるタスクを3つ提案してください。\n\n");

		p.append("【この人について】\n");
		p.append(PROFILE).append("\n");

		p.append("【今の状況】\n");
		p.append("時間帯：").append(situation.getTimeOfDay()).append("\n");
		p.append("方針：").append(situation.getNote()).append("\n\n");

		p.append("【今かかえているタスク】\n");
		if (activeTasks.isEmpty()) {
			p.append("（登録されているタスクはありません）\n");
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

		// ここが v1.1 の要。3つの役割を分けて、選ぶ意味を持たせる
		p.append("【3つの役割】\n");
		p.append("1つ目：上の「かかえているタスク」のどれか1つを、**最初の一歩**まで刻んだもの。\n");
		p.append("　　　　5分以内で終わり、やれば元のタスクが進んだと言える大きさにする。\n");
		p.append("2つ目：今日中に終わる、少し手応えのあるもの。\n");
		p.append("3つ目：かかえているタスクとは関係のない、気分が変わるもの。\n");
		p.append("（かかえているタスクが無い場合は、1つ目も自由に決めてよい）\n\n");

		p.append("【守ること】\n");
		p.append("・かかえているタスクを、そのままの表現で提案しない。必ず刻むか、別のものにする。\n");
		p.append("・「今いる場所で、道具を探さずに始められる」大きさにする。\n");
		p.append("・title は、何をするかが読んだだけで分かる具体的な動作にする。\n");
		p.append("・reason は、なぜ今この人にこれを勧めるのかを40字以内で書く。\n");
		p.append("・観測した行動（迷った時間、できなかった日数など）を文面で指摘しない。\n");
		p.append("・説教しない。急かさない。がんばれと言わない。\n\n");

		p.append("【出力】\n");
		p.append("cards という配列に3件。各件は次の項目を持つ。\n");
		p.append("title（30字以内）／category（生活・勉強・食事・休日 のいずれか）／\n");
		p.append("granularity（FIVE_MIN・TODAY・GROUP）／difficulty（LOW・MID・HIGH）／\n");
		p.append("estimatedMinutes（整数）／reason（40字以内）\n");

		return p.toString();
	}
}