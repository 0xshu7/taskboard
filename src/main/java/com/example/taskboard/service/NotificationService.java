package com.example.taskboard.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskboard.domain.Task;
import com.example.taskboard.domain.TaskStatus;
import com.example.taskboard.mail.MailSender;
import com.example.taskboard.repository.TaskRepository;

// 締切が近いタスクを知らせる
@Service
public class NotificationService {

	// ログイン機能を作るまでの仮のユーザーID
	private static final Long USER_ID = 1L;

	private final TaskRepository taskRepository;
	private final MailSender mailSender;

	// application.yml の app.notify-to を読み込む
	@Value("${app.notify-to}")
	private String notifyTo;

	public NotificationService(TaskRepository taskRepository, MailSender mailSender) {
		this.taskRepository = taskRepository;
		this.mailSender = mailSender;
	}

	// 毎朝8時に実行する
	// cron は左から「秒 分 時 日 月 曜日」。0秒 0分 8時 毎日 毎月 毎曜日
	@Scheduled(cron = "0 0 8 * * *")
	@Transactional
	public void notifyDueTasks() {

		LocalDate today = LocalDate.now();
		LocalDate tomorrow = today.plusDays(1);

		// 締切が今日か明日で、まだ完了していないタスクを取る
		List<Task> candidates = taskRepository
				.findByUserIdAndStatusNotAndDueDateBetweenOrderByDueDateAsc(
						USER_ID, TaskStatus.DONE, today, tomorrow);

		// このうち、今日まだ通知していないものだけを選ぶ
		List<Task> targets = new ArrayList<>();

		for (Task task : candidates) {
			// 通知したことがない、または最後に通知したのが今日ではない
			if (task.getNotifiedDate() == null || !task.getNotifiedDate().equals(today)) {
				targets.add(task);
			}
		}

		// 送るものが無ければ、メールは送らない
		// 「何もありません」を毎日送ると、読まれなくなる
		if (targets.isEmpty()) {
			return;
		}

		mailSender.send(notifyTo, buildSubject(targets), buildBody(targets, today));

		// 送ったタスクに「今日通知した」と記録する
		// これで、同じ日に何度実行しても二重に送られない
		for (Task task : targets) {
			task.setNotifiedDate(today);
			taskRepository.save(task);
		}
	}

	// 件名を作る
	private String buildSubject(List<Task> tasks) {
		return "【Task Board】締切が近いタスクが " + tasks.size() + " 件あります";
	}

	// 本文を作る
	private String buildBody(List<Task> tasks, LocalDate today) {

		// 文字列を何度も足すときは StringBuilder を使う
		// String の + は、足すたびに新しい文字列を作るので効率が悪い
		StringBuilder body = new StringBuilder();

		body.append("締切が近いタスクです。\n\n");

		for (Task task : tasks) {

			// 今日が締切か、明日が締切かで見出しを変える
			String when = task.getDueDate().equals(today) ? "【今日まで】" : "【明日まで】";

			body.append(when)
			    .append(task.getTitle())
			    .append("（難易度 ")
			    .append(task.getDifficulty().getLabel())
			    .append("）\n");
		}

		body.append("\n迷っているより、まず実行。ほんの少しでも。\n");
		body.append("http://localhost:8080/\n");

		return body.toString();
	}
}