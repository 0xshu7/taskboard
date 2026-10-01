package com.example.taskboard.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskboard.domain.Difficulty;
import com.example.taskboard.domain.ProposalCard;
import com.example.taskboard.domain.Task;
import com.example.taskboard.domain.TaskStatus;
import com.example.taskboard.exception.NotFoundException;
import com.example.taskboard.repository.TaskRepository;

/**
 * タスクの処理をまとめる場所。
 *
 * controller はこのクラスだけを呼び、repository を直接は触らない。
 * 呼び出しの道を1本に保つと、処理の順番や条件が散らばらずに済む。
 *
 * 担当することは4つ。
 * 1. 取り出す（findActive / findDone / countRemaining / findById）
 * 2. 作る・変える・消す（create / update / nextStatus / reorder / delete）
 * 3. カードからタスクを作る（createFromCard / startExisting）
 * 4. 自動完了（startAuto / cancelAuto / completeDue）
 *
 * 扱うのは、いまログインしている人のタスクだけ。
 * 誰かは UserService に聞き、このクラスはログインの仕組みを知らない。
 *
 * 例外は completeDue で、こちらは時計から呼ばれるためログインしている人がいない。
 * 利用者で絞らず、予定時刻を過ぎたものを全員ぶん取る。
 *
 * 1件を取り出すときは、必ず findById を通す。
 * 他人のタスクかどうかの確認をそこに1か所だけ置いてあるので、
 * repository を直接呼ぶと、その確認を素通りすることになる。
 *
 * 例外は1つだけ。createFromCard は ID ではなくカードそのものを受け取るので、
 * このクラスでは所有者を確かめられない。確認は ProposalService.findCard にある。
 *
 * 状態と完了日時は Task 側が面倒を見ている。
 * setStatus を呼べば完了日時も一緒に動くので、
 * このクラスで日時を触ることはない。
 */
@Service
public class TaskService {

	// ===== フィールド =====

	/** 分数が入っていないタスクの既定値。手で追加したタスクに使う */
	private static final int DEFAULT_MINUTES = 5;

	/** 自動完了までの最短の秒数。これより短くはしない */
	private static final int MIN_AUTO_SECONDS = 5;

	/** タスクの出し入れ */
	private final TaskRepository taskRepository;

	/** いまログインしている利用者を教えてくれる係 */
	private final UserService userService;

	/**
	 * 自動完了までの時間を割る数。
	 *
	 * 1 なら見積もりどおりの時間、60 なら分が秒になる。
	 * デモで25分待つわけにはいかないので、設定で短くできるようにしてある。
	 *
	 * コードではなく application.yml 側で切り替える。
	 * デモのためにコードを書き換えると、戻し忘れたまま本番の説明をすることになる。
	 */
	@Value("${app.auto-complete-divisor:1}")
	private int autoCompleteDivisor;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public TaskService(TaskRepository taskRepository, UserService userService) {
		this.taskRepository = taskRepository;
		this.userService = userService;
	}

	// ===== メソッド（取り出す） =====

	/**
	 * 残りのタスク数を返す。
	 *
	 * 「完了でないもの」を数えている。未実施と進行中の合計。
	 *
	 * @return 未完了のタスク数
	 */
	public long countRemaining() {
		return taskRepository.countByUserIdAndStatusNot(userId(), TaskStatus.DONE);
	}

	/**
	 * 未完了のタスクを、並べ替えた順で返す。
	 *
	 * 未実施と進行中を分けずに1つのリストにしている。
	 * 状態はタグの色で見分けられるので、リストを分けると行き来が増えるだけになる。
	 *
	 * @return 表示順に並んだ未完了のタスク
	 */
	public List<Task> findActive() {

		List<Task> tasks = taskRepository
				.findByUserIdAndStatusNotOrderBySortOrderAsc(userId(), TaskStatus.DONE);

		// おまかせ中のタスクを先頭に出す。
		//
		// sort は安定並べ替えなので、おまかせ中どうし、そうでないものどうしの
		// 並び順は元のまま（本人が決めた sort_order）が保たれる。
		tasks.sort((a, b) -> {
			boolean aAuto = a.getAutoCompleteAt() != null;
			boolean bAuto = b.getAutoCompleteAt() != null;

			if (aAuto == bAuto) {
				return 0;
			}
			return aAuto ? -1 : 1;
		});

		return tasks;
	}

	/**
	 * 完了したタスクを、新しい順で返す。
	 *
	 * 画面では折りたたんで下に置く。
	 * 消さずに残すのは、前にどう進めたかを見返す材料になるため。
	 *
	 * @return 完了日時の新しい順に並んだタスク
	 */
	public List<Task> findDone() {
		return taskRepository.findByUserIdAndStatusOrderByCompletedAtDesc(userId(), TaskStatus.DONE);
	}

	/**
	 * タスクを1件取り出す。
	 *
	 * 他人のタスクは取り出せない。
	 *
	 * 一覧は user_id で絞っているので、他人のタスクは画面に出ない。
	 * だが URL の番号を書き換えれば、出ていないものにも手が届く。
	 * /tasks/999/delete の 999 を他人のタスクにすれば、それが消せてしまう。
	 *
	 * 見えないことと、触れないことは別。
	 * 1件を扱うすべての処理がこのメソッドを通るので、確認はここに1か所だけ置く。
	 *
	 * 他人のものだったときも「見つかりません」と同じ扱いにしている。
	 * 「あなたのものではありません」と返すと、
	 * その番号のタスクが存在することを相手に教えることになる。
	 *
	 * NotFoundException は 404 で返る。
	 * 以前は IllegalArgumentException を投げていたが、それは 500（サーバーの不具合）になる。
	 * 壊れていないのに壊れたと言うと、本当に壊れたときに見分けがつかない。
	 *
	 * @param id タスクのID
	 * @return 見つかったタスク
	 * @throws NotFoundException 無いか、他人のものだったとき
	 */
	public Task findById(Long id) {

		Task task = taskRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("タスクが見つかりません:" + id));

		if (!task.getUserId().equals(userId())) {
			throw new NotFoundException("タスクが見つかりません:" + id);
		}
		return task;
	}

	// ===== メソッド（作る・変える・消す） =====

	/**
	 * 新しいタスクを登録する。
	 *
	 * 難易度を選ばずに追加されたときは「中」にする。
	 * 入力を必須にすると、追加のたびに1つ判断が増える。
	 * 「迷っているより、まず実行」なので、決めずに書き始められるようにしている。
	 *
	 * @param title      タスクの内容
	 * @param difficulty 難易度。null なら中になる
	 * @param dueDate    締切。無くてよい
	 */
	@Transactional
	public void create(String title, Difficulty difficulty, LocalDate dueDate) {

		if (difficulty == null) {
			difficulty = Difficulty.MID;
		}

		Task task = new Task();
		task.setUserId(userId());
		task.setTitle(title);
		task.setDifficulty(difficulty);
		task.setDueDate(dueDate);
		task.setStatus(TaskStatus.TODO);
		task.setSortOrder(nextSortOrder());

		taskRepository.save(task);
	}

	/**
	 * 既存のタスクを書き換える。
	 *
	 * 変えられるのは内容・難易度・締切・完了のヒントの4つ。
	 * 状態はここでは触らない。変えるのは nextStatus だけに限っている。
	 *
	 * @param id         タスクのID
	 * @param title      新しい内容
	 * @param difficulty 新しい難易度
	 * @param dueDate    新しい締切
	 * @param detail     新しい完了のヒント。空欄で保存されることもあるので null を許す
	 */
	@Transactional
	public void update(Long id, String title, Difficulty difficulty, LocalDate dueDate, String detail) {

		Task task = findById(id);

		task.setTitle(title);
		task.setDifficulty(difficulty);
		task.setDueDate(dueDate);
		task.setDetail(detail);

		taskRepository.save(task);
	}

	/**
	 * 状態を1つ進める。未実施 → 進行中 → 完了 → 未実施 と一周する。
	 *
	 * 一周させているのは、押し間違えたときに戻せるようにするため。
	 * 完了にしたものを取り消す道が無いと、間違いが残り続ける。
	 *
	 * 完了日時はここでは触らない。Task.setStatus が一緒に面倒を見ている。
	 *
	 * @param id タスクのID
	 */
	@Transactional
	public void nextStatus(Long id) {

		Task task = findById(id);

		if (task.getStatus() == TaskStatus.TODO) {
			task.setStatus(TaskStatus.DOING);

		} else if (task.getStatus() == TaskStatus.DOING) {
			task.setStatus(TaskStatus.DONE);

		} else {
			task.setStatus(TaskStatus.TODO);
		}

		taskRepository.save(task);
	}

	/**
	 * 画面から送られてきた並び順のとおりに、表示順を振り直す。
	 *
	 * ids には「上から順に並んだタスクIDの一覧」が入る。
	 * 上から 0, 1, 2 と番号を振っていくので、
	 * 送られてきた順がそのまま表示順になる。
	 *
	 * findById を通しているので、他人のタスクIDが混ざっていれば途中で止まる。
	 * @Transactional が付いているため、そこまでの並べ替えも取り消される。
	 *
	 * @param ids 上から順に並んだタスクIDの一覧
	 */
	@Transactional
	public void reorder(List<Long> ids) {

		for (int i = 0; i < ids.size(); i++) {
			Task task = findById(ids.get(i));
			task.setSortOrder(i);
			taskRepository.save(task);
		}
	}

	/**
	 * タスクを削除する。
	 *
	 * @param id タスクのID
	 */
	@Transactional
	public void delete(Long id) {
		taskRepository.delete(findById(id));
	}

	// ===== メソッド（カードからタスクにする） =====

	/**
	 * 提案カードからタスクを作る。
	 *
	 * 状態を進行中から始める。
	 * 3択から選んだということは、これからやると決めたということなので、
	 * もう一度「始める」を押させる意味がない。
	 *
	 * カードから写すのは内容・完了のヒント・難易度・かかる時間の4つ。
	 * 締切は付けない。今すぐ取りかかるものなので、期限を持たせる必要がない。
	 *
	 * このメソッドだけ、IDではなくカードそのものを受け取っている。
	 * そのため、ここでは「そのカードが本人のものか」を確かめられない。
	 * 確認は ProposalService.findCard で済ませてから渡している。
	 * 別の場所からカードを作って渡すと、その確認を飛ばすことになる。
	 *
	 * @param card 選ばれたカード。所有者の確認は ProposalService.findCard で済んでいる
	 * @return 作られたタスク
	 */
	@Transactional
	public Task createFromCard(ProposalCard card) {

		Task task = new Task();
		task.setUserId(userId());
		task.setTitle(card.getTitle());
		task.setDetail(card.getDetail());
		task.setDifficulty(card.getDifficulty());
		task.setEstimatedMinutes(card.getEstimatedMinutes());
		task.setStatus(TaskStatus.DOING);
		task.setSortOrder(nextSortOrder());

		return taskRepository.save(task);
	}

	/**
	 * すでにあるタスクを進行中にする。
	 *
	 * 3案の3つ目が選ばれたときに呼ばれる。
	 * 新しく作らないので、同じ内容のタスクが2件並ぶことがない。
	 *
	 * 完了のヒントは、そのタスクが持っていないときだけ入れる。
	 * 本人が書いたものを、AIが書いたもので黙って上書きしないため。
	 * 消えたことに気づけるのは本人だけで、戻す手段が無い。
	 *
	 * @param taskId 進行中にするタスクのID
	 * @param detail AIが書いた完了のヒント
	 */
	@Transactional
	public void startExisting(Long taskId, String detail) {

		Task task = findById(taskId);

		task.setStatus(TaskStatus.DOING);

		if (task.getDetail() == null || task.getDetail().isEmpty()) {
			task.setDetail(detail);
		}

		taskRepository.save(task);
	}

	// ===== メソッド（自動完了） =====

	/**
	 * 「やっちゃう」を押したとき。自動完了を予約する。
	 *
	 * 進行中にしたうえで、完了する時刻を決めて入れておく。
	 * 時間が来たかどうかを見るのはサーバー側なので、
	 * アプリを閉じていても完了し、通知が届く。
	 *
	 * 分数が入っていないタスク（手で追加したもの）は既定値を使う。
	 *
	 * @param taskId 予約するタスクのID
	 */
	@Transactional
	public void startAuto(Long taskId) {

		Task task = findById(taskId);

		int minutes = task.getEstimatedMinutes() == null
				? DEFAULT_MINUTES
				: task.getEstimatedMinutes();

		// 割る数が 0 だと、割り算そのものが例外で落ちる。
		// 設定ファイルから読む値なので、書き間違いが入りうる
		int divisor = Math.max(autoCompleteDivisor, 1);

		// 割った結果が0にならないようにする。
		// 0秒後だと、押した瞬間に完了してしまい、何が起きたか分からない
		long seconds = Math.max(minutes * 60L / divisor, MIN_AUTO_SECONDS);

		task.setStatus(TaskStatus.DOING);
		task.setAutoCompleteAt(LocalDateTime.now().plusSeconds(seconds));

		taskRepository.save(task);
	}

	/**
	 * 「おまかせ中」をもう一度押したとき。予約を取り消す。
	 *
	 * 状態は進行中のままにする。
	 * 押した時点で始めているはずなので、未実施に戻すと手が止まる。
	 *
	 * @param taskId 取り消すタスクのID
	 */
	@Transactional
	public void cancelAuto(Long taskId) {

		Task task = findById(taskId);

		task.setAutoCompleteAt(null);
		taskRepository.save(task);
	}

	/**
	 * 予定時刻を過ぎたタスクを完了にする。AutoCompleteService から10秒ごとに呼ばれる。
	 *
	 * 完了させるのは status が進行中で、予定時刻を過ぎたものだけ。
	 * 予約していないタスクの autoCompleteAt は null なので、検索に引っかからない。
	 *
	 * ログインしている人がいないので、利用者では絞らない。
	 * ここだけ findById を通らないが、IDを外から受け取っていないので、
	 * 誰かが他人のタスクを指定する余地がない。
	 *
	 * 予約を消しているのは、完了を取り消して進行中に戻したときに、
	 * 過ぎた予約が生き返って、すぐまた完了にされてしまうのを防ぐため。
	 *
	 * 通知はここでは送らない。
	 * 外部への通信を、データベースを書き換えている区間に入れないため。
	 * 完了したタスクを返すので、知らせるのは呼び出し側の仕事になる。
	 *
	 * @return 完了にしたタスク。1件も無ければ空のリスト
	 */
	@Transactional
	public List<Task> completeDue() {

		List<Task> tasks = taskRepository
				.findByStatusAndAutoCompleteAtLessThanEqual(TaskStatus.DOING, LocalDateTime.now());

		for (Task task : tasks) {

			task.setStatus(TaskStatus.DONE);
			task.setAutoCompleteAt(null);

			taskRepository.save(task);
		}

		return tasks;
	}

	// ===== メソッド（内部で使うもの） =====

	/**
	 * いまログインしている利用者のIDを返す。
	 *
	 * 「いまの利用者は誰か」を知っているのは UserService だけにしてある。
	 * このクラスはログインの仕組みを知らず、IDを受け取るだけで済む。
	 *
	 * @return 利用者のID
	 */
	private Long userId() {
		return userService.currentId();
	}

	/**
	 * 新しいタスクに付ける表示順を返す。いまある中でいちばん大きい値 + 1。
	 *
	 * private なので、このクラスの外からは呼べない。
	 * 表示順の決め方を外に見せないことで、付け方が散らばらないようにしている。
	 *
	 * 自分の未完了タスクだけを見ている。
	 * 全件を見ると、他の利用者のタスクの番号に引きずられて、
	 * 使わない大きな数字が付くことになる。
	 *
	 * 取り出してから調べているため、件数が増えると重くなる。
	 * SQL で最大値だけを取れば1回で済むが、いまの件数では違いが出ない。
	 * 遅くなったときに直す。
	 *
	 * @return 付けるべき表示順
	 */
	private int nextSortOrder() {

		int max = 0;

		for (Task task : findActive()) {
			if (task.getSortOrder() > max) {
				max = task.getSortOrder();
			}
		}
		return max + 1;
	}
}