package com.example.taskboard.ai;


import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.taskboard.domain.Difficulty;
import com.example.taskboard.domain.Granularity;
import com.example.taskboard.domain.ProposalCard;

// ai.mock=true のときに使う、決まった3案を返すクラス。
// 画面と保存の流れを、AI を呼ばずに完成させるために使う
@Component
@ConditionalOnProperty(name = "ai.mock", havingValue = "true")
public class MockProposalGenerator implements ProposalGenerator{
	
	@Override
	public List<ProposalCard>generate(String prompt){
		
		List<ProposalCard>cards=new ArrayList<>();
		
		cards.add(card("机の上を5分だけ片づける", "生活",
				Granularity.FIVE_MIN,Difficulty.LOW,5,
				"まず手を動かすと、次に進みやすいので"));
		
		cards.add(card("参考書を1ページだけ読む", "勉強",
				Granularity.FIVE_MIN, Difficulty.LOW, 5,
				"1ページなら、疲れていても始められるので"));
		
		cards.add(card("冷蔵庫にあるもので一品つくる", "食事",
				Granularity.TODAY, Difficulty.MID, 20,
				"買い物に行かずに済むうえ、達成感があるので"));
		
		return cards;
	}
	
	@Override
	public String sourceName() {
		return "MOCK";
	}
	
	// カードを1枚作る。同じ書き方を3回くり返さないためのメソッド
	private ProposalCard card(String title,String category,
			Granularity granularity,Difficulty difficulty,
			int minutes,String reason) {
		
		ProposalCard c =new ProposalCard();
		c.setTitle(title);
		c.setCategory(category);
		c.setGranularity(granularity);
		c.setDifficulty(difficulty);
		c.setEstimatedMinutes(minutes);
		c.setReason(reason);
		
		return c;
	}

}
