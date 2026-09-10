package com.example.taskboard.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.taskboard.domain.Difficulty;
import com.example.taskboard.service.NotificationService;
import com.example.taskboard.service.TaskService;

// 画面を返すクラス。@Controller は「HTMLを返す」という意味
// （@RestController だと文字やJSONをそのまま返してしまう）
@Controller
public class TaskController {

	private final TaskService taskService;
	private final NotificationService notificationService;

	public TaskController(TaskService taskService, NotificationService notificationService) {
		this.taskService = taskService;
		this.notificationService = notificationService;
	}

	// トップページ。一覧を作って画面に渡す
	@GetMapping("/tasks")
	public String index(Model model) {

		// 1つのリストに全部入れる。状態はタグの色で見分ける
		model.addAttribute("tasks", taskService.findActive());
		model.addAttribute("doneTasks", taskService.findDone());
		model.addAttribute("remaining", taskService.countRemaining());

		// 難易度のドロップダウンを作るために、enum の一覧を渡す
		model.addAttribute("difficulties", Difficulty.values());

		// 締切超過の判定に使う。画面側で LocalDate.now() は呼べないので渡す
		model.addAttribute("today",java.time.LocalDate.now());
		
		// templates/tasks.html を表示するという意味
		return "tasks";
	}

	// 新しいタスクを追加する
	@PostMapping("/tasks")
	public String create(
			@RequestParam String title,
			// 未選択のときは null が入る。required = false が必要
			@RequestParam(required = false) Difficulty difficulty,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate) {

		taskService.create(title, difficulty, dueDate);
		return "redirect:/tasks";
	}

	// 既存のタスクを書き換える
	@PostMapping("/tasks/{id}/update")
	public String update(
			@PathVariable Long id,   // URL の {id} 部分が入る
			@RequestParam String title,
			@RequestParam Difficulty difficulty,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate) {

		taskService.update(id, title, difficulty, dueDate);
		return "redirect:/tasks";
	}

	// 状態を1つ進める（タグをクリックしたとき）
	@PostMapping("/tasks/{id}/next")
	public String next(@PathVariable Long id) {
		taskService.nextStatus(id);
		return "redirect:/tasks";
	}

	// ドラッグで並べ替えた結果を保存する
	// ids には "3,1,2" のようにIDがカンマ区切りで届く
	// Spring が自動でカンマを区切って List<Long> にしてくれる
	@PostMapping("/tasks/reorder")
	public String reorder(@RequestParam List<Long> ids) {
		taskService.reorder(ids);
		return "redirect:/tasks";
	}

	// 削除する
	@PostMapping("/tasks/{id}/delete")
	public String delete(@PathVariable Long id) {
		taskService.delete(id);
		return "redirect:/tasks";
	}
	
	// 通知を今すぐ送る（動作確認用。あとで消すか、管理者だけに限定する）
	@PostMapping("/notify/test")
	public String notifyTest() {
		notificationService.notifyDueTasks();
		return "redirect:/tasks";
	}
}