package com.example.taskboard.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskboard.ai.PromptBuilder;
import com.example.taskboard.ai.ProposalGenerator;
import com.example.taskboard.domain.ProposalCard;
import com.example.taskboard.domain.ProposalSet;
import com.example.taskboard.domain.ProposalStatus;
import com.example.taskboard.logic.Situation;
import com.example.taskboard.logic.SituationJudge;
import com.example.taskboard.repository.ProposalCardRepository;
import com.example.taskboard.repository.ProposalSetRepository;
import com.example.taskboard.repository.UserRepository;

// スタートを押してから、3案を出し、選ぶまでを受け持つ
@Service
public class ProposalService {

    private final UserRepository userRepository;

	// ログイン機能を作るまでの仮のユーザーID
	private static final Long USER_ID = 1L;

	private final ProposalSetRepository setRepository;
	private final ProposalCardRepository cardRepository;
	private final SituationJudge situationJudge;
	private final PromptBuilder promptBuilder;
	private final ProposalGenerator generator;
	private final TaskService taskService;

	public ProposalService(ProposalSetRepository setRepository,
			ProposalCardRepository cardRepository,
			SituationJudge situationJudge,
			PromptBuilder promptBuilder,
			ProposalGenerator generator,
			TaskService taskService, UserRepository userRepository) {

		this.setRepository = setRepository;
		this.cardRepository = cardRepository;
		this.situationJudge = situationJudge;
		this.promptBuilder = promptBuilder;
		this.generator = generator;
		this.taskService = taskService;
		this.userRepository = userRepository;
	}

	// スタートを押したとき。3案を作って保存し、そのセットを返す
	@Transactional
	public ProposalSet start() {

		// 1. 今の状況を判断する
		Situation situation = situationJudge.judge(LocalTime.now());

		// 2. AI に渡す文章を組み立てる
		String prompt = promptBuilder.buildProposal(situation, taskService.findActive());

		// 3. カードを作ってもらう（今はモックが固定の3枚を返す）
		List<ProposalCard> cards = generator.generate(prompt);

		// 4. セットを保存する。プロンプト全文も一緒に残す
		ProposalSet set = new ProposalSet();
		set.setUserId(USER_ID);
		set.setRequestedAt(LocalDateTime.now());
		set.setStatus(ProposalStatus.OPEN);
		set.setSource(generator.sourceName());
		set.setPromptText(prompt);
		setRepository.save(set);
		
		// 5. カードにセットIDと並び順を付けて保存する
		int position =1;
		for(ProposalCard card:cards) {
			card.setProposalSetId(set.getId());
			card.setPosition(position);
			cardRepository.save(card);
			position++;
		}
		
		return set;

	}
	
	public ProposalSet findSet(Long setId) {
		return setRepository.findById(setId)
				.orElseThrow(() -> new IllegalArgumentException("提案が見つかりません: " + setId));
		
	}
	
	public List<ProposalCard>findCards(Long setId){
		return cardRepository.findByProposalSetIdOrderByPositionAsc(setId);
	}
	
	// カードを1つ選んだとき。タスクを作り、セットを決定済みにする
	@Transactional
	public void choose(Long setId,Long cardId) {
		
		ProposalSet set = findSet(setId);
		
		ProposalCard card = cardRepository.findById(cardId)
				.orElseThrow(() -> new IllegalArgumentException("カードが見つかりません: " + cardId));

		// 選ばれた印を付ける
		card.setChosen(true);
		cardRepository.save(card);
		
		// セットを決定済みにする。decidedAt が迷い時間の計算に使われる
		set.setStatus(ProposalStatus.CHOSEN);
		set.setDecidedAt(LocalDateTime.now());
		setRepository.save(set);
		
		// タスクとして登録する
		taskService.createFromCard(card);
		
		printHesitation(set);
	}
	
	// 「別の話題にする」を押したとき。今のセットを閉じて、新しく作り直す
	@Transactional
	public ProposalSet retopic(Long setId) {
		
		ProposalSet set = findSet(setId);
		set.setStatus(ProposalStatus.RETOPIC);
		set.setDecidedAt(LocalDateTime.now());
		setRepository.save(set);
		
		printHesitation(set);

		return start();		
		
	}
	
	// 迷い時間をコンソールに出す。
	// あとで次回のプロンプトに渡すが、今は動きの確認だけ
	private void printHesitation(ProposalSet set) {
		
		if (set.getDecidedAt()==null) {
			return;
		}
		
		long seconds = Duration.between(set.getRequestedAt(), set.getDecidedAt()).getSeconds();
		
		System.out.println("### 迷い時間 = " + seconds + "秒 / 結果 = " + set.getStatus());
	}

}
