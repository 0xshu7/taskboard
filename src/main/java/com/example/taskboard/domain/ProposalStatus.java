package com.example.taskboard.domain;


//提案1セットの状態
public enum ProposalStatus {
	
	OPEN,       // 3案を出して、まだ選ばれていない
	CHOSEN,     // どれかが選ばれた
	RETOPIC,    // 「別の話題にする」が押された
	EXPIRED     // 時間切れ（30分）

}
