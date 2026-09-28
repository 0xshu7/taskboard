package com.example.taskboard.controller;

// ===== インポート =====
// 以下は Spring の部品。
// Controller   … HTMLを返すクラスです、という印
// Model        … 画面へ値を渡すための入れ物
// GetMapping   … そのURLを開いたときに動くメソッドの印
// PostMapping  … そのURLへ送信されたときに動くメソッドの印
// RequestParam … 送られてきた入力を1つずつ受け取る
//
// 以下はこのアプリの部品。
// ProposalSet     … 提案1セット
// ProposalService … スタートから3案、選ぶまでの処理
// UserService     … 利用者と、最初の3択の答え

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.taskboard.domain.ProposalSet;
import com.example.taskboard.service.ProposalService;
import com.example.taskboard.service.UserService;

/**
 * 最初の3問を受け持つ。
 *
 * 使う場面は2つあるが、画面は1つしかない。
 * 　初回     … スタートを押したとき、まだ答えていなければここへ送られる
 * 　答え直し … 設定の「答え直す」で消したあと、次のスタートでここへ来る
 *
 * 【1問ずつ出す】
 *
 * 3問を1画面に並べると、選択肢が9個並ぶ。
 * このアプリは3つから選ぶものなので、聞くときも3つずつにしている。
 *
 * 途中の答えは、次の画面に隠し項目で持ち越す。
 * セッションに置くこともできるが、
 * 　・途中で離脱した人の答えがサーバーに残る
 * 　・「いつ消すか」を決める必要が出る
 * ので、画面と一緒に運ぶ形にした。サーバーは何も覚えない。
 *
 * 答えたあとはスタート画面に戻らず、そのまま3案を出す。
 * 3回押したのに、もう一度スタートを押させる意味がない。
 *
 * 答えは AI には渡らない。
 * 分野の点数、2件目の分数、3件目の選び方という、
 * アプリ側の判断の材料になるだけ。
 */
@Controller
public class ProfileController {

	// ===== フィールド =====

	/** 利用者と、最初の3択の答え */
	private final UserService userService;

	/** 答えたあと、そのまま3案を出すために使う */
	private final ProposalService proposalService;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public ProfileController(UserService userService, ProposalService proposalService) {
		this.userService = userService;
		this.proposalService = proposalService;
	}

	// ===== メソッド =====

	/**
	 * 1問目を表示する。
	 *
	 * @param model 画面へ値を渡す入れ物
	 * @return templates/profile.html
	 */
	@GetMapping("/profile")
	public String step1(Model model) {

		model.addAttribute("step", 1);
		return "profile";
	}

	/**
	 * 1問目に答えたとき。2問目を表示する。
	 *
	 * 受け取った答えをそのまま画面へ返し、隠し項目として次へ運んでもらう。
	 *
	 * @param mainUse 1問目の答え
	 * @param model   画面へ値を渡す入れ物
	 * @return templates/profile.html
	 */
	@PostMapping("/profile/step2")
	public String step2(@RequestParam String mainUse, Model model) {

		model.addAttribute("step", 2);
		model.addAttribute("mainUse", mainUse);

		return "profile";
	}

	/**
	 * 2問目に答えたとき。3問目を表示する。
	 *
	 * @param mainUse   1問目の答え
	 * @param orderPref 2問目の答え
	 * @param model     画面へ値を渡す入れ物
	 * @return templates/profile.html
	 */
	@PostMapping("/profile/step3")
	public String step3(@RequestParam String mainUse,
			@RequestParam String orderPref,
			Model model) {

		model.addAttribute("step", 3);
		model.addAttribute("mainUse", mainUse);
		model.addAttribute("orderPref", orderPref);

		return "profile";
	}

	/**
	 * 3問目に答えたとき。まとめて保存して、そのまま3案を出す。
	 *
	 * 答えた直後はタスクが1件も無いことが多い。
	 * いままでその場面がいちばん弱かったが、
	 * 3つの答えが材料になるので、何も知らない状態ではなくなる。
	 *
	 * @param mainUse    主な用途
	 * @param orderPref  すすめ方
	 * @param focusLevel 集中時間
	 * @return できたセットの画面へ移る
	 */
	@PostMapping("/profile")
	public String save(@RequestParam String mainUse,
			@RequestParam String orderPref,
			@RequestParam String focusLevel) {

		userService.saveProfile(mainUse, orderPref, focusLevel);

		ProposalSet set = proposalService.start();

		return "redirect:/proposals/" + set.getId();
	}

	/**
	 * 「答え直す」を押したとき。答えを消して設定画面に戻る。
	 *
	 * ここでは質問画面へ送らない。
	 * 次にスタートを押したときに、関所（ProposalController.start）が
	 * 自動でここへ送る。同じ判定を2か所に持たずに済む。
	 *
	 * @return 設定画面へ戻る
	 */
	@PostMapping("/profile/reset")
	public String reset() {

		userService.resetProfile();

		return "redirect:/settings";
	}
}