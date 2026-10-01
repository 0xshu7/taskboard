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
 * 利用者に関わることをまとめる場所。
 *
 * 担当することは3つ。
 * 1. いまログインしている人を返す（current / currentId）
 * 2. 設定を保存する（updateSettings）
 * 3. 最初の3問の答えを保存・削除する（saveProfile / resetProfile）
 *
 * 【ログインの仕組みを知っているのは、このクラスだけ】
 *
 * 他のクラスは current() か currentId() を呼ぶだけで、
 * Googleログインの仕組みを知らずに済む。
 * ログインの方式を変えても、直すのはここだけになる。
 *
 * TaskService も ProposalService も PushSubscriptionService も、
 * 「いまの利用者は誰か」を自分では調べない。
 * 調べ方が3か所に散ると、1つ直し忘れたときに
 * 「ある画面だけ他人のデータが見える」という形で現れる。
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
 *
 * 【current() が1画面で何度も呼ばれる】
 *
 * タスク一覧を1枚出すのに、8回ほど呼ばれている。
 * 画面へ設定を渡すとき、タスクを取るとき、件数を数えるとき、と
 * それぞれが「いまの利用者」を必要とするため。
 * そのたびに users を検索しているので、同じ問い合わせが8回走る。
 *
 * 直す方法はある。1回の要求のあいだ、結果を覚えておけばよい。
 * それでも直していないのは、2つの理由による。
 *
 * 　1. 利用者が数人の段階では、速さの差が出ない
 * 　2. 覚えておく仕組みを入れると、「いつ忘れるか」を決める必要が出る。
 * 　　 ログアウトや答え直しのあとに古い値が残ると、
 * 　　 「設定を変えたのに反映されない」という形で現れる
 *
 * 見つけていないのではなく、いまは直さないと決めている。
 * 遅さが実際に目に見えたときに直す。
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
	 * 初めての人の要求が同時に2つ届くと、両方が「無い」と判断して
	 * 2回登録しようとすることがある。
	 * users.google_sub に unique を付けてあるので、データは壊れず、
	 * 片方が失敗するだけで済む。開き直せば直る。
	 * 初回ログインの一瞬にしか起きないので、ここでは何もしていない。
	 *
	 * @return いまログインしている利用者
	 * @throws IllegalStateException ログインしていないとき
	 */
	@Transactional
	public User current() {

		String googleSub = currentPrincipal().getAttribute("sub");

		// sub が来ないことは、設定が正しければ起きない。
		// ただし来なかった場合、findByGoogleSub(null) は必ず空振りするため、
		// 呼ばれるたびに新しい利用者が作られ続ける。
		// タスクが消えたように見えて、原因が分かりにくい。ここで止める。
		if (googleSub == null || googleSub.isBlank()) {
			throw new IllegalStateException("Googleから識別子を受け取れませんでした");
		}

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
	 * いま保存できるのは効果音と紙ふぶきの入切。
	 * 通知の入切は、届け先を登録するか消すかで決まるので、ここでは扱わない。
	 *
	 * 値を写すのは SettingsForm.apply の仕事で、ここは呼ぶだけ。
	 * 範囲や null の確認も User の setter が持っている。
	 * 項目が増えても、このメソッドは変わらない。
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

	// ===== メソッド（最初の3択） =====

	/**
	 * 最初の3問の答えを保存する。
	 *
	 * 3つまとめて受け取る。1つずつ保存できるようにすると、
	 * 途中まで答えた状態が生まれ、hasProfile の判定が当てにならなくなる。
	 *
	 * 答えた時刻も一緒に入る（User.saveProfile）。
	 * その時刻が、分野ごとの完了率を数えはじめる起点になる。
	 * 別々に入れる形にすると、片方だけ忘れたときに
	 * 「答え直したのに提案が変わらない」という形で現れる。
	 *
	 * 値の正しさはここでは確かめていない。
	 * 画面のボタンは決まった値しか送らないが、直接送れば何でも入る。
	 * 想定外の値が入っても、SituationJudge は「知らない分野」として
	 * 点数を足さずに流すので、壊れずに既定の動きになる。
	 * 止めるより、変な値でも動き続けるほうがこの画面には合っている。
	 *
	 * @param mainUse    主な用途。べんきょう / せいかつ / リラックス
	 * @param orderPref  すすめ方。EASY / DUE / OLD
	 * @param focusLevel 集中時間。SHORT / MIDDLE / LONG
	 */
	@Transactional
	public void saveProfile(String mainUse, String orderPref, String focusLevel) {

		User user = current();

		user.saveProfile(mainUse, orderPref, focusLevel);

		userRepository.save(user);
	}

	/**
	 * 最初の3問の答えを消す。
	 *
	 * 設定の「やりなおす」から呼ばれる。
	 * 次にスタートを押したとき、質問から始まる。
	 *
	 * 消す手順そのものは User.resetProfile が持っている。
	 * ここは「誰のものを消すか」を決めて保存するだけ。
	 *
	 * 答えた時刻は消さない。
	 * 答え直したときに saveProfile が新しい時刻を入れるので、
	 * ここで消すと、答え直すまでのあいだ起点が無い状態になる。
	 */
	@Transactional
	public void resetProfile() {

		User user = current();

		user.resetProfile();

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
	 * private なので、このクラスの外からは呼べない。
	 * Spring Security の型（OAuth2User）が他のクラスへ漏れないようにしている。
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