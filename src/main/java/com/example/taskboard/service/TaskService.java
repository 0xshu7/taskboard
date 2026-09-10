package com.example.taskboard.service;

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

//業務の処理をまとめる場所。Controller はここだけを呼び、Repository は直接触らない
@Service
public class TaskService {

	// ログイン機能を作るまでの仮のユーザーID。差し替えるのはこの1行だけ
	private static final Long USER_ID = 1L;

	// Repository を受け取って保持する
	private final TaskRepository taskRepository;

	// コンストラクタが1つだけなら、Spring が自動で引数を渡してくれる
	public TaskService(TaskRepository taskRepository) {
		this.taskRepository = taskRepository;

	}

	// 残りタスク数。「完了でないもの」の件数を数える
	public long countRemaining() {
		return taskRepository.countByUserIdAndStatusNot(USER_ID, TaskStatus.DONE);

	}

	// 未完了のタスク。メインのリストに出す
	public List<Task> findActive(){
		return taskRepository.findByUserIdAndStatusNotOrderBySortOrderAsc(USER_ID, TaskStatus.DONE);
		
	}
	
	// 完了したタスク。折りたたんで下に出す
	public List<Task>findDone(){
		return taskRepository.findByUserIdAndStatusOrderByCompletedAtDesc(USER_ID, TaskStatus.DONE);
		
	}
	
	

	// 状態を1つ進める。未実施 → 進行中 → 完了 → 未実施 と一周する
	@Transactional
	public void nextStatus(Long id) {
		Task task = findById(id);

		// 今の状態によって、次の状態を決める
		if (task.getStatus() == TaskStatus.TODO) {
			task.setStatus(TaskStatus.DOING);
			task.setCompletedAt(null);

		} else if (task.getStatus() == TaskStatus.DOING) {
			task.setStatus(TaskStatus.DONE);
			task.setCompletedAt(LocalDateTime.now()); // 完了した時刻を記録

		} else {
			task.setStatus(TaskStatus.TODO);
			task.setCompletedAt(null);
		}

		taskRepository.save(task);
	}

	// 画面から送られてきた並び順のとおりに sortOrder を振り直す
	// ids は「上から順に並んだタスクIDの一覧」
	@Transactional
	public void reorder(List<Long> ids) {
		// 上から 0, 1, 2 ... と番号を振っていく
		for (int i = 0; i < ids.size(); i++) {
			Task task = findById(ids.get(i));
			task.setSortOrder(i);
			taskRepository.save(task);
		}
	}

	// 1件取り出す。見つからなければ例外を投げる
	public Task findById(Long id) {
		return taskRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("タスクが見つかりません:" + id));

	}

	// 新しいタスクを登録する
	// @Transactional を付けると、途中でエラーが出たときに変更がなかったことになる
	@Transactional
	public void create(String title, Difficulty difficulty, LocalDate dueDate) {
		// 難易度を選ばずに追加されたときは「中」にしておく
		if (difficulty == null) {
			difficulty = Difficulty.MID;
		}
		Task task = new Task();
		task.setUserId(USER_ID);
		task.setTitle(title);
		task.setDifficulty(difficulty);
		task.setDueDate(dueDate);
		task.setStatus(TaskStatus.TODO);
		task.setSortOrder(nextSortOrder());
		taskRepository.save(task);

	}

	// 既存のタスクを書き換える
	@Transactional
	public void update(Long id, String title, Difficulty difficulty, LocalDate dueDate) {
		Task task = findById(id);
		task.setTitle(title);
		task.setDifficulty(difficulty);
		task.setDueDate(dueDate);
		taskRepository.save(task);

	}

	// 状態を変える
	@Transactional
	public void changeStatus(Long id, TaskStatus next) {
		Task task = findById(id);
		task.setStatus(next);

		// 完了になったら完了日時を記録し、完了以外に戻したら消す
		if (next == TaskStatus.DONE) {
			task.setCompletedAt(LocalDateTime.now());

		} else {
			task.setCompletedAt(null);
		}
		taskRepository.save(task);
	}

	// 削除する
	@Transactional
	public void delete(Long id) {
		taskRepository.delete(findById(id));
	}

	// 新しいタスクに付ける並び順。今ある中で一番大きい値 + 1
	private int nextSortOrder() {
		int max = 0;
		for (Task task : taskRepository.findAll()) {
			if (task.getSortOrder() > max) {
				max = task.getSortOrder();

			}
		}
		return max + 1;
	}
	
	// 提案カードからタスクを作る。選んだ＝これからやるので、いきなり進行中にする
	@Transactional
	public Task createFromCard(ProposalCard card) {
		
		Task task =new Task();
		task.setUserId(USER_ID);
		task.setTitle(card.getTitle());
		task.setDifficulty(card.getDifficulty());
		task.setStatus(TaskStatus.DOING);
		task.setSortOrder(nextSortOrder());
		
		return taskRepository.save(task);
	}

}
