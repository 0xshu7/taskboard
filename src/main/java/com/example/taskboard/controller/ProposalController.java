package com.example.taskboard.controller;

// ===== インポート =====
// 以下は Spring の部品。
// Controller   … HTMLを返すクラスです、という印
// Model        … 画面へ値を渡すための入れ物
// GetMapping   … そのURLを開いたときに動くメソッドの印
// PathVariable … URLの一部（/proposals/7 の 7）を受け取る
// PostMapping  … そのURLへ送信されたときに動くメソッドの印
//
// 以下はこのアプリの部品。
// ProposalSet     … 提案1セット
// ProposalService … スタートから3案、選ぶまでの処理
// UserService     … 利用者と設定。効果音の音量を画面へ渡すために使う

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.taskboard.domain.ProposalSet;
import com.example.taskboard.service.ProposalService;
import com.example.taskboard.service.UserService;

/**
 * スタートを押してから、3案を出し、選ぶまでの画面を受け持つ。
 *
 * 流れは次のとおり。
 * 1. スタートを押す           → POST /proposals
 * 2. 3案が表示される          → GET  /proposals/{setId}
 * 3a. どれかを選ぶ            → POST /proposals/{setId}/choose/{cardId} → タスク一覧へ
 * 3b. 別の案を見る            → POST /proposals/{setId}/retopic        → 新しい3案へ
 *
 * 判断や計算はここには書かない。すべて ProposalService に任せている。
 * このクラスがしているのは、URLと処理を結びつけ、結果の行き先を決めることだけ。
 *
 * 操作のあとは必ず redirect: で移動する。
 * そのまま画面を返すと、ブラウザで再読み込みしたときに
 * 同じ操作がもう一度送られ、3案が二重に作られたりタスクが重複したりする。
 *
 * 他人のセットかどうかの確認は ProposalService.findSet が行う。
 * URLの番号を書き換えても、他人の3案は読めない。
 */
@Controller
public class ProposalController {

	// ===== フィールド =====

	/** スタートから3案、選ぶまでの処理 */
	private final ProposalService proposalService;

	/** 利用者と設定。効果音の音量を画面へ渡すために使う */
	private final UserService userService;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public ProposalController(ProposalService proposalService, UserService userService) {
		this.proposalService = proposalService;
		this.userService = userService;
	}

	// ===== メソッド =====

	/**
	 * スタートボタンを押したとき。3案を出して、その画面へ移る。
	 *
	 * 押されてから作るので、AIの応答を待つ4〜5秒がかかる。
	 * その間は画面側でオーバーレイを出している（effects.js）。
	 *
	 * 以前は画面を開いた時点で裏に1セット作っておき、待ち時間を消していたが、
	 * 1回のスタートでAIを2回呼ぶことになり、無料枠が実質半分になっていた。
	 * 待ち時間は演出で埋められるが、枠は埋められないので、作りおきはやめた。
	 *
	 * @return できたセットの画面へ移る
	 */
	@PostMapping("/proposals")
	public String start() {

		ProposalSet set = proposalService.start();

		return "redirect:/proposals/" + set.getId();
	}

	/**
	 * 3案を表示する。
	 *
	 * セットとカードを別々に渡している。
	 * カードは ProposalSet の中に持たせず、IDでつないでいるため、
	 * 取り出すのも別々になる。
	 *
	 * findSet を先に呼んでいる。
	 * 他人のセットならそこで例外になるので、カードを読む前に止まる。
	 *
	 * @param setId 表示するセットのID
	 * @param model 画面へ値を渡す入れ物
	 * @return templates/proposal.html
	 */
	@GetMapping("/proposals/{setId}")
	public String show(@PathVariable Long setId, Model model) {

		model.addAttribute("set", proposalService.findSet(setId));
		model.addAttribute("cards", proposalService.findCards(setId));

		// 効果音の音量。画面の body に渡し、JavaScript がそこから読む
		model.addAttribute("volume", userService.current().getSoundVolume());

		return "proposal";
	}

	/**
	 * カードを選んだとき。タスクになるので一覧へ移る。
	 *
	 * URLに2つの値が入っている。
	 * /proposals/7/choose/21 なら、setId に 7、cardId に 21 が入る。
	 * カードのIDだけでもセットは分かるが、
	 * URLを見ただけでどのセットの話か分かるようにしている。
	 *
	 * 選んだあとは提案画面に戻らず、タスク一覧へ移る。
	 * 選んだ時点で決まっているので、もう一度3案を見せる意味がない。
	 *
	 * @param setId  セットのID
	 * @param cardId 選ばれたカードのID
	 * @return タスク一覧へ移る
	 */
	@PostMapping("/proposals/{setId}/choose/{cardId}")
	public String choose(@PathVariable Long setId, @PathVariable Long cardId) {

		proposalService.choose(setId, cardId);
		return "redirect:/tasks";
	}

	/**
	 * 「別の案を見る」を押したとき。新しいセットを作ってそちらへ移る。
	 *
	 * 閉じたセットも記録として残る。
	 * 何が選ばれなかったかは、提案を良くするための手がかりになる。
	 *
	 * ここでもAIを1回呼ぶので、押すたびに無料枠が減る。
	 *
	 * @param setId 閉じるセットのID
	 * @return 新しい3案の画面へ移る
	 */
	@PostMapping("/proposals/{setId}/retopic")
	public String retopic(@PathVariable Long setId) {

		ProposalSet next = proposalService.retopic(setId);

		return "redirect:/proposals/" + next.getId();
	}
}