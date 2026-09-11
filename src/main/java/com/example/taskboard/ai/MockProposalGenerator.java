package com.example.taskboard.ai;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.taskboard.domain.ProposalCard;

// ai.mock=true のときに使う、決まった3案を返すクラス。
// 画面と保存の流れを、AI を呼ばずに完成させるために使う。
// 中身は FallbackCards と共通（同じ3案を2箇所に書かないため）
@Component
@ConditionalOnProperty(name = "ai.mock", havingValue = "true")
public class MockProposalGenerator implements ProposalGenerator {

	@Override
	public List<ProposalCard> generate(String prompt) {
		return FallbackCards.create();
	}

	@Override
	public String sourceName() {
		return "MOCK";
	}
}