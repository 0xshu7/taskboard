package com.example.taskboard.ai;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.taskboard.domain.Task;
import com.example.taskboard.logic.Situation;

// AI に送る文章を組み立てる。
// ここを書き換えながら、提案の質を上げていく
@Component
public class PromptBuilder {
	
	public String buildProposal(Situation situation,List<Task>activeTasks) {
		StringBuilder p = new StringBuilder();
		
		p.append("あなたは、行動を後押しする相棒です。\n");
		p.append("次の状況の人に、今すぐ着手できるタスクを3つ提案してください。\n\n");
		
		p.append("【今の状況】\n");
		p.append("時間帯：").append(situation.getTimeOfDay()).append("\n");
		p.append("方針：").append(situation.getNote()).append("\n\n");
		
		p.append("【今かかえているタスク】\n");
		if(activeTasks.isEmpty()) {
			p.append("（登録されているタスクはありません）\n");
		}else {
			for(Task task:activeTasks) {
				p.append("・").append(task.getTitle());
				if(task.getDueDate() != null) {
					p.append("（締切 ").append(task.getDueDate()).append("）");
				}
				p.append("\n");
			}
		}
		p.append("\n");
		
		p.append("【守ること】\n");
		p.append("・3つとも違う種類にする。似たものを並べない。\n");
		p.append("・「やろうと思えば今すぐ始められる」大きさにする。\n");
		p.append("・reason は、なぜ今これを勧めるのかを40字以内で書く。\n");
		p.append("・観測した行動（迷った時間、できなかった日数など）を文面で指摘しない。\n");
		p.append("・説教しない。急かさない。\n");
		
		return p.toString();
		
		
	}

}
