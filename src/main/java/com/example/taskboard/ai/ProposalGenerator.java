package com.example.taskboard.ai;


import java.util.List;

import com.example.taskboard.domain.ProposalCard;

// 提案カードを作る役目。モックと本物を差し替えられるようにする
public interface ProposalGenerator {
	
	// プロンプトを渡すと、カードを3枚返す
	// 返ってくるカードは、まだ DB に保存されていない状態
	List<ProposalCard>generate(String prompt);
	
	// この実装が何者かを表す。proposal_sets.source に記録する
	String sourceName();

}
