package com.example.taskboard.service;

// ===== インポート =====
// java.time.LocalDate     … 日付だけ。締切に使う
// java.time.LocalDateTime … 日付＋時刻。完了日時に使う
// java.util.List          … リストの型
//
// 以下は Spring の部品。
// Service       … 処理のまとまりです、という印。Spring が管理下に置く
// Transactional … メソッドの途中で失敗したら、その中の保存をすべて取り消す
//
// 以下はこのアプリの部品。
// Difficulty     … 難易度
// ProposalCard   … 提案カード1枚
// Task           … タスク1件
// TaskStatus     … タスクの状態
// TaskRepository … タスクの出し入れ

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskboard.domain.Difficulty;
import com.example.taskboard.domain.ProposalCard;
import com.example.taskboard.domain.Task;
import com.example.taskboard.domain.TaskStatus;
import com.example.taskboard.repository.TaskRepository;

/**
 * タスクの処理をまとめる場所。
 *
 * controller はこのクラスだけを呼び、repository を直接は触らない。
 * 呼び出しの道を1本に保つと、処理の順番や条件が散らばらずに済む。
 *
 * 担当することは3つ。
 * 1. 取り出す（findActive / findDone / countRemaining / findById）
 * 2. 作る・変える・消す（create / update / nextStatus / reorder / delete）
 * 3. カードからタスクを作る（createFromCard）
 *
 * 扱うのは、いまログインしている人のタスクだけ。
 * 誰かは UserService に聞き、このクラスはログインの仕組みを知らない。
 *
 * 状態と完了日時は必ずセットで扱う。
 * 完了にしたのに日時が入っていない、完了を取り消したのに日時が残っている、
 * という食い違いが起きると、完了一覧の並び順が狂う。
 * そのため、状態を変える処理はこのクラスの中に閉じ込めてある。
 */
@Service
public class TaskService {

	// ===== フィールド =====

	/** タスクの出し入れ */
	private final TaskRepository taskRepository;

	/** いまログインしている利用者を教えてくれる係 */
	private final UserService userService;

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
		return taskRepository.findByUserIdAndStatusNotOrderBySortOrderAsc(userId(), TaskStatus.DONE);
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
	 * 見つからないときは例外を投げる。
	 * null を返すと、受け取った側が確認を忘れたときに
	 * ずっと先で分かりにくい形で失敗するため、その場で止める。
	 *
	 * @param id タスクのID
	 * @return 見つかったタスク
	 */
	public Task findById(Long id) {
		return taskRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("タスクが見つかりません:" + id));
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
	 * 状態と完了日時はここでは触らない。食い違いを防ぐため、
	 * 状態を変えるのは nextStatus だけに限っている。
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
	 * 完了になったときだけ完了日時を入れ、それ以外では消す。
	 * この2つを必ず一緒に動かすことが、完了一覧を正しく並べる前提になっている。
	 *
	 * @param id タスクのID
	 */
	@Transactional
	public void nextStatus(Long id) {
		Task task = findById(id);

		if (task.getStatus() == TaskStatus.TODO) {
			task.setStatus(TaskStatus.DOING);
			task.setCompletedAt(null);

		} else if (task.getStatus() == TaskStatus.DOING) {
			task.setStatus(TaskStatus.DONE);
			task.setCompletedAt(LocalDateTime.now());

		} else {
			task.setStatus(TaskStatus.TODO);
			task.setCompletedAt(null);
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
	 * @Transactional を付けているのは、途中で失敗したときに
	 * 順番が半分だけ入れ替わった状態を残さないため。
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

	// ===== メソッド（カードから作る） =====

	/**
	 * 提案カードからタスクを作る。
	 *
	 * 状態を進行中から始める。
	 * 3択から選んだということは、これからやると決めたということなので、
	 * もう一度「始める」を押させる意味がない。
	 *
	 * カードから写すのは内容・完了のヒント・難易度の3つ。
	 * 締切は付けない。今すぐ取りかかるものなので、期限を持たせる必要がない。
	 *
	 * @param card 選ばれたカード
	 * @return 作られたタスク
	 */
	@Transactional
	public Task createFromCard(ProposalCard card) {

		Task task = new Task();
		task.setUserId(userId());
		task.setTitle(card.getTitle());
		task.setDetail(card.getDetail());
		task.setDifficulty(card.getDifficulty());
		task.setStatus(TaskStatus.DOING);
		task.setSortOrder(nextSortOrder());

		return taskRepository.save(task);
	}

	// ===== メソッド（内部で使うもの） =====

	/**
	 * いまログインしている利用者のIDを返す。
	 *
	 * 「いまの利用者は誰か」を知っているのは UserService だけにしてある。
	 * このクラスはログインの仕組みを知らず、IDを受け取るだけで済む。
	 *
	 * 短い名前の private メソッドにしているのは、呼び出しが6か所あり、
	 * 毎回 userService.currentId() と書くと行が長くなって読みにくいため。
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