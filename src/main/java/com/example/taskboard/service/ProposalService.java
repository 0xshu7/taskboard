package com.example.taskboard.service;

// ===== インポート =====
// java.time.Duration      … 2つの時刻の差。迷い時間の計算に使う
// java.time.LocalDateTime … 日付＋時刻。作った時刻・決めた時刻に使う
// java.time.LocalTime     … 時刻だけ。いまが朝か夜かの判定に渡す
// java.util.List          … リストの型
//
// 以下は Spring の部品。
// Service       … 処理のまとまりです、という印。Spring が管理下に置く
// Transactional … メソッドの途中で失敗したら、その中の保存をすべて取り消す
//
// 以下はこのアプリの部品。
// FallbackCards          … AIが失敗したときに出す、決まった3案
// PromptBuilder          … AIに送る文章を組み立てる
// ProposalGenerator      … 3案を作る係。モックと本物を差し替えられる
// ProposalCard           … 提案カード1枚
// ProposalSet            … 提案1セット
// ProposalStatus         … セットの状態
// Situation              … いまの状況（時間帯と方針）
// SituationJudge         … 時刻から状況を判断する
// ProposalCardRepository … カードの出し入れ
// ProposalSetRepository  … セットの出し入れ

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

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

/**
 * スタートを押してから、3案を出し、選ぶまでを受け持つ。
 *
 * このアプリの中心にあたる処理で、担当することは3つ。
 *
 * 1. 3案を作る（generateSet）
 * 2. スタートに応える（start）
 * 3. 選ばれたときの後始末（choose / retopic）
 *
 * 押されてから作る。作りおきはしない。
 *
 * 以前は、スタート画面を開いた時点で裏で1セット作っておき、
 * 押されたときにはそれを出すことで待ち時間を消していた。
 * 速さでは優れていたが、次の2つと引き換えだった。
 *
 * 1. 1回のスタートで AI を2回呼ぶ（画面を開いたときと、押したとき）。
 *    無料枠が1日20回なので、実質10回しか使えなかった。
 * 2. 裏で動かすための @Async が必要になり、
 *    別の流れではログイン情報が引き継がれないため、
 *    利用者IDを引数で持ち回る必要があった。
 *
 * 押した回数ぶんしか呼ばなくなったので、20回が20回のまま使える。
 * 代わりに3〜5秒待つことになるので、画面側でボタンの表示を変えて知らせる。
 *
 * AI が失敗しても止まらない。
 * 呼び出しに失敗した場合は FallbackCards の固定3案に切り替える。
 * どこから来た3案かは ProposalSet.source に残すので、
 * あとから「AI が何割失敗したか」を数えられる。
 */
@Service
public class ProposalService {

	// ===== フィールド =====

	/** 提案セットの出し入れ */
	private final ProposalSetRepository setRepository;

	/** 提案カードの出し入れ */
	private final ProposalCardRepository cardRepository;

	/** 時刻から「いまどんな状況か」を判断する */
	private final SituationJudge situationJudge;

	/** AI に送る文章を組み立てる */
	private final PromptBuilder promptBuilder;

	/**
	 * 3案を作る係。
	 *
	 * 型はインターフェースにしてある。
	 * ai.mock の設定によって、中身がモックと本物で入れ替わる。
	 * このクラスはどちらが入っているかを知らないまま使える。
	 */
	private final ProposalGenerator generator;

	/** タスクの処理。手持ちのタスクを読み、選ばれたカードをタスクに変える */
	private final TaskService taskService;

	/** いまログインしている利用者を教えてくれる係 */
	private final UserService userService;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 *
	 * 自分で new せず、外から渡してもらう形にしておくと差し替えが効く。
	 * ProposalGenerator をモックと本物で切り替えられるのはこのため。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public ProposalService(ProposalSetRepository setRepository,
			ProposalCardRepository cardRepository,
			SituationJudge situationJudge,
			PromptBuilder promptBuilder,
			ProposalGenerator generator,
			TaskService taskService,
			UserService userService) {

		this.setRepository = setRepository;
		this.cardRepository = cardRepository;
		this.situationJudge = situationJudge;
		this.promptBuilder = promptBuilder;
		this.generator = generator;
		this.taskService = taskService;
		this.userService = userService;
	}

	// ===== メソッド =====

	/**
	 * スタートが押されたときに、3案のセットを返す。
	 *
	 * 押されるたびに新しく作る。前のセットは探さない。
	 * 押さずに離れたセットは OPEN のまま残るが、それも記録として意味がある。
	 *
	 * @return 表示する3案のセット
	 */
	@Transactional
	public ProposalSet start() {
		return generateSet();
	}

	/**
	 * 3案を作って保存する。
	 *
	 * AI の呼び出しに失敗した場合は固定3案に切り替える。
	 * ここで例外を外に出さないことが、「AIが落ちてもアプリは止まらない」を成り立たせている。
	 *
	 * 応答にかかった時間と、どこから来た3案かをログに出す。
	 * 速さの改善は、推測ではなく計測して判断する。
	 *
	 * @return 保存した3案のセット
	 */
	@Transactional
	public ProposalSet generateSet() {

		Long userId = userService.currentId();

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

		// セットを先に保存する。カードが持つ proposalSetId を決めるために、
		// セットの id が先に必要になるため。
		ProposalSet set = new ProposalSet();
		set.setUserId(userId);
		set.setRequestedAt(LocalDateTime.now());
		set.setStatus(ProposalStatus.OPEN);
		set.setSource(source);
		set.setPromptText(prompt);
		setRepository.save(set);

		// 3枚に1〜3の番号を振って保存する。
		// この番号が表示順であり、同時に重さの役割も表す。
		int position = 1;
		for (ProposalCard card : cards) {
			card.setProposalSetId(set.getId());
			card.setPosition(position);
			cardRepository.save(card);
			position++;
		}

		return set;
	}

	/**
	 * セットを1件取り出す。
	 *
	 * 見つからないときは例外を投げる。
	 * null を返すと、受け取った側が確認を忘れたときに
	 * ずっと先で分かりにくい形で失敗するため、その場で止める。
	 *
	 * @param setId セットのID
	 * @return 見つかったセット
	 */
	public ProposalSet findSet(Long setId) {
		return setRepository.findById(setId)
				.orElseThrow(() -> new IllegalArgumentException("提案が見つかりません: " + setId));
	}

	/**
	 * セットに属する3枚のカードを、表示順で取り出す。
	 *
	 * @param setId セットのID
	 * @return 1番目から順に並んだカード
	 */
	public List<ProposalCard> findCards(Long setId) {
		return cardRepository.findByProposalSetIdOrderByPositionAsc(setId);
	}

	/**
	 * カードを1つ選んだとき。タスクを作り、セットを決定済みにする。
	 *
	 * 選ばれなかった2枚も消さずに残す。
	 * 「何を出して、何が選ばれなかったか」が、提案の質を測る材料になるため。
	 *
	 * @param setId  セットのID
	 * @param cardId 選ばれたカードのID
	 */
	@Transactional
	public void choose(Long setId, Long cardId) {

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

	/**
	 * 「別の話題にする」を押したとき。いまのセットを閉じて、新しく作り直す。
	 *
	 * 閉じたセットも記録として残す。
	 * 何が選ばれなかったかは、提案を良くするための手がかりになる。
	 *
	 * @param setId 閉じるセットのID
	 * @return 新しく用意したセット
	 */
	@Transactional
	public ProposalSet retopic(Long setId) {

		ProposalSet set = findSet(setId);
		set.setStatus(ProposalStatus.RETOPIC);
		set.setDecidedAt(LocalDateTime.now());
		setRepository.save(set);

		printHesitation(set);

		return generateSet();
	}

	// ===== メソッド（内部で使うもの） =====

	/**
	 * 迷い時間をコンソールに出す。
	 *
	 * 押してから決めるまでの秒数。
	 * 何秒からを迷いと見なすかはアプリ側では決めず、
	 * 秒数をそのままプロンプトへ渡して解釈は AI に任せる方針。
	 * 同じ判断を2か所に持つと、必ずどこかで食い違うため。
	 *
	 * @param set 決定済みのセット
	 */
	private void printHesitation(ProposalSet set) {

		if (set.getDecidedAt() == null) {
			return;
		}

		// Duration.between は2つの時刻の差を表す。getSeconds で秒数になる
		long seconds = Duration.between(set.getRequestedAt(), set.getDecidedAt()).getSeconds();

		System.out.println("### 迷い時間 = " + seconds + "秒 / 結果 = " + set.getStatus());
	}
}