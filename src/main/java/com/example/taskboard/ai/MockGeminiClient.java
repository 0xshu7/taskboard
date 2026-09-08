package com.example.taskboard.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="ai.mock",havingValue = "true",matchIfMissing=true)

public class MockGeminiClient implements GeminiClient{
	
	@Override
	public String generate(String purpose,String prompt) {
		// 設計書 4.1.1 のスキーマに沿った固定応答。プロンプト設計書 3.1 の内容を後で resources/mock/*.json に移す
		return """
				{
				  "cards": [
				    {
				      "title": "机の上を5分だけ片づける",
				      "category": "生活",
				      "granularity": "FIVE_MIN",
				      "difficulty": "LOW",
				      "estimatedMinutes": 5,
				      "reason": "まず手を動かすと次に進みやすいので",
				      "steps": ["机の上の物を3つ、元の場所に戻す"],
				      "doneCondition": "机の上に物が3つ以上減っている",
				      "resultQuestion": "片づけた物はいくつでしたか？"
				    }
				  ]
				}
				""";
	}

}
