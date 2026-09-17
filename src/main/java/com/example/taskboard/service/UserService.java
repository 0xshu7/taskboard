package com.example.taskboard.service;

// ===== インポート =====
// 以下は Spring の部品。
// Service       … 処理のまとまりです、という印。Spring が管理下に置く
// Transactional … メソッドの途中で失敗したら、その中の保存をすべて取り消す
//
// 以下は Spring Security の部品。
// Authentication        … いまログインしている人の情報をまとめたもの
// SecurityContextHolder … その情報を取り出す窓口。どこからでも呼べる
// OAuth2User            … Googleログインで受け取った利用者情報
//
// 以下はこのアプリの部品。
// SettingsForm   … 設定画面とやりとりする入れ物
// User           … 利用者1人
// UserRepository … 利用者の出し入れ

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskboard.domain.User;
import com.example.taskboard.dto.SettingsForm;
import com.example.taskboard.repository.UserRepository;

/**
 * ログインしている利用者を取ってくる係。
 *
 * 「いまの利用者は誰か」を知っているのはこのクラスだけにしてある。
 * 他のクラスは current() か currentId() を呼ぶだけで、
 * Googleログインの仕組みを知らずに済む。
 * ログインの方式を変えても、直すのはここだけになる。
 *
 * 初めてログインした人は、その場で登録する。
 * 登録画面を別に作らないのは、入力してもらうものが何も無いため。
 *
 * Googleから受け取るのは sub という識別子だけ。
 * application.yml で scope を openid だけにしているので、
 * メールアドレスも名前も送られてこない。
 *
 * sub を鍵にしているのは、それが変わらないため。
 * メールアドレスは本人が変更できるので、鍵にすると別人として扱われてしまう。
 */
@Service
public class UserService {

	// ===== フィールド =====

	/** 利用者の出し入れ */
	private final UserRepository userRepository;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	// ===== メソッド =====

	/**
	 * いまログインしている利用者を返す。初めての人ならその場で登録する。
	 *
	 * orElseGet は「見つかったらそれを返し、無ければ指定した処理を動かす」という書き方。
	 * 似たものに orElse があるが、そちらは見つかった場合でも中の処理が動いてしまう。
	 * ここで orElse を使うと、毎回むだに登録処理が走ることになる。
	 *
	 * @return いまログインしている利用者
	 * @throws IllegalStateException ログインしていないとき
	 */
	@Transactional
	public User current() {
		String googleSub = currentPrincipal().getAttribute("sub");

		return userRepository.findByGoogleSub(googleSub)
				.orElseGet(() -> userRepository.save(new User(googleSub)));
	}

	/**
	 * いまログインしている利用者のIDを返す。
	 *
	 * タスクや提案セットは user_id でこのIDを指している。
	 * 他のサービスは、このメソッドを通してIDを受け取る。
	 *
	 * @return 利用者のID
	 */
	@Transactional
	public Long currentId() {
		return current().getId();
	}

	/**
	 * 設定を保存する。
	 *
	 * いま保存できるのは効果音の音量だけ。
	 * 通知の入切は、届け先を登録するか消すかで決まるので、ここでは扱わない。
	 *
	 * 設定項目を増やすときに触るのは3か所。
	 * 1. User に列を足す
	 * 2. SettingsForm に項目を足す（from と apply にも1行ずつ）
	 * 3. settings.html に入力欄を足す
	 *
	 * @param form 画面から受け取った入れ物
	 */
	@Transactional
	public void updateSettings(SettingsForm form) {
		User user = current();
		form.apply(user);
		userRepository.save(user);
	}

	// ===== メソッド（内部で使うもの） =====

	/**
	 * いまログインしている人の情報を取り出す。
	 *
	 * SecurityContextHolder は、いまの要求を処理している間ずっと
	 * 「誰がログインしているか」を持っている入れ物。
	 * 引数で受け渡さなくても、どこからでも取り出せる。
	 *
	 * instanceof のあとに変数名を書くと、型の確認と代入を同時にできる。
	 * 確認してから改めてキャストする2行を、1行にまとめられる。
	 *
	 * ログインしていなければ例外を投げて、その場で止める。
	 * null を返すと、受け取った側が確認を忘れたときに
	 * ずっと先で分かりにくい形で失敗するため。
	 *
	 * @return Googleから受け取った利用者情報
	 * @throws IllegalStateException ログインしていないとき
	 */
	private OAuth2User currentPrincipal() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth == null || !(auth.getPrincipal() instanceof OAuth2User principal)) {
			throw new IllegalStateException("ログインしていません");
		}
		return principal;
	}
}