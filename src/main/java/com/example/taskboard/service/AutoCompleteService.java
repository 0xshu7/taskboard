package com.example.taskboard.service;

// ===== インポート =====
// java.time.LocalDateTime … 日付＋時刻。予定時刻の比較に使う
// java.util.List          … リストの型
//
// 以下は Spring の部品。
// Scheduled     … 決まった間隔でメソッドを自動で動かす
// Service       … 処理のまとまりです、という印
// Transactional … メソッドの途中で失敗したら、その中の保存をすべて取り消す
//
// 以下はこのアプリの部品。
// Task           … タスク1件
// TaskStatus     … タスクの状態
// TaskRepository … タスクの出し入れ
// UserRepository … 利用者の出し入れ

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskboard.domain.Task;
import com.example.taskboard.domain.TaskStatus;
import com.example.taskboard.repository.TaskRepository;
import com.example.taskboard.repository.UserRepository;

/**
 * 「やっちゃう」で予約されたタスクを、時間が来たら完了にする。
 *
 * 画面のタイマーではなく、サーバー側で時刻を見ている。
 * ブラウザのタイマーだと、閉じた瞬間に止まってしまう。
 * こちらで持てば、押してアプリを閉じて、実際に手を動かしている間に完了し、
 * 通知だけが届く、という流れになる。
 *
 * 完了させるのは status が進行中で、予定時刻を過ぎたものだけ。
 * 予約していないタスクの autoCompleteAt は null なので、検索に引っかからない。
 *
 * この仕組みを入れた理由は、完了ボタンの押し忘れを減らすこと。
 * 始めるときに1回、終わったときにもう1回押す必要があると、2回目を忘れる。
 * 「やっちゃう」は、始めると同時に終わりを予約する。
 */
@Service
public class AutoCompleteService {

	// ===== フィールド =====

	/** タスクの出し入れ */
	private final TaskRepository taskRepository;

	/** 利用者の出し入れ。通知の送り先を引くのに使う */
	private final UserRepository userRepository;

	/** 通知を送る係。送り方はあちらに1か所だけ持たせてある */
	private final NotificationService notificationService;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public AutoCompleteService(TaskRepository taskRepository,
			UserRepository userRepository,
			NotificationService notificationService) {

		this.taskRepository = taskRepository;
		this.userRepository = userRepository;
		this.notificationService = notificationService;
	}

	// ===== メソッド =====

	/**
	 * 時間が来たタスクを完了にする。
	 *
	 * fixedDelay は「前回が終わってから次までの間隔」。
	 * fixedRate（開始から開始まで）と違い、処理が長引いても重ならない。
	 *
	 * 10秒ごとにしているのは、デモで divisor を 60 にしたときのため。
	 * 5分のタスクが5秒で終わる設定なので、間隔が長いと待たされる。
	 * 対象が無ければ検索1回で終わるので、負荷はほとんどない。
	 *
	 * ブラウザからの要求ではないので、ログインしている人がいない。
	 * だから「いまの利用者」ではなく、タスクが持つ userId から引く。
	 */
	@Scheduled(fixedDelay = 10000)
	@Transactional
	public void completeDueTasks() {

		List<Task> tasks = taskRepository
				.findByStatusAndAutoCompleteAtLessThanEqual(TaskStatus.DOING, LocalDateTime.now());

		for (Task task : tasks) {

			task.setStatus(TaskStatus.DONE);
			task.setAutoCompleteAt(null);

			// 予約を消す。残しておくと、もう一度完了にしようとし続ける
			task.setAutoCompleteAt(null);

			taskRepository.save(task);

			System.out.println("### おまかせ完了: " + task.getTitle());

			// 通知する。届け先を登録していない人には、何も起きない
			userRepository.findById(task.getUserId()).ifPresent(user ->
					notificationService.send(user, "おまかせ完了",
							task.getTitle() + "\nを完了にしました"));
		}
	}
}