package com.example.taskboard.service;

// ===== インポート =====
// java.time.Duration      … 2つの時刻の差。迷い時間の計算に使う
// java.time.LocalDateTime … 日付＋時刻。作った時刻・決めた時刻に使う
// java.time.LocalTime     … 時刻だけ。いまが朝か夜かの判定に渡す
// java.util.List          … リストの型
// java.util.Optional      … 「あるかもしれないし、無いかもしれない」入れ物
//
// 以下は Spring の部品。
// Value         … application.yml の値をフィールドに読み込む
// Async         … 呼んだ側を待たせず、別の流れで動かす
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

/**
 * スタートを押してから、3案を出し、選ぶまでを受け持つ。
 *
 * このアプリの中心にあたる処理で、担当することは4つ。
 *
 * 1. 3案を作る（generateSet）
 * 2. スタートに応える（start）
 * 3. 選ばれたときの後始末（choose / retopic）
 * 4. 待ち時間を消す（prepareInBackground）
 *
 * 待ち時間について。
 * AI の応答には数秒かかる。人が待てるのは3秒程度で、10秒を超えると離脱する。
 * 「迷っているより、まず実行」を掲げる以上、押してから数秒待たせるのは致命的になる。
 * そこで、スタート画面を開いた時点で裏で1セット作っておき、
 * 押されたときにはそれを出すことで、待ち時間を実質0にしている。
 *
 * 作りおきには期限を設ける。
 * 数時間前に作った3案は、そのときの状況に合っていないため、
 * 一定時間を過ぎたセットは破棄して作り直す。
 *
 * AI が失敗しても止まらない。
 * 呼び出しに失敗した場合は FallbackCards の固定3案に切り替える。
 * どこから来た3案かは ProposalSet.source に残すので、
 * あとから「AI が何割失敗したか」を数えられる。
 *
 * 利用者IDの扱いについて。
 * 裏で動く処理（@Async）は別の流れで動くため、
 * そこでは「いまログインしている人」が分からない。
 * そのため generateSet と prepareInBackground は、IDを引数で受け取る。
 * 画面から呼ばれるメソッド（start / choose / retopic）は、
 * ログイン情報がそのまま使えるので userId() で取る。
 */
@Service
public class ProposalService {

	// ===== フィールド =====

	/**
	 * 事前生成したセットが有効な時間（分）。これを過ぎたら作り直す。
	 *
	 * @Value は application.yml の値を読み込む仕組み。
	 * 末尾の :30 は「設定が見つからないときは30を使う」という既定値。
	 * 調整したい数字をコードに直接書かずに済ませている。
	 */
	@Value("${app.proposal.expire-minutes:30}")
	private int expireMinutes;

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
	 * スタート画面を開いたときに、裏で1セット作っておく。
	 *
	 * @Async を付けると、呼んだ側は終わるのを待たずに次へ進む。
	 * 画面の表示が数秒止まらないのは、このため。
	 *
	 * 注意が2つある。
	 *
	 * 1. @Async はクラスの外から呼ばれたときだけ効く。
	 *    同じクラスの中から呼ぶと、ふつうのメソッド呼び出しになり、待たされる。
	 *    そのため、このメソッドはコントローラから直接呼んでいる。
	 *
	 * 2. 別の流れで動くので、ログイン情報が引き継がれない。
	 *    ここで「いまの利用者」を取ろうとすると失敗する。
	 *    呼ぶ側（コントローラ）がIDを受け取っておき、引数で渡す。
	 *
	 * すでに使われていないセットがあるときは作らない。
	 * 作りおきが2つあっても片方しか使われず、AI を1回分むだに呼ぶことになるため。
	 *
	 * @param userId 作りおきを用意する相手のID
	 */
	@Async
	public void prepareInBackground(Long userId) {

		// すでに使われていないセットがあれば、作らない
		boolean exists = setRepository
				.findFirstByUserIdAndStatusOrderByIdDesc(userId, ProposalStatus.OPEN)
				.isPresent();

		if (exists) {
			return;
		}

		try {
			generateSet(userId);
			System.out.println("### 事前生成 完了");

		} catch (RuntimeException e) {
			// 裏で動いている処理なので、失敗しても画面には影響させない。
			// 押されたときに、その場で作り直せばよい。
			System.out.println("### 事前生成 失敗: " + e.getMessage());
		}
	}

	/**
	 * スタートが押されたときに、3案のセットを返す。
	 *
	 * 作りおきがあればそれを使い、無ければその場で作る。
	 * 作りおきが古くなっている場合は破棄して作り直す。
	 *
	 * @return 表示する3案のセット
	 */
	@Transactional
	public ProposalSet start() {

		Long userId = userId();

		// Optional は「あるかもしれないし、無いかもしれない」入れ物。
		// null をそのまま返すより、受け取る側が確認を忘れにくい。
		Optional<ProposalSet> ready = setRepository
				.findFirstByUserIdAndStatusOrderByIdDesc(userId, ProposalStatus.OPEN);

		if (ready.isPresent()) {
			ProposalSet set = ready.get();

			// 古いセットは使わない。時間が経つと状況が変わっているため
			if (set.getRequestedAt().isBefore(LocalDateTime.now().minusMinutes(expireMinutes))) {
				set.setStatus(ProposalStatus.EXPIRED);
				setRepository.save(set);
				System.out.println("### 古いセットを破棄（EXPIRED）");
				return generateSet(userId);
			}

			// 迷い時間は「押してから選ぶまで」なので、押した時刻に更新する。
			// 作った時刻のままだと、作りおきしていた時間まで迷いに数えてしまう。
			set.setRequestedAt(LocalDateTime.now());
			setRepository.save(set);

			System.out.println("### 事前生成を使用（待ち時間 0秒）");
			return set;
		}

		// 作りおきが間に合わなかった場合は、その場で作る
		return generateSet(userId);
	}

	/**
	 * 3案を作って保存する。start と prepareInBackground の共通部分。
	 *
	 * AI の呼び出しに失敗した場合は固定3案に切り替える。
	 * ここで例外を外に出さないことが、「AIが落ちてもアプリは止まらない」を成り立たせている。
	 *
	 * 応答にかかった時間と、どこから来た3案かをログに出す。
	 * 速さの改善は、推測ではなく計測して判断する。
	 *
	 * @param userId 3案を作る相手のID
	 * @return 保存した3案のセット
	 */
	@Transactional
	public ProposalSet generateSet(Long userId) {

		Situation situation = situationJudge.judge(LocalTime.now());
		String prompt = promptBuilder.buildProposal(situation, taskService.findActive(userId));

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

		return start();
	}

	// ===== メソッド（内部で使うもの） =====

	/**
	 * いまログインしている利用者のIDを返す。
	 *
	 * 画面から呼ばれるメソッドだけがこれを使える。
	 * 裏で動く処理からは呼べないので、そちらは引数でIDを受け取る。
	 *
	 * @return 利用者のID
	 */
	private Long userId() {
		return userService.currentId();
	}

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
