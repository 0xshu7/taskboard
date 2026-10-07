package com.example.taskboard.service;

// ===== インポート =====
// 標準 … 時刻（Duration / LocalDateTime）、並べ替え（Comparator）、
//        組と並び（HashMap / Map / List）、くじ引き（ThreadLocalRandom）
// Spring … Service / Transactional、ログ（Logger / LoggerFactory）
// このアプリ … AI まわり（FallbackCards / PromptBuilder / ProposalGenerator）、
//              domain の各クラス、NotFoundException、SituationJudge、2つの Repository

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskboard.ai.FallbackCards;
import com.example.taskboard.ai.PromptBuilder;
import com.example.taskboard.ai.ProposalGenerator;
import com.example.taskboard.domain.Difficulty;
import com.example.taskboard.domain.Granularity;
import com.example.taskboard.domain.ProposalCard;
import com.example.taskboard.domain.ProposalSet;
import com.example.taskboard.domain.ProposalStatus;
import com.example.taskboard.domain.Task;
import com.example.taskboard.domain.User;
import com.example.taskboard.exception.NotFoundException;
import com.example.taskboard.logic.Situation;
import com.example.taskboard.logic.SituationJudge;
import com.example.taskboard.repository.ProposalCardRepository;
import com.example.taskboard.repository.ProposalSetRepository;

/**
 * スタートを押してから、3案を出し、選ぶまでを受け持つ。
 *
 * 担当することは3つ。3案を作る（generateSet）／スタートに応える（start）／
 * 選ばれたときの後始末（choose・retopic）。
 *
 * 【押されてから作る。作りおきはしない】
 *
 * 以前は画面を開いた時点で1セット作って待ち時間を消していたが、1回のスタートで
 * AI を2回呼ぶため、無料枠20回が実質10回になった。裏で動かす @Async も必要で、
 * ログイン情報が引き継がれない問題も付いてきた。代わりに3〜5秒待つ。
 * 失敗したら FallbackCards の固定3案に切り替え、出どころを ProposalSet.source に残す。
 *
 * 【AIを待っているあいだ、データベースの接続を握らない】
 *
 * start と retopic に @Transactional を付けない。付けると応答を待つ3〜5秒のあいだ、
 * 接続が他の画面に回せない。書き込みだけを ProposalStore にまとめ、一瞬だけ握る。
 *
 * 保存を別のクラスに置いたのは、ここに書いても効かないため。
 * **同じクラスの中から自分のメソッドを呼ぶと @Transactional は無視される。**
 * Spring は外から呼ばれたときだけ横取りできる。@Async が効かなかったのと同じ理由。
 * choose だけ残してあるのは、外部への通信が無く、3つを同時に動かすため。
 *
 * 【AI に渡す材料と、アプリが決めること】
 *
 * 未完了の一覧に加えて、完了したタスクと前回の3案も渡す。完了を渡さないと、
 * 前にやったものと同じ内容が「新しい案」として出てくる。
 *
 * 最初の3択の答えは渡さない。分野の点数・2件目の分数・3件目の選び方に使い、
 * AI には結果の分野だけが届く。文章で渡すと効きすぎて、タスク一覧を見ずに作り始めた。
 * 分野も同じで、以前は「違う話題に」と頼んでいたが守られないことがあった。
 *
 * 【記録と、そこからの重み付け】
 *
 * 時間帯と、選ばれたカードから生まれたタスクのIDを残し、次の提案に効かせる
 * （categoryBonus）。選択だけを数えると、選びやすいだけの提案に寄るので、終わった数を見る。
 * これは機械学習ではない。数えて割って足すだけで、学習するモデルは無い。
 *
 * 【他人のものに触れないようにする】
 *
 * 1件を取り出す入口は findSet と findCard の2つだけ。どちらも所有者を確かめてから
 * 返すので、このクラスの中でも repository.findById はその2つ以外では使わない。
 */
@Service
public class ProposalService {

	// ===== フィールド =====

	/** ログ。warn は AI の失敗、info は応答時間と出どころ、debug は重みと迷い時間 */
	private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

	/** AI に渡す「最近やったこと」の件数。全部渡すと古いものが効きすぎる */
	private static final int RECENT_DONE_LIMIT = 10;

	/**
	 * 完了率の分母へ足す数。1回出して1回終わっただけの分野が 100% になるのを防ぐ。
	 * まだ出していない分野も (0+1)/(0+3) = 0.33 で並ぶので、場合分けの if が要らない。
	 */
	private static final double BONUS_DENOMINATOR = 3.0;

	/** 起点が無いときに使う日付。列を足す前に答えた利用者は全期間を数える */
	private static final LocalDateTime EPOCH = LocalDateTime.of(2000, 1, 1, 0, 0);

	/** カードに振る番号。表示順であり、同時に重さの順でもある */
	private static final int FIRST_POSITION = 1;
	private static final int SECOND_POSITION = 2;
	private static final int PICKED_POSITION = 3;

	/**
	 * 3件目を「まとまった時間が要る」と見なす分数。短い見積もりのタスクまでそう呼ぶと、
	 * 画面で「5分」と食い違う。分数が無いときは、終わりが見えていないものとみなす。
	 */
	private static final int GROUP_MINUTES = 30;

	/** 提案セットの出し入れ。所有者を確かめる findSet だけが使う */
	private final ProposalSetRepository setRepository;

	/** 提案カードの出し入れ。取り出しと、分野ごとの集計に使う */
	private final ProposalCardRepository cardRepository;

	/** 提案の書き込み。ここだけがデータベースの接続を握る */
	private final ProposalStore proposalStore;

	/** 日時から「いまどんな状況か」と「どの分野を出すか」を決める */
	private final SituationJudge situationJudge;

	/** AI に送る文章を組み立てる */
	private final PromptBuilder promptBuilder;

	/** 3案を作る係。ai.mock の設定で、モックと本物が入れ替わる */
	private final ProposalGenerator generator;

	/** タスクの処理。手持ちのタスクを読み、選ばれたカードをタスクに変える */
	private final TaskService taskService;

	/** いまログインしている利用者を教えてくれる係 */
	private final UserService userService;

	// ===== 内部の入れ物 =====

	/**
	 * AI を呼んだ結果。2つまとめて返したいだけなので、ここだけの入れ物にしている。
	 * @param cards  3枚のカード
	 * @param source どこから来たか（AI / FALLBACK / MOCK）
	 */
	private record Generated(List<ProposalCard> cards, String source) {
	}

	// ===== コンストラクタ =====

	/** 必要な部品を受け取る。外から渡す形にしておくと差し替えが効く */
	public ProposalService(ProposalSetRepository setRepository,
			ProposalCardRepository cardRepository,
			ProposalStore proposalStore,
			SituationJudge situationJudge,
			PromptBuilder promptBuilder,
			ProposalGenerator generator,
			TaskService taskService,
			UserService userService) {

		this.setRepository = setRepository;
		this.cardRepository = cardRepository;
		this.proposalStore = proposalStore;
		this.situationJudge = situationJudge;
		this.promptBuilder = promptBuilder;
		this.generator = generator;
		this.taskService = taskService;
		this.userService = userService;
	}

	// ===== メソッド =====

	/**
	 * スタートが押されたときに、3案のセットを返す。押されるたびに新しく作る。
	 * @return 表示する3案のセット
	 */
	public ProposalSet start() {
		return generateSet(List.of());
	}

	/**
	 * 3案を作って保存する。材料を集める → 文章を作る → AIを呼ぶ → 保存する、の4段階。
	 * 後ろの3つは別のメソッドに切り出してあるので、ここには流れだけが残る。
	 *
	 * 前回の3案を受け取るのは「別の話題にする」のときだけ。同じ題名（AI に渡す）と
	 * 同じ分野（SituationJudge に渡す）を外すために使う。
	 *
	 * @param previousCards 前回の3案。スタートのときは空
	 * @return 保存した3案のセット
	 */
	public ProposalSet generateSet(List<ProposalCard> previousCards) {

		// 最初の3択の答えを持っている。未回答なら中身が null
		User user = userService.current();

		// 前回の1件目の分野を外す。分野は3つしかないので、外すのは1つだけ
		String avoidCategory = previousCards.isEmpty()
				? null
				: previousCards.get(0).getCategory();

		List<String> excludeTitles = previousCards.stream()
				.map(ProposalCard::getTitle)
				.toList();

		// 日時を渡すのは、時間帯だけでなく曜日も見るため。最後が記録から出した重み
		Situation situation = situationJudge.judge(
				LocalDateTime.now(), user.getMainUse(), user.getFocusLevel(),
				avoidCategory, categoryBonus(user));

		// 3つ目の案に使うタスクを1件選ぶ。新しいタスクが増え続けるのを防ぎ、
		// 止まっているものを掘り起こす。findActive は本人のぶんだけを返す
		List<Task> activeTasks = taskService.findActive();
		Task pickedTask = pickOne(activeTasks, user.getOrderPref());

		// 完了したものも材料に入れる。無いと、前にやったものと同じ案が出てくる
		List<Task> doneTasks = recentDone();

		String prompt = promptBuilder.buildProposal(
				situation, activeTasks, pickedTask, doneTasks, excludeTitles);

		// ここで3〜5秒待つ。接続は握っていない
		Generated generated = callGenerator(prompt, situation, pickedTask);

		numberCards(generated.cards(), pickedTask, generated.source(), situation);

		return proposalStore.save(
				newSet(user, situation, prompt, generated.source()),
				generated.cards());
	}

	/**
	 * AI を呼んで3案を受け取る。失敗したら固定3案に切り替える。
	 *
	 * ここで例外を外に出さないことが、「AIが落ちてもアプリは止まらない」を成り立たせている。
	 * try に入れるのは generator.generate だけ。広げると別の失敗まですり替わり、
	 * 原因が見えなくなる。応答時間も残す。速さは計測して判断する。
	 *
	 * @param prompt     送る文章
	 * @param situation  いまの状況。固定3案を作るときに使う
	 * @param pickedTask 3件目に使うタスク。無ければ null
	 * @return 3案と、その出どころ
	 */
	private Generated callGenerator(String prompt, Situation situation, Task pickedTask) {

		List<ProposalCard> cards;
		String source;

		long begin = System.currentTimeMillis();

		try {
			cards = generator.generate(prompt);
			source = generator.sourceName();

		} catch (RuntimeException e) {
			log.warn("AI 失敗のためフォールバック: {}", e.getMessage());
			cards = FallbackCards.create(situation, pickedTask);
			source = "FALLBACK";
		}

		log.info("AI 応答 {} ms / source={}", System.currentTimeMillis() - begin, source);

		return new Generated(cards, source);
	}

	/**
	 * 保存するセットを組み立てる。まだ保存はしない。
	 *
	 * AI を呼んだあとに呼ばれるので、requestedAt は「3案が出そろった時刻」になる。
	 * 待っていた3〜5秒を迷い時間に数えないための形。
	 *
	 * @param user      いまの利用者
	 * @param situation いまの状況。時間帯を残すために使う
	 * @param prompt    送った文章。そのまま残す
	 * @param source    どこから来た3案か
	 * @return まだ保存していないセット
	 */
	private ProposalSet newSet(User user, Situation situation, String prompt, String source) {

		ProposalSet set = new ProposalSet();
		set.setUserId(user.getId());
		set.setRequestedAt(LocalDateTime.now());
		set.setStatus(ProposalStatus.OPEN);
		set.setSource(source);
		set.setTimeOfDay(situation.getTimeOfDay());
		set.setPromptText(prompt);

		return set;
	}

	/**
	 * 3枚のカードに番号を振り、重さを入れる。まだ保存はしない。番号が表示順であり、
	 * 同時に重さの役割も表す。
	 *
	 * 粒度と難易度は AI の答えを使わず、ここで入れ直す。プロンプトで指定していないので、
	 * 3〜10分の案に HIGH が付くことがあり、画面に並ぶ値が食い違う。
	 *
	 * 3枚目だけ、元になったタスクを覚えておく。選ばれたとき新しく作らずに進行中にするため。
	 * 難易度と目安時間も元の値を入れ、粒度はその分数から決める（granularityOf）。
	 * モックだけ除くのは、状況も指定タスクも受け取らず、題名と中身が食い違うため。
	 *
	 * @param cards      3枚のカード
	 * @param pickedTask 3件目に使うタスク。無ければ null
	 * @param source     どこから来た3案か
	 */
	private void numberCards(List<ProposalCard> cards, Task pickedTask, String source,
			Situation situation) {

		int position = FIRST_POSITION;

		for (ProposalCard card : cards) {
			card.setPosition(position);

			// 1枚目はすぐ終わる軽いもの、2枚目は今日中に終わるもの
			if (position == FIRST_POSITION) {
				card.setCategory(situation.getFirstCategory());
				card.setGranularity(Granularity.FIVE_MIN);
				card.setDifficulty(Difficulty.LOW);

			} else if (position == SECOND_POSITION) {
				card.setCategory(situation.getSecondCategory());
				card.setGranularity(Granularity.TODAY);
				card.setDifficulty(Difficulty.MID);
			}

			if (position == PICKED_POSITION && pickedTask != null && !"MOCK".equals(source)) {
				card.setSourceTaskId(pickedTask.getId());
				card.setTitle(pickedTask.getTitle());

				if (pickedTask.getDifficulty() != null) {
					card.setDifficulty(pickedTask.getDifficulty());
				}
				if (pickedTask.getEstimatedMinutes() != null) {
					card.setEstimatedMinutes(pickedTask.getEstimatedMinutes());
				}

				// 分数を入れ替えたあとで決める。
				// 元のタスクに見積もりが無ければ、AI が付けた分数がそのまま残るため
				card.setGranularity(granularityOf(card.getEstimatedMinutes()));
			}
			position++;
		}
	}

	/**
	 * 3件目の粒度を、カードに入っている分数から決める。1件目と2件目は決め打ちだが、
	 * 3件目は元のタスクを進めるものなので、その見積もりに合わせる。
	 *
	 * @param minutes カードの目安時間。入っていないこともある
	 * @return 30分以上か、分からなければ GROUP。それより短ければ TODAY
	 */
	private Granularity granularityOf(Integer minutes) {

		if (minutes == null || minutes >= GROUP_MINUTES) {
			return Granularity.GROUP;
		}
		return Granularity.TODAY;
	}

	// ===== メソッド（取り出す） =====

	/**
	 * セットを1件取り出す。他人のセットは取り出せない。
	 *
	 * 画面に出ていなくても URL の番号を書き換えれば手が届き、3枚目には本人の
	 * タスク名が入る。見えないことと触れないことは別で、確認はここに1か所だけ置く。
	 *
	 * 他人のものも「見つかりません」と同じにしている。分けると、その番号の
	 * セットが存在することを教えることになる。NotFoundException は 404 で返る。
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
	 * 先に findSet を通して所有者を確かめる。setId だけで引くと他人の中身が読める。
	 *
	 * @param setId セットのID
	 * @return 1番目から順に並んだカード
	 */
	public List<ProposalCard> findCards(Long setId) {
		return findCards(findSet(setId));
	}

	/**
	 * 所有者を確認ずみのセットから、3枚のカードを取り出す。引数がセットそのものなので、
	 * 確認を飛ばして呼べない。手に入れる方法が findSet だけのため。
	 *
	 * @param set 所有者を確認ずみのセット
	 * @return 1番目から順に並んだカード
	 */
	public List<ProposalCard> findCards(ProposalSet set) {
		return cardRepository.findByProposalSetIdOrderByPositionAsc(set.getId());
	}

	/**
	 * カードを1枚取り出す。他人のカードは取り出せない。
	 *
	 * 確認は2段階。セットが本人のものか（findSet 済み）と、そのカードがこのセットのものか。
	 * IDが別々に送られてくるので、自分のセットIDと他人のカードIDを組み合わせられるため。
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
	 * @Transactional を残しているのは、タスクだけができて印が付いていない状態を作らないため。
	 * 選ばれなかった2枚も残す。できたタスクのIDを控えるのは、完了したかを数えるため。
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

		// 元になったタスクがあるなら、新しく作らずに進行中にする。
		// ここで作ると、同じ内容のタスクが2件並ぶ
		if (card.getSourceTaskId() == null) {
			Task task = taskService.createFromCard(card);
			card.setResultTaskId(task.getId());
		} else {
			taskService.startExisting(card.getSourceTaskId(), card.getDetail());
			card.setResultTaskId(card.getSourceTaskId());
		}

		// 印と、できたタスクのIDを、まとめて1回で保存する
		proposalStore.saveCard(card);

		// セットを決定済みにする。decidedAt が迷い時間の計算に使われる
		proposalStore.close(set, ProposalStatus.CHOSEN);

		logHesitation(set);
	}

	/**
	 * 「別の案を見る」を押したとき。いまのセットを閉じて、新しく作り直す。
	 *
	 * 閉じる前に、画面に出ている3枚をそのまま次へ渡す。控えずに呼び直すと、
	 * 同じ材料・同じ指示で呼ぶことになり、似たものが返ってきて当然だった。
	 *
	 * @param setId 閉じるセットのID
	 * @return 新しく用意したセット
	 * @throws NotFoundException セットが無いか、他人のものだったとき
	 */
	public ProposalSet retopic(Long setId) {

		ProposalSet set = findSet(setId);

		// 確認ずみのセットをそのまま渡す。所有者の確認は1回で済む
		List<ProposalCard> shown = findCards(set);

		proposalStore.close(set, ProposalStatus.RETOPIC);

		logHesitation(set);

		return generateSet(shown);
	}

	// ===== メソッド（内部で使うもの） =====

	/**
	 * 分野ごとの完了率を出す。SituationJudge の点数に足される。
	 *
	 * 数え方は「(終わった数 + 1) / (出した数 + 3)」。数えるのは質問に答えた時刻より後だけで、
	 * 答えなおすと起点が動く。記録そのものは消さない。
	 *
	 * 選ばれただけでは上がらず、終わって初めて上がる。選択だけを報酬にすると、
	 * 楽そうなものばかり出て誰も動かない形になりうる。目的は動けること。
	 *
	 * @param user いまの利用者
	 * @return 分野ごとの重み。記録が無い分野は入らない
	 */
	private Map<String, Double> categoryBonus(User user) {

		LocalDateTime since = user.getProfileSetAt() != null
				? user.getProfileSetAt()
				: EPOCH;

		Map<String, Double> bonus = new HashMap<>();

		for (Object[] row : cardRepository.countByCategory(user.getId(), since)) {

			String category = (String) row[0];
			double shown = ((Number) row[1]).doubleValue();
			double done = ((Number) row[2]).doubleValue();

			bonus.put(category, (done + 1) / (shown + BONUS_DENOMINATOR));
		}

		log.debug("分野の重み {}", bonus);

		return bonus;
	}

	/**
	 * 迷い時間をログに出す。3案が出てから決めるまでの秒数。
	 *
	 * 何秒からを迷いと見なすかは決めていない。秒数のまま残すだけで、
	 * いまはプロンプトにも画面にも使っていない。
	 * 提案の材料に入れるかどうかは、記録が溜まってから決める。
	 *
	 * @param set 決定済みのセット
	 */
	private void logHesitation(ProposalSet set) {

		if (set.getDecidedAt() == null) {
			return;
		}

		// Duration.between は2つの時刻の差を表す。getSeconds で秒数になる
		long seconds = Duration.between(set.getRequestedAt(), set.getDecidedAt()).getSeconds();

		log.debug("迷い時間 = {}秒 / 結果 = {}", seconds, set.getStatus());
	}

	/**
	 * 最近やったことを、新しい順に少しだけ取り出す。findDone が本人のぶんだけを返す。
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
	 * 3つ目の案に使うタスクを1件選ぶ。EASY は見積りの短い順、DUE は締切の近い順、
	 * OLD は登録の古い順、未回答はくじ引き。
	 *
	 * 値が入っていないタスクは後ろに回す。先頭に置くと、書き忘れただけのものが毎回出る。
	 * 1件も無ければ null。呼び出し側は null のときプロンプトの欄ごと出さない。
	 *
	 * @param tasks     未完了のタスク
	 * @param orderPref すすめ方の好み。未回答なら null
	 * @return 選ばれた1件。無ければ null
	 */
	private Task pickOne(List<Task> tasks, String orderPref) {

		if (tasks.isEmpty()) {
			return null;
		}

		// switch は null を受け取れないので、空文字に置き換えてから渡す
		Comparator<Task> order = switch (orderPref == null ? "" : orderPref) {

			case "EASY" -> Comparator.comparing(Task::getEstimatedMinutes,
					Comparator.nullsLast(Comparator.naturalOrder()));

			case "DUE" -> Comparator.comparing(Task::getDueDate,
					Comparator.nullsLast(Comparator.naturalOrder()));

			case "OLD" -> Comparator.comparing(Task::getCreatedAt,
					Comparator.nullsLast(Comparator.naturalOrder()));

			// 未回答。くじ引きにする
			default -> null;
		};

		if (order == null) {
			// ThreadLocalRandom.current() は、いま動いているスレッド用のくじ引きを返す。
			// Random を1つ持つと、同時に使う人数ぶんの取り合いになる
			return tasks.get(ThreadLocalRandom.current().nextInt(tasks.size()));
		}

		return tasks.stream().min(order).orElse(null);
	}
}