package com.example.taskboard.controller;

// ===== インポート =====
// java.time.LocalDate … 日付だけ。締切の受け取りと、今日の日付に使う
// java.util.List      … リストの型。並べ替えのID一覧に使う
//
// 以下は Spring の部品。
// DateTimeFormat … 画面から来た文字を日付に変換する形式を指定する
// Controller     … HTMLを返すクラスです、という印
// Model          … 画面へ値を渡すための入れ物
// GetMapping     … そのURLを開いたときに動くメソッドの印
// PathVariable   … URLの一部（/tasks/5/update の 5）を受け取る
// PostMapping    … そのURLへ送信されたときに動くメソッドの印
// RequestParam   … 入力欄の値を受け取る
//
// 以下はこのアプリの部品。
// Difficulty  … 難易度
// TaskService … タスクの処理
// UserService … 利用者と設定

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
import com.example.taskboard.service.TaskService;
import com.example.taskboard.service.UserService;

/**
 * タスク一覧を受け持つ。
 *
 * 扱うURLは2種類。
 * 表示（GET）  … /tasks
 * 操作（POST） … 追加・更新・状態変更・並べ替え・削除
 *
 * 操作のあとは必ず redirect: で表示のURLへ移す。
 * そのまま画面を返すと、ブラウザで再読み込みしたときに
 * 同じ操作がもう一度送られ、タスクが二重にできる。
 *
 * 判断や計算はここには書かない。すべて TaskService に任せている。
 * このクラスがしているのは、URLと処理を結びつけ、結果の行き先を決めることだけ。
 */

// ===== アノテーション =====
// @Controller … HTMLを返すクラスです、という印。
//                返した文字列（"tasks"）は templates/tasks.html を指す。
//                @RestController にすると、その文字列がそのまま画面に出てしまう。

@Controller
public class TaskController {

	// ===== フィールド =====

	/** タスクの処理 */
	private final TaskService taskService;

	/** 利用者と設定。効果音の音量を画面へ渡すために使う */
	private final UserService userService;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 *
	 * ここで受け取ったものを使うだけで、自分では new しない。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public TaskController(TaskService taskService, UserService userService) {
		this.taskService = taskService;
		this.userService = userService;
	}

	// ===== メソッド（表示） =====

	/**
	 * タスク一覧を表示する。
	 *
	 * 未完了と完了を別々に渡しているが、未完了は1つのリストにまとめてある。
	 * 未実施と進行中で分けるとリストが増えるだけで、状態はタグの色で見分けられる。
	 *
	 * 今日の日付を渡しているのは、締切を過ぎたタスクを色分けするため。
	 * 画面側では LocalDate.now() のような処理を書けないので、ここで用意する。
	 *
	 * @param model 画面へ値を渡す入れ物
	 * @return templates/tasks.html
	 */
	@GetMapping("/tasks")
	public String index(Model model) {

		model.addAttribute("tasks", taskService.findActive());
		model.addAttribute("doneTasks", taskService.findDone());
		model.addAttribute("remaining", taskService.countRemaining());

		// 効果音の音量。画面の body に渡し、JavaScript がそこから読む
		model.addAttribute("volume", userService.current().getSoundVolume());

		// 難易度の選択肢。列挙型の一覧をそのまま渡す
		model.addAttribute("difficulties", Difficulty.values());

		// 締切超過の判定に使う
		model.addAttribute("today", LocalDate.now());

		return "tasks";
	}

	// ===== メソッド（操作） =====

	/**
	 * 新しいタスクを追加する。
	 *
	 * 難易度と締切は required = false にしている。
	 * 難易度を選ばずに送ると null が届き、service 側で「中」になる。
	 * 必須にすると、追加のたびに判断が1つ増える。
	 *
	 * @DateTimeFormat は、画面から来た "2026-09-17" という文字を
	 * LocalDate に変換するための指定。形式を書かないと変換できずに失敗する。
	 *
	 * @param title      タスクの内容
	 * @param difficulty 難易度。未選択なら null
	 * @param dueDate    締切。未入力なら null
	 * @return タスク一覧へ戻る
	 */
	@PostMapping("/tasks")
	public String create(
			@RequestParam String title,
			@RequestParam(required = false) Difficulty difficulty,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate) {

		taskService.create(title, difficulty, dueDate);
		return "redirect:/tasks";
	}

	/**
	 * 既存のタスクを書き換える。
	 *
	 * @PathVariable は URL の一部を受け取る。
	 * /tasks/5/update なら、{id} の部分にあたる 5 が入る。
	 *
	 * detail（完了のヒント）は空欄で保存されることがあるので required = false にする。
	 *
	 * @param id         タスクのID
	 * @param title      新しい内容
	 * @param difficulty 新しい難易度
	 * @param dueDate    新しい締切
	 * @param detail     新しい完了のヒント
	 * @return タスク一覧へ戻る
	 */
	@PostMapping("/tasks/{id}/update")
	public String update(@PathVariable Long id,
			@RequestParam String title,
			@RequestParam Difficulty difficulty,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
			@RequestParam(required = false) String detail) {

		taskService.update(id, title, difficulty, dueDate, detail);
		return "redirect:/tasks";
	}

	/**
	 * 状態を1つ進める。タグを押したときに呼ばれる。
	 *
	 * 未実施 → 進行中 → 完了 → 未実施 と一周する。
	 *
	 * @param id タスクのID
	 * @return タスク一覧へ戻る
	 */
	@PostMapping("/tasks/{id}/next")
	public String next(@PathVariable Long id) {
		taskService.nextStatus(id);
		return "redirect:/tasks";
	}

	/**
	 * ドラッグで並べ替えた結果を保存する。
	 *
	 * ids には "3,1,2" のようにIDがカンマ区切りで届く。
	 * Spring がカンマで区切って List<Long> に変換してくれるので、
	 * 自分で分解する必要はない。
	 *
	 * @param ids 上から順に並んだタスクIDの一覧
	 * @return タスク一覧へ戻る
	 */
	@PostMapping("/tasks/reorder")
	public String reorder(@RequestParam List<Long> ids) {
		taskService.reorder(ids);
		return "redirect:/tasks";
	}

	/**
	 * タスクを削除する。
	 *
	 * @param id タスクのID
	 * @return タスク一覧へ戻る
	 */
	@PostMapping("/tasks/{id}/delete")
	public String delete(@PathVariable Long id) {
		taskService.delete(id);
		return "redirect:/tasks";
	}
}