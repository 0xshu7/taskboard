package com.example.taskboard.service;

// ===== インポート =====
// java.time.Duration      … 2つの時刻の差。迷い時間の計算に使う
// java.time.LocalDateTime … 日付＋時刻。作った時刻・決めた時刻、状況の判断に使う
// java.util.List          … リストの型
// java.util.Random        … くじ引き
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
// NotFoundException      … 見つからないときの例外。404で返る
// Situation              … いまの状況と、出す分野
// SituationJudge         … 日時から状況と分野を決める
// ProposalCardRepository … カードの出し入れ
// ProposalSetRepository  … セットの出し入れ

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskboard.ai.FallbackCards;
import com.example.taskboard.ai.PromptBuilder;
import com.example.taskboard.ai.ProposalGenerator;
import com.example.taskboard.domain.ProposalCard;
import com.example.taskboard.domain.ProposalSet;
import com.example.taskboard.domain.ProposalStatus;
import com.example.taskboard.domain.Task;
import com.example.taskboard.exception.NotFoundException;
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
 *
 * 【AI に渡す材料】
 *
 * 未完了の一覧だけでなく、完了したタスクと、前回出した3案も渡す。
 *
 * 未完了は「まだ手を付けていないもの」なので偏る。
 * そして完了を渡さないと、AI は過去を知らないまま「新しい案」を作ることになり、
 * 前にやったものと同じ内容が「あたらしい」として出てくる。
 *
 * 【分野はアプリが決める】
 *
 * 1件目と2件目にどの分野を出すかは SituationJudge が決め、
 * プロンプトにはその結果だけを書く。
 * 以前は「3案は違う話題に」とAIに頼んでいたが、守られないことがあった。
 * アプリが決めてしまえば、守る／守らないの問題が消える。
 *
 * 「別の話題にする」を押したときは、前回の1件目の分野も外す。
 * そうしないと、時刻が変わっていないので同じ分野が出続ける。
 *
 * 【他人のものに触れないようにする】
 *
 * 1件を取り出す入口は2つだけにしてある。
 *
 * 　セット … findSet
 * 　カード … findCard
 *
 * どちらも所有者を確かめてから返す。
 * repository を直接呼ぶと、その確認を素通りすることになるので、
 * このクラスの中でも repository.findById は上の2つ以外では使わない。
 *
 * TaskService.findById と同じ作りにしてある。
 * 同じ形の穴を、片方だけ塞いで終わりにしないため。
 */
@Service
public class ProposalService {

	// ===== フィールド =====

	/**
	 * 「最近やったこと」として AI に渡す件数。
	 *
	 * 全部渡すと、続けるほどプロンプトが長くなり、
	 * 古いものが効きすぎて提案の幅が狭くなる。
	 */
	private static final int RECENT_DONE_LIMIT = 10;

	/** 提案セットの出し入れ */
	private final ProposalSetRepository setRepository;

	/** 提案カードの出し入れ */
	private final ProposalCardRepository cardRepository;

	/** 日時から「いまどんな状況か」と「どの分野を出すか」を決める */
	private final SituationJudge situationJudge;

	/** AI に送る文章を組み立てる */
	private final PromptBuilder promptBuilder;

	/**
	 * 3つ目に使うタスクを選ぶためのくじ引き。
	 *
	 * 呼ぶたびに new すると、短い間に呼ばれたとき同じ値が続くことがある。
	 * 1つ作って使い回す。
	 */
	private final Random random = new Random();

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
		return generateSet(List.of());
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
	 * 前回の3案を受け取るのは「別の話題にする」のときだけ。
	 * そこから2つのことを決める。
	 * 　・同じ題名を出さない（AI に渡す）
	 * 　・同じ分野を出さない（SituationJudge に渡す）
	 *
	 * 題名の一覧ではなくカードのまま受け取っているのは、
	 * 題名も分野も同じところから取れて、渡すものが1つで済むため。
	 *
	 * @param previousCards 前回の3案。スタートのときは空
	 * @return 保存した3案のセット
	 */
	@Transactional
	public ProposalSet generateSet(List<ProposalCard> previousCards) {

		Long userId = userService.currentId();

		// 前回の1件目の分野。次はここを外す。
		// 外すのは1つだけ。分野は3つしかないので、2つ外すと選ぶ余地が消える
		String avoidCategory = previousCards.isEmpty()
				? null
				: previousCards.get(0).getCategory();

		List<String> excludeTitles = previousCards.stream()
				.map(ProposalCard::getTitle)
				.toList();

		// 日時を渡すのは、時間帯だけでなく曜日も見るため。
		//
		// 2つ目と3つ目は初回の3択の答え（主な用途・集中時間）。
		// まだ質問画面が無いので null を渡している。
		// null のときは、分野は時間帯と曜日だけで決まり、2件目は20〜60分になる。
		Situation situation = situationJudge.judge(
				LocalDateTime.now(), null, null, avoidCategory);

		// 3つ目の案にそのまま使うタスクを、手持ちから1件だけ選ぶ。
		//
		// 押すたびに新しいタスクが増え続けるのを防ぎ、
		// 登録したまま止まっているものを掘り起こすため。
		//
		// 選び方はくじ引き。締切順などにすると、同じものばかり出て埋もれ直す。
		// 1件も無ければ null で、そのときは3案とも新しい案になる。
		//
		// findActive はログイン中の本人のぶんだけを返すので、
		// ここに他人のタスクが混ざることはない。AIへ送る材料も本人のものだけになる。
		List<Task> activeTasks = taskService.findActive();
		Task pickedTask = pickOne(activeTasks);

		// 完了したものも材料に入れる。
		// これが無いと、AI は過去を知らないまま「新しい案」を作ることになり、
		// 前にやったものと同じ内容が出てくる。
		List<Task> doneTasks = recentDone();

		String prompt = promptBuilder.buildProposal(
				situation, activeTasks, pickedTask, doneTasks, excludeTitles);

		List<ProposalCard> cards;
		String source;

		long begin = System.currentTimeMillis();

		// この catch が受けるのは、AI の呼び出しの失敗だけ。
		// try の中には generator.generate しか入れていないので、
		// 所有者の確認で投げる NotFoundException がここに紛れ込むことはない。
		// 範囲を広げると、見つからなかったことまで「AIの失敗」として
		// 固定3案にすり替わり、原因が見えなくなる。
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

			// 3枚目だけ、元になったタスクを覚えておく。
			// 選ばれたとき、新しく作らずにそのタスクを進行中にするため。
			//
			// 本物のAIのときだけ紐づける。
			// モックもフォールバックもプロンプトを読まないので、
			// 指定したタスクとは無関係な内容を返す。
			// それを紐づけると、関係のないヒントが本人のタスクに入ってしまう。
			if (position == 3 && pickedTask != null && "AI".equals(source)) {
				card.setSourceTaskId(pickedTask.getId());
				card.setTitle(pickedTask.getTitle());
			}

			cardRepository.save(card);
			position++;
		}

		return set;
	}

	// ===== メソッド（取り出す） =====

	/**
	 * セットを1件取り出す。
	 *
	 * 他人のセットは取り出せない。
	 *
	 * 画面に出ていなくても、URL の番号を書き換えれば手が届く。
	 * /proposals/999 の 999 を他人のセットにすれば、その3案が読めてしまう。
	 * 3枚目には本人のタスク名がそのまま入るので、他人のタスクを読むことにもなる。
	 *
	 * 見えないことと、触れないことは別。
	 * セットを扱うすべての処理がこのメソッドを通るので、確認はここに1か所だけ置く。
	 *
	 * 他人のものだったときも「見つかりません」と同じ扱いにしている。
	 * 「あなたのものではありません」と返すと、
	 * その番号のセットが存在することを相手に教えることになる。
	 *
	 * NotFoundException は 404 で返る。
	 * 以前は IllegalArgumentException を投げていたが、それは 500（サーバーの不具合）になる。
	 * 壊れていないのに壊れたと言うと、本当に壊れたときに見分けがつかない。
	 *
	 * @param setId セットのID
	 * @return 見つかったセット
	 * @throws NotFoundException 無いか、他人のものだったとき
	 */
	public ProposalSet findSet(Long setId) {

		ProposalSet set = setRepository.findById(setId)
				.orElseThrow(() -> new NotFoundException("提案が見つかりません: " + setId));

		if (!set.getUserId().equals(userService.currentId())) {
			throw new NotFoundException("提案が見つかりません: " + setId);
		}
		return set;
	}

	/**
	 * セットに属する3枚のカードを、表示順で取り出す。
	 *
	 * 先に findSet を通して所有者を確かめている。
	 * カードを setId だけで引くと、他人のセットの中身がそのまま読めてしまう。
	 *
	 * @param setId セットのID
	 * @return 1番目から順に並んだカード
	 * @throws NotFoundException セットが無いか、他人のものだったとき
	 */
	public List<ProposalCard> findCards(Long setId) {

		findSet(setId);

		return cardRepository.findByProposalSetIdOrderByPositionAsc(setId);
	}

	/**
	 * カードを1枚取り出す。
	 *
	 * 他人のカードは取り出せない。
	 *
	 * 確認は2段階。
	 * 1. セットが本人のものか（findSet を通って渡ってきている）
	 * 2. そのカードが、このセットのものか
	 *
	 * 2つ目が要るのは、セットとカードのIDが別々に送られてくるため。
	 * 自分のセットIDと、他人のカードIDを組み合わせて送ることができる。
	 * セットだけ確かめて満足すると、そこが空く。
	 *
	 * @param set    所有者を確認ずみのセット
	 * @param cardId カードのID
	 * @return 見つかったカード
	 * @throws NotFoundException 無いか、そのセットのものでなかったとき
	 */
	private ProposalCard findCard(ProposalSet set, Long cardId) {

		ProposalCard card = cardRepository.findById(cardId)
				.orElseThrow(() -> new NotFoundException("カードが見つかりません: " + cardId));

		if (!card.getProposalSetId().equals(set.getId())) {
			throw new NotFoundException("カードが見つかりません: " + cardId);
		}
		return card;
	}

	// ===== メソッド（選ばれたときの後始末） =====

	/**
	 * カードを1つ選んだとき。タスクを作り、セットを決定済みにする。
	 *
	 * 選ばれなかった2枚も消さずに残す。
	 * 「何を出して、何が選ばれなかったか」が、提案の質を測る材料になるため。
	 *
	 * @param setId  セットのID
	 * @param cardId 選ばれたカードのID
	 * @throws NotFoundException セットかカードが無いか、他人のものだったとき
	 */
	@Transactional
	public void choose(Long setId, Long cardId) {

		ProposalSet set = findSet(setId);
		ProposalCard card = findCard(set, cardId);

		// 選ばれた印を付ける
		card.setChosen(true);
		cardRepository.save(card);

		// セットを決定済みにする。decidedAt が迷い時間の計算に使われる
		set.setStatus(ProposalStatus.CHOSEN);
		set.setDecidedAt(LocalDateTime.now());
		setRepository.save(set);

		// タスクにする。
		// 元になったタスクがあるなら、新しく作らずにそれを進行中にする。
		// ここで新しく作ると、同じ内容のタスクが2件並ぶことになる。
		if (card.getSourceTaskId() == null) {
			taskService.createFromCard(card);
		} else {
			taskService.startExisting(card.getSourceTaskId(), card.getDetail());
		}

		printHesitation(set);
	}

	/**
	 * 「別の案を見る」を押したとき。いまのセットを閉じて、新しく作り直す。
	 *
	 * 閉じたセットも記録として残す。
	 * 何が選ばれなかったかは、提案を良くするための手がかりになる。
	 *
	 * 閉じる前に、いま画面に出ている3枚をそのまま次へ渡す。
	 * 題名も分野もそこから取れるので、渡すものは1つで済む。
	 *
	 * 控えずに呼び直すと、同じ材料・同じ指示で呼ぶことになるので、
	 * 似たものが返ってきて当然だった。
	 *
	 * @param setId 閉じるセットのID
	 * @return 新しく用意したセット
	 * @throws NotFoundException セットが無いか、他人のものだったとき
	 */
	@Transactional
	public ProposalSet retopic(Long setId) {

		ProposalSet set = findSet(setId);

		// findCards は中で findSet を通るので、ここでも所有者は確かめられている
		List<ProposalCard> shown = findCards(setId);

		set.setStatus(ProposalStatus.RETOPIC);
		set.setDecidedAt(LocalDateTime.now());
		setRepository.save(set);

		printHesitation(set);

		return generateSet(shown);
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

	/**
	 * 最近やったことを、新しい順に少しだけ取り出す。
	 *
	 * findDone は本人のぶんだけを新しい順に返すので、
	 * 先頭から必要な数だけ取ればよい。
	 *
	 * @return 完了したタスク。多くても RECENT_DONE_LIMIT 件
	 */
	private List<Task> recentDone() {

		List<Task> done = taskService.findDone();

		if (done.size() <= RECENT_DONE_LIMIT) {
			return done;
		}
		return done.subList(0, RECENT_DONE_LIMIT);
	}

	/**
	 * 未完了のタスクから1件、ランダムに選ぶ。
	 *
	 * 1件も無ければ null を返す。
	 * 呼び出し側は null のときプロンプトの欄ごと出さないので、
	 * ここで空のリストを返す形にはしていない。
	 *
	 * @param tasks 未完了のタスク
	 * @return 選ばれた1件。無ければ null
	 */
	private Task pickOne(List<Task> tasks) {

		if (tasks.isEmpty()) {
			return null;
		}

		// nextInt(n) は 0 以上 n 未満の整数を返す。
		// リストの番号は0から始まるので、そのまま添字に使える
		return tasks.get(random.nextInt(tasks.size()));
	}
}