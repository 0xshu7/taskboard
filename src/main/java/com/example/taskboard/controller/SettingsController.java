package com.example.taskboard.controller;

// ===== インポート =====
// 以下は Spring の部品。
// Value          … application.yml に書いた値を受け取る
// Controller     … HTMLを返すクラスです、という印
// Model          … 画面へ値を渡すための入れ物
// GetMapping     … そのURLを開いたときに動くメソッドの印
// PostMapping    … そのURLへ送信されたときに動くメソッドの印
// RequestBody    … 送られてきた JSON を Java の入れ物に変換して受け取る
// ResponseBody   … 返した値を、テンプレート名ではなくそのまま応答として返す
//
// 以下はこのアプリの部品。
// SettingsForm               … 設定画面とやりとりする入れ物
// User                       … 利用者1人
// PushSubscriptionRepository … 通知の届け先の出し入れ
// UserService                … 利用者と設定

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.taskboard.domain.User;
import com.example.taskboard.dto.SettingsForm;
import com.example.taskboard.repository.PushSubscriptionRepository;
import com.example.taskboard.service.UserService;

/**
 * 設定画面を受け持つ。
 *
 * 扱うURLは2つ。
 * 表示（GET）  … /settings
 * 保存（POST） … /settings/volume
 *
 * この画面には「保存する」ボタンが無い。
 * 触った時点で反映される形にしてある。
 *
 * 通知のスライドは push.js が /push/subscribe と /push/unsubscribe を呼ぶ。
 * 音量のつまみは、離した時点で /settings/volume を呼ぶ。
 *
 * ボタンを置かないのは、設定が2つしかなく、
 * どちらも変わったことがその場で分かるため。
 * 音量は音が鳴り、通知はスライドの色が変わる。
 * 効いたかどうかが見えていれば、「保存した」と伝える必要がない。
 */
@Controller
public class SettingsController {

	// ===== フィールド =====

	/** 利用者と設定 */
	private final UserService userService;

	/** 通知の届け先。スライドの初期状態を決めるために使う */
	private final PushSubscriptionRepository subscriptionRepository;

	/**
	 * VAPIDの公開鍵。通知の許可をもらうときに、ブラウザへ渡す必要がある。
	 *
	 * 秘密鍵と対になっていて、ブラウザは
	 * 「この鍵の持ち主からの通知だけ受け取る」という約束をする。
	 * 公開鍵なので画面に出しても問題ない。秘密鍵のほうは絶対に出さない。
	 *
	 * @Value は application.yml の値を受け取る仕組み。
	 * yml 側では環境変数から読んでいるので、値そのものはどこにも書かれていない。
	 */
	@Value("${push.public-key}")
	private String vapidPublicKey;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public SettingsController(UserService userService,
			PushSubscriptionRepository subscriptionRepository) {

		this.userService = userService;
		this.subscriptionRepository = subscriptionRepository;
	}

	// ===== メソッド =====

	/**
	 * 設定画面を出す。
	 *
	 * 画面へ渡すのは4つ。
	 *   form        … 入力欄の中身（いまは音量だけ）
	 *   volume      … 効果音の音量。つまみを動かしたときの試し鳴らしに使う
	 *   vapidKey    … 通知の公開鍵。push.js が body から読む
	 *   pushEnabled … 通知のスライドを入にして表示するかどうか
	 *
	 * pushEnabled は、届け先が1件でも登録されているかで決める。
	 * 入切を別の列に持たず、届け先の有無そのものを状態として使っている。
	 * 同じことを2か所に持つと、必ずどこかで食い違うため。
	 *
	 * @param model 画面へ値を渡す入れ物
	 * @return templates/settings.html
	 */
	@GetMapping("/settings")
	public String settings(Model model) {

		User user = userService.current();

		model.addAttribute("form", SettingsForm.from(user));
		model.addAttribute("volume", user.getSoundVolume());
		model.addAttribute("vapidKey", vapidPublicKey);
		model.addAttribute("pushEnabled",
				!subscriptionRepository.findByUserId(user.getId()).isEmpty());

		return "settings";
	}

	/**
	 * 音量を保存する。つまみを離したときに、画面から裏で呼ばれる。
	 *
	 * @ResponseBody を付けているので、返した "ok" がそのまま応答になる。
	 * 付けないと、Spring が ok.html というテンプレートを探しに行って失敗する。
	 *
	 * @RequestBody は、届いた JSON を SettingsForm に詰めて渡す仕組み。
	 * {"soundVolume":50} のような形で送られてくる。
	 *
	 * 画面は切り替わらないので、redirect: も返さない。
	 *
	 * @param form 画面から届いた設定
	 * @return 画面では使わないが、成功したことを示す文字列
	 */
	@PostMapping("/settings/volume")
	@ResponseBody
	public String saveVolume(@RequestBody SettingsForm form) {
		userService.updateSettings(form);
		return "ok";
	}
}