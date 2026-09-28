package com.example.taskboard.controller;

// ===== インポート =====
// 以下は Spring の部品。
// Controller … HTMLを返すクラスです、という印
// Model      … 画面へ値を渡すための入れ物
// GetMapping … そのURLを開いたときに動くメソッドの印
//
// 以下はこのアプリの部品。
// TaskService … タスクの処理。残り件数を出すために使う
// UserService … 利用者と設定。効果音の音量を画面へ渡すために使う

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.taskboard.dto.SettingsForm;
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

	/** 利用者と設定。効果音の音量を画面へ渡すために使う */
	private final UserService userService;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public HomeController(TaskService taskService, UserService userService) {
		this.taskService = taskService;
		this.userService = userService;
	}

	// ===== メソッド =====

	/**
	 * スタート画面を表示する。
	 *
	 * この画面では AI を呼ばない。開くだけなら何も起きない。
	 *
	 * 以前は、開いた時点で裏に3案を1セット作っておき、
	 * 押されたときには待ち時間ゼロで出せるようにしていた。
	 * 速さでは優れていたが、次の2つと引き換えだった。
	 *
	 * 1. 1回のスタートで AI を2回呼ぶ（開いたときと、押したとき）。
	 *    無料枠が1日20回なので、実質10回しか使えなかった。
	 *    押さずに閉じた場合、その1回はまるごとむだになる。
	 * 2. 裏で動かすための @Async が必要で、別の流れでは
	 *    ログイン情報が引き継がれないため、利用者IDを引数で持ち回っていた。
	 *
	 * いまは押されてから作る。4〜5秒待つことになるが、
	 * その間は画面側でオーバーレイを出している（effects.js）。
	 * 待ち時間は演出で埋められるが、枠は埋められない。
	 *
	 * @param model 画面へ値を渡す入れ物
	 * @return templates/home.html
	 */
	@GetMapping("/")
	public String home(Model model) {

		model.addAttribute("remaining", taskService.countRemaining());

		// 効果音の音量。画面の body に渡し、JavaScript がそこから読む
		// 設定は入れ物ごと渡す。項目が増えても、この行は変わらない
		model.addAttribute("settings", SettingsForm.from(userService.current()));

		return "home";
	}

	/**
	 * ログイン画面を表示する。
	 *
	 * ログインしていない人がアプリを開くと、Spring Security がここへ送る。
	 * ログアウトしたときも、ここへ戻ってくる。
	 *
	 * この画面を自前で用意しているのには理由がある。
	 * Spring Security は、ログインの方法が1つしかないとき
	 * 「選ばせる画面に意味がない」と判断して、ログイン画面を作らず
	 * いきなり提供元（Google）へ送る。
	 * そのため /login が存在せず、ログアウトすると 404 になっていた。
	 *
	 * SecurityConfig の .loginPage("/login") と対になっている。
	 * 片方だけ消すと、ログアウトしたときに行き先が無くなる。
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