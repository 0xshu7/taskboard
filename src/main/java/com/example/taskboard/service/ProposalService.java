package com.example.taskboard.service;

// ===== インポート =====
// java.time.Duration      … 2つの時刻の差。迷い時間の計算に使う
// java.time.LocalDateTime … 日付＋時刻。作った時刻・決めた時刻、状況の判断に使う
// java.util.Comparator    … 並べ替えの決まり。3件目の選び方に使う
// java.util.HashMap       … 「見出し → 値」の組の実装。分野ごとの重みに使う
// java.util.List          … リストの型
// java.util.Map           … 「見出し → 値」の組の型
//
// java.util.concurrent.ThreadLocalRandom
//   … くじ引き。スレッドごとに別のものが用意されるので、取り合いが起きない
//
// 以下はログの部品。Spring Boot に最初から入っているので、pom.xml に足すものは無い。
// Logger        … ログを出す道具
// LoggerFactory … そのクラス用の Logger を作る
//
// 以下は Spring の部品。
// Service       … 処理のまとまりです、という印。Spring が管理下に置く
// Transactional … メソッドの途中で失敗したら、その中の保存をすべて取り消す
//
// 以下はこのアプリの部品。
// FallbackCards          … AIが失敗したときに出す、決まった3案
// PromptBuilder          … AIに送る文章を組み立てる
// ProposalGenerator      … 3案を作る係。モックと本物を差し替えられる
// Difficulty             … 難易度
// Granularity            … タスクの粒度
// ProposalCard           … 提案カード1枚
// ProposalSet            … 提案1セット
// ProposalStatus         … セットの状態
// Task                   … タスク1件
// User                   … 利用者1人。最初の3択の答えを持っている
// NotFoundException      … 見つからないときの例外。404で返る
// Situation              … いまの状況と、出す分野
// SituationJudge         … 日時から状況と分野を決める
// ProposalCardRepository … カードの出し入れ
// ProposalSetRepository  … セットの出し入れ

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
 * 【AIを待っているあいだ、データベースの接続を握らない】
 *
 * 以前は start と retopic に @Transactional が付いていた。
 * generateSet はその中から呼ばれるので、
 * AI の応答を待つ3〜5秒のあいだ、接続を握ったままになっていた。
 * 握っているあいだ、その接続は他の画面に回せない。
 *
 * いまは、その2つから @Transactional を外してある。
 * 材料を集める読み出しは、それぞれが短く済ませる。
 * 書き込みだけを ProposalStore にまとめ、保存する一瞬だけ握る。
 *
 * 保存を別のクラスに置いたのは、ここに書いても効かないため。
 * **同じクラスの中から自分のメソッドを呼ぶと @Transactional は無視される。**
 * Spring は外から呼ばれたときだけ横取りできる。@Async が効かなかったのと同じ理由。
 *
 * choose だけは @Transactional を残している。
 * 外部への通信が無く、カードとタスクとセットの3つを同時に動かすため。
 * こちらは controller から直接呼ばれるので、きちんと効く。
 *
 * 【generateSet の中身を4つに分けている】
 *
 * 材料を集める → 文章を作る → AIを呼ぶ → 保存する、の4段階で読める。
 * 最初の2つは上から下へ読めばいいだけなので generateSet に残し、
 * 残りを callGenerator / newSet / numberCards に切り出した。
 * どこで何が起きるかを、メソッドの名前で分かるようにするため。
 *
 * 【AI に渡す材料】
 *
 * 未完了の一覧だけでなく、完了したタスクと、前回出した3案も渡す。
 *
 * 未完了は「まだ手を付けていないもの」なので偏る。
 * そして完了を渡さないと、AI は過去を知らないまま「新しい案」を作ることになり、
 * 前にやったものと同じ内容が「あたらしい」として出てくる。
 *
 * 【最初の3択の答えは、AI に渡さない】
 *
 * 主な用途・すすめ方・集中時間の3つは、このクラスとロジックだけが読む。
 * 　主な用途   → 分野の点数（SituationJudge）
 * 　集中時間   → 2件目の分数（SituationJudge）
 * 　すすめ方   → 3件目に使うタスクの選び方（pickOne）
 *
 * AI には「その結果どの分野を出すか」だけが届く。
 * 文章でプロフィールを渡すと効きすぎて、タスク一覧を見ずに
 * そこから中身を作り始めることがあった。
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
 * 【記録と、そこからの重み付け】
 *
 * このときの時間帯を ProposalSet に、
 * 選ばれたカードから生まれたタスクのIDを ProposalCard に保存している。
 * あとで「時間帯ごとに、どの分野が出されて、選ばれて、実際に終わったか」を
 * 数えるため。選択だけを数えると、選びやすいだけの提案に寄ってしまう。
 *
 * その記録を、次の提案に効かせている（categoryBonus）。
 * 分野ごとの完了率を SituationJudge の点数に足すので、
 * 終わらせている分野が出やすくなる。
 *
 * 数えるのは「質問に答えた時刻より後」だけ。
 * 答えなおすと起点が動くので、そこで評価もやりなおる。
 * 記録そのものは消さない。
 * 「やりなおせます」という言葉と、実際の動きをそろえるため。
 *
 * これは機械学習ではない。数えて割って足すだけで、学習するモデルは無い。
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
	 * ログを出す道具。
	 *
	 * 重さは3つに分けてある。
	 * 　warn  … AI が落ちてフォールバックに切り替わった
	 * 　info  … AI の応答にかかった時間と、どこから来た3案か
	 * 　debug … 分野の重みと、迷い時間
	 *
	 * 下の2つは提案の中身を追うためのもので、普段は要らない。
	 * 見たいときは設定で debug に下げる。
	 */
	private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

	/**
	 * 「最近やったこと」として AI に渡す件数。
	 *
	 * 全部渡すと、続けるほどプロンプトが長くなり、
	 * 古いものが効きすぎて提案の幅が狭くなる。
	 */
	private static final int RECENT_DONE_LIMIT = 10;

	/**
	 * 完了率を出すときに分母へ足す数。
	 *
	 * 1回出して1回終わっただけの分野が「完了率100%」になって
	 * 独占するのを防ぐため。
	 * この値のおかげで、まだ出していない分野も (0+1)/(0+3) = 0.33 になり、
	 * 3分野が同じ値で並ぶ。データが無いうちは差が出ないので、
	 * 場合分けの if を書かずに済む。
	 */
	private static final double BONUS_DENOMINATOR = 3.0;

	/**
	 * 記録が1件も無いときに使う起点。
	 *
	 * profile_set_at の列を足す前に答えた利用者は、この値が空になる。
	 * その場合は全期間を数える。
	 */
	private static final LocalDateTime EPOCH = LocalDateTime.of(2000, 1, 1, 0, 0);

	/** 1枚目のカードに振られる番号 */
	private static final int FIRST_POSITION = 1;

	/** 2枚目のカードに振られる番号 */
	private static final int SECOND_POSITION = 2;

	/** 3枚目のカードに振られる番号 */
	private static final int PICKED_POSITION = 3;

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

	// ===== 内部の入れ物 =====

	/**
	 * AI を呼んだ結果。3案と、その出どころ。
	 *
	 * 2つまとめて返したいだけなので、このクラスの中だけの入れ物にしている。
	 * record は「値を持つだけの型」を1行で書くための書き方で、
	 * 取り出しは cards()・source() のように呼ぶ。
	 *
	 * @param cards  3枚のカード
	 * @param source どこから来たか（AI / FALLBACK / MOCK）
	 */
	private record Generated(List<ProposalCard> cards, String source) {
	}

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
	 * スタートが押されたときに、3案のセットを返す。
	 *
	 * 押されるたびに新しく作る。前のセットは探さない。
	 * 押さずに離れたセットは OPEN のまま残るが、それも記録として意味がある。
	 *
	 * @Transactional は付けない。
	 * 途中で AI を呼ぶので、付けるとその待ち時間ぶん接続を握ることになる。
	 *
	 * @return 表示する3案のセット
	 */
	public ProposalSet start() {
		return generateSet(List.of());
	}

	/**
	 * 3案を作って保存する。
	 *
	 * 上から順に、材料を集める → 文章を作る → AIを呼ぶ → 保存する、の4段階。
	 * 後ろの3つは別のメソッドに切り出してあるので、ここには流れだけが残る。
	 *
	 * 保存だけが ProposalStore を通る。
	 * 材料を集める読み出しは、それぞれが短く終わるので、まとめて囲まない。
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
	public ProposalSet generateSet(List<ProposalCard> previousCards) {

		// 最初の3択の答えを持っている。未回答なら中身が null
		User user = userService.current();

		// 前回の1件目の分野。次はここを外す。
		// 外すのは1つだけ。分野は3つしかないので、2つ外すと選ぶ余地が消える
		String avoidCategory = previousCards.isEmpty()
				? null
				: previousCards.get(0).getCategory();

		List<String> excludeTitles = previousCards.stream()
				.map(ProposalCard::getTitle)
				.toList();

		// 日時を渡すのは、時間帯だけでなく曜日も見るため。
		// 未回答のときは null が渡り、分野は時間帯と曜日だけで決まる。
		//
		// 最後の引数が、記録から出した分野ごとの重み。
		Situation situation = situationJudge.judge(
				LocalDateTime.now(), user.getMainUse(), user.getFocusLevel(),
				avoidCategory, categoryBonus(user));

		// 3つ目の案にそのまま使うタスクを、手持ちから1件だけ選ぶ。
		//
		// 押すたびに新しいタスクが増え続けるのを防ぎ、
		// 登録したまま止まっているものを掘り起こすため。
		//
		// 1件も無ければ null で、そのときは3案とも新しい案になる。
		//
		// findActive はログイン中の本人のぶんだけを返すので、
		// ここに他人のタスクが混ざることはない。AIへ送る材料も本人のものだけになる。
		List<Task> activeTasks = taskService.findActive();
		Task pickedTask = pickOne(activeTasks, user.getOrderPref());

		// 完了したものも材料に入れる。
		// これが無いと、AI は過去を知らないまま「新しい案」を作ることになり、
		// 前にやったものと同じ内容が出てくる。
		List<Task> doneTasks = recentDone();

		String prompt = promptBuilder.buildProposal(
				situation, activeTasks, pickedTask, doneTasks, excludeTitles);

		// ここで3〜5秒待つ。接続は握っていない
		Generated generated = callGenerator(prompt, situation, pickedTask);

		numberCards(generated.cards(), pickedTask, generated.source());

		return proposalStore.save(
				newSet(user, situation, prompt, generated.source()),
				generated.cards());
	}

	/**
	 * AI を呼んで3案を受け取る。失敗したら固定3案に切り替える。
	 *
	 * ここで例外を外に出さないことが、「AIが落ちてもアプリは止まらない」を成り立たせている。
	 *
	 * try の中には generator.generate しか入れていない。
	 * 範囲を広げると、見つからなかったことまで「AIの失敗」として
	 * 固定3案にすり替わり、原因が見えなくなる。
	 *
	 * 失敗は warn で出す。アプリは動き続けるので error ではないが、
	 * 続くようなら調べたいことなので、普段から見える重さにしておく。
	 *
	 * 応答にかかった時間と、どこから来た3案かも残す。
	 * 速さの改善は、推測ではなく計測して判断する。
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
	 * 値を入れるだけなので、データベースには触らない。
	 * 保存は ProposalStore.save がまとめて行う。
	 *
	 * ここが呼ばれるのは AI を呼んだあとなので、
	 * requestedAt に入るのは「3案が出そろった時刻」になる。
	 * 待っていた3〜5秒を迷い時間に数えないための形。詳しくは ProposalSet を見ること。
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
	 * 3枚のカードに番号を振り、重さを入れる。まだ保存はしない。
	 *
	 * 番号が表示順であり、同時に重さの役割も表す。
	 *
	 * 粒度と難易度は、AI の答えを使わずにここで入れ直す。
	 * 分数はプロンプトで枠を指定しているが、粒度と難易度は指定していないので、
	 * 3〜10分の案に「まとまった時間が要る」や HIGH が付くことがある。
	 * 画面には分野・難易度・目安時間が並ぶので、ここが食い違うと選ぶ手がかりにならない。
	 *
	 * 3枚目だけ、元になったタスクを覚えておく。
	 * 選ばれたとき、新しく作らずにそのタスクを進行中にするため。
	 * 題名に続いて難易度と目安時間も元の値を入れる。
	 * 本人が付けた値が、選んだあとのタスクにそのまま残るようにするため。
	 * 入っていない項目は、AI の値をそのまま残す。
	 *
	 * 除くのはモックだけ。
	 * モックは状況も指定タスクも受け取らないので、無関係な3枚目を返す。
	 * それに題名を貼ると、中身とタイトルが食い違う。
	 *
	 * フォールバックは、指定タスクをそのまま3枚目に使うようになったので
	 * 紐づけてよい。紐づけないと、選んだときに同じ題名のタスクが
	 * もう1件できてしまう。
	 *
	 * @param cards      3枚のカード
	 * @param pickedTask 3件目に使うタスク。無ければ null
	 * @param source     どこから来た3案か
	 */
	private void numberCards(List<ProposalCard> cards, Task pickedTask, String source) {

		int position = FIRST_POSITION;

		for (ProposalCard card : cards) {
			card.setPosition(position);

			// 1枚目はすぐ終わる軽いもの、2枚目は今日中に終わるもの
			if (position == FIRST_POSITION) {
				card.setGranularity(Granularity.FIVE_MIN);
				card.setDifficulty(Difficulty.LOW);

			} else if (position == SECOND_POSITION) {
				card.setGranularity(Granularity.TODAY);
				card.setDifficulty(Difficulty.MID);
			}

			if (position == PICKED_POSITION && pickedTask != null && !"MOCK".equals(source)) {
				card.setSourceTaskId(pickedTask.getId());
				card.setTitle(pickedTask.getTitle());
				card.setGranularity(Granularity.TODAY);

				if (pickedTask.getDifficulty() != null) {
					card.setDifficulty(pickedTask.getDifficulty());
				}
				if (pickedTask.getEstimatedMinutes() != null) {
					card.setEstimatedMinutes(pickedTask.getEstimatedMinutes());
				}
			}
			position++;
		}
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
		return findCards(findSet(setId));
	}

	/**
	 * 所有者を確認ずみのセットから、3枚のカードを取り出す。
	 *
	 * すでに findSet を通ったセットを持っているときに使う。
	 * ID から引き直すと、同じ確認をもう一度することになる。
	 *
	 * 引数がセットそのものなので、確認を飛ばして呼ぶことはできない。
	 * ProposalSet を手に入れる方法が findSet しかないため。
	 *
	 * @param set 所有者を確認ずみのセット
	 * @return 1番目から順に並んだカード
	 */
	public List<ProposalCard> findCards(ProposalSet set) {
		return cardRepository.findByProposalSetIdOrderByPositionAsc(set.getId());
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
	 * ここは @Transactional を残している。
	 * 外部への通信が無く、カードとタスクとセットの3つを同時に動かすため。
	 * 途中で失敗したときに、タスクだけができて印が付いていない状態を作らない。
	 *
	 * controller から直接呼ばれるので、アノテーションはきちんと効く。
	 *
	 * 選ばれなかった2枚も消さずに残す。
	 * 「何を出して、何が選ばれなかったか」が、提案の質を測る材料になるため。
	 *
	 * できたタスクのIDをカードに控えている。
	 * これが無いと「選ばれて、実際に完了したか」を数えられない。
	 * 新しい案でも既存タスクでも同じ形で入るので、あとの集計で場合分けが要らない。
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

		// タスクにする。
		// 元になったタスクがあるなら、新しく作らずにそれを進行中にする。
		// ここで新しく作ると、同じ内容のタスクが2件並ぶことになる。
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
	 * @Transactional は付けない。
	 * 新しい3案を作る側で AI を呼ぶので、付けるとその待ち時間ぶん接続を握る。
	 * 閉じる処理だけが ProposalStore を通って、短く握る。
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
	public ProposalSet retopic(Long setId) {

		ProposalSet set = findSet(setId);

		// 確認ずみのセットをそのまま渡す。ID から引き直さないので、所有者の確認は1回で済む
		List<ProposalCard> shown = findCards(set);

		proposalStore.close(set, ProposalStatus.RETOPIC);

		logHesitation(set);

		return generateSet(shown);
	}

	// ===== メソッド（内部で使うもの） =====

	/**
	 * 分野ごとの完了率を出す。SituationJudge の点数に足される。
	 *
	 * 数え方は「(終わった数 + 1) / (出した数 + 3)」。
	 *
	 * 分子と分母に足しているのは、1回出して1回終わっただけの分野が
	 * 100% になって独占するのを防ぐため。
	 * まだ出していない分野は (0+1)/(0+3) = 0.33 になり、3分野が同じ値で並ぶ。
	 * つまりデータが無いうちは差が出ないので、場合分けが要らない。
	 *
	 * 数えるのは、質問に答えた時刻より後だけ。
	 * 答えなおすと起点が動くので、そこで評価もやりなおる。
	 * 記録そのものは消さないので、集計や振り返りには残る。
	 *
	 * 選ばれただけでは上がらず、終わって初めて上がる。
	 * 選択だけを報酬にすると、選びやすいだけの提案に寄ってしまい、
	 * 楽そうなものばかり出て誰も動かない形になりうる。
	 * このアプリの目的は選ばせることではなく、動けること。
	 *
	 * 結果は debug で出す。提案の中身を追うときだけ見たいもので、
	 * 毎回の操作で出し続けると他のログが埋もれる。
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
	 * 迷い時間をログに出す。
	 *
	 * 3案が出てから決めるまでの秒数。
	 * 押してから3案が出るまでの待ち時間は入っていない（ProposalSet を見ること）。
	 *
	 * 何秒からを迷いと見なすかはアプリ側では決めず、
	 * 秒数をそのままプロンプトへ渡して解釈は AI に任せる方針。
	 * 同じ判断を2か所に持つと、必ずどこかで食い違うため。
	 *
	 * こちらも debug。確かめたいときだけ出せばよい。
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
	 * 3つ目の案に使うタスクを1件選ぶ。
	 *
	 * 選び方は最初の3択の答えによる。
	 * 　EASY … 見積りの短い順（かんたんなものから）
	 * 　DUE  … 締切の近い順
	 * 　OLD  … 登録の古い順（残っているものから）
	 * 未回答のときはくじ引き。
	 *
	 * 締切や見積り、登録日時が入っていないタスクは後ろに回している。
	 * 未設定を先頭に置くと、書き忘れただけのものが毎回出てしまう。
	 *
	 * 1件も無ければ null を返す。
	 * 呼び出し側は null のときプロンプトの欄ごと出さないので、
	 * ここで空のリストを返す形にはしていない。
	 *
	 * 文字列をそのまま書いているのは、この値を読むのがここだけのため。
	 * 分野の名前（Categories）は3つのクラスに散っていたのでまとめたが、
	 * こちらは1か所しかないので、定数にしても探す手間は減らない。
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
			// nextInt(n) は 0 以上 n 未満の整数を返す。
			// リストの番号は0から始まるので、そのまま添字に使える。
			//
			// ThreadLocalRandom.current() は、いま動いているスレッド用のくじ引きを返す。
			// このクラスは1つだけ作られて使い回されるので、
			// Random を1つ持つと、同時に使う人数ぶんの取り合いになる
			return tasks.get(ThreadLocalRandom.current().nextInt(tasks.size()));
		}

		return tasks.stream().min(order).orElse(null);
	}
}