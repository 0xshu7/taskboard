package com.example.taskboard.controller;

// ===== インポート =====
// 以下は Spring の部品。
// Controller … HTMLを返すクラスです、という印
// Model      … 画面へ値を渡すための入れ物
// GetMapping … そのURLを開いたときに動くメソッドの印
//
// 以下はこのアプリの部品。
// TaskService     … タスクの処理。残り件数を出すために使う
// UserService     … 利用者と設定。効果音の音量を画面へ渡すために使う

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.taskboard.service.ProposalService;
import com.example.taskboard.service.TaskService;
import com.example.taskboard.service.UserService;

/**
 * アプリを開いたときの最初の画面を受け持つ。
 *
 * 一覧ではなく、スタートボタンだけを大きく置いている。
 * 開いた瞬間に一覧が目に入ると、何から手を付けるかを考えることになる。
 * 「迷っているより、まず実行」なので、押すものを1つに絞っている。
 *
 * タスク一覧への入口は小さく添える程度にしてある。
 * 必要な人は行けるが、主役は押さない。
 *
 * このクラスには操作（POST）が無い。
 * スタートを押したあとの処理は ProposalController が受け持つ。
 */
@Controller
public class HomeController {

	// ===== フィールド =====

	/** タスクの処理。残り件数を出すために使う */
	private final TaskService taskService;

	/** 3案の処理。裏で作りおきを始めるために使う */
	private final ProposalService proposalService;

	/** 利用者と設定。効果音の音量を画面へ渡すために使う */
	private final UserService userService;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public HomeController(TaskService taskService, ProposalService proposalService,
			UserService userService) {
		this.taskService = taskService;
		this.proposalService = proposalService;
		this.userService = userService;
	}

	// ===== メソッド =====

	/**
	 * スタート画面を表示する。
	 *
	 * 表示のついでに、裏で3案を作り始める。
	 * これがこのアプリの待ち時間対策の入口になっている。
	 *
	 * AI の応答には数秒かかる。人が待てるのは3秒程度で、
	 * 10秒を超えると離脱する。押してから待たせるのは致命的なので、
	 * 開いた時点で作り始め、押されたときにはできている状態にする。
	 *
	 * prepareInBackground には @Async が付いているので、
	 * 呼んだあと終わるのを待たずに次へ進む。画面の表示は止まらない。
	 * 同じクラスの中から呼ぶと @Async が効かないため、
	 * コントローラから直接呼ぶ必要がある。
	 *
	 * 代償として、画面を開いただけで AI を1回呼ぶ。
	 * 押さずに閉じた場合、その1回はむだになる。
	 *
	 * @param model 画面へ値を渡す入れ物
	 * @return templates/home.html
	 */
	@GetMapping("/")
	public String home(Model model) {

		model.addAttribute("remaining", taskService.countRemaining());

		// 効果音の音量。画面の body に渡し、JavaScript がそこから読む
		model.addAttribute("volume", userService.current().getSoundVolume());

		return "home";
	}
	
	/**
	 * ログイン画面を表示する。
	 *
	 * ログインしていない人がアプリを開くと、Spring Security がここへ送る。
	 * ログアウトしたときも、ここへ戻ってくる。
	 *
	 * ここでは何も渡さない。まだ誰か分からない状態なので、利用者の情報は取れない。
	 *
	 * @return templates/login.html
	 */
	@GetMapping("/login")
	public String login() {
		return "login";
	}
	

}