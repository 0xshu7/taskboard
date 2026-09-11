package com.example.taskboard.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskboard.ai.FallbackCards;
import com.example.taskboard.ai.PromptBuilder;
import com.example.taskboard.ai.ProposalGenerator;
import com.example.taskboard.domain.ProposalCard;
import com.example.taskboard.domain.ProposalSet;
import com.example.taskboard.domain.ProposalStatus;
import com.example.taskboard.logic.Situation;
import com.example.taskboard.logic.SituationJudge;
import com.example.taskboard.repository.ProposalCardRepository;
import com.example.taskboard.repository.ProposalSetRepository;  
// スタートを押してから、3案を出し、選ぶまでを受け持つ
@Service
public class ProposalService {

	// ログイン機能を作るまでの仮のユーザーID
	private static final Long USER_ID = 1L;
	
	// 事前生成したセットが有効な時間。これを過ぎたら作り直す
	@Value("${app.proposal.expire-minutes:30}")
	private int expireMinutes;
	

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
			TaskService taskService) {

		this.setRepository = setRepository;
		this.cardRepository = cardRepository;
		this.situationJudge = situationJudge;
		this.promptBuilder = promptBuilder;
		this.generator = generator;
		this.taskService = taskService;

	}

	// スタート画面を開いたときに、裏で1セット作っておく。
	// @Async を付けると、呼んだ側は待たずにすぐ次へ進む
	@Async
	public void prepareInBackground() {

		// すでに使われていないセットがあれば、作らない（無駄にAIを呼ばない）
		boolean exists = setRepository
				.findFirstByUserIdAndStatusOrderByIdDesc(USER_ID, ProposalStatus.OPEN)
				.isPresent();

		if (exists) {
			return;
		}

		try {
			generateSet();
			System.out.println("### 事前生成 完了");

		} catch (RuntimeException e) {
			// 裏の処理なので、失敗しても画面には影響させない
			System.out.println("### 事前生成 失敗: " + e.getMessage());
		}
	}

	@Transactional
	public ProposalSet start() {

		Optional<ProposalSet> ready =
				setRepository.findFirstByUserIdAndStatusOrderByIdDesc(USER_ID, ProposalStatus.OPEN);

		if (ready.isPresent()) {
			ProposalSet set = ready.get();

			// 古いセットは使わない。時間が経つと状況が変わっているため
			if (set.getRequestedAt().isBefore(LocalDateTime.now().minusMinutes(expireMinutes))) {
				set.setStatus(ProposalStatus.EXPIRED);
				setRepository.save(set);
				System.out.println("### 古いセットを破棄（EXPIRED）");
				return generateSet();
			}

			// 迷い時間は「押してから選ぶまで」なので、押した時刻に更新する。
			// 作った時刻のままだと、待っていた時間まで迷いに数えてしまう
			set.setRequestedAt(LocalDateTime.now());
			setRepository.save(set);

			System.out.println("### 事前生成を使用（待ち時間 0秒）");
			return set;
		}

		// 間に合わなかった場合はその場で作る
		return generateSet();
	}

	// 実際に3案を作って保存する。start と prepareInBackground の共通部分
	@Transactional
	public ProposalSet generateSet() {

		Situation situation = situationJudge.judge(LocalTime.now());
		String prompt = promptBuilder.buildProposal(situation, taskService.findActive());

		List<ProposalCard> cards;
		String source;

		long begin = System.currentTimeMillis();

		try {
			cards = generator.generate(prompt);
			source = generator.sourceName();

		} catch (RuntimeException e) {
			System.out.println("### AI 失敗のためフォールバック: " + e.getMessage());
			cards = FallbackCards.create();
			source = "FALLBACK";
		}

		System.out.println("### AI 応答 " + (System.currentTimeMillis() - begin)
				+ " ms / source=" + source);

		ProposalSet set = new ProposalSet();
		set.setUserId(USER_ID);
		set.setRequestedAt(LocalDateTime.now());
		set.setStatus(ProposalStatus.OPEN);
		set.setSource(source);
		set.setPromptText(prompt);
		setRepository.save(set);

		int position = 1;
		for (ProposalCard card : cards) {
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
