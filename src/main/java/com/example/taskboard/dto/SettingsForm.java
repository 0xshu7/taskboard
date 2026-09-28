package com.example.taskboard.dto;

// ===== インポート =====
// 以下はこのアプリの部品。
// User … 利用者1人。ここから値を写し、ここへ書き戻す

import com.example.taskboard.domain.User;

/**
 * 設定画面の入れ物。画面のスイッチと、このクラスの項目が1対1で対応する。
 *
 * このクラスは dto パッケージに置いている。
 * データベースの形ではなく、画面の形に合わせた入れ物だから。
 *
 * User を画面に直接つながないのは、そのためでもある。
 * 直接つなぐと、画面の都合が User 側に持ち込まれ、
 * データベースの形が画面に引きずられていく。
 *
 * いま持っているのは効果音と紙ふぶきの2つ。
 * 通知の入切は届け先の有無で決まるので、この入れ物には入っていない。
 *
 * 【項目を増やすとき】
 *   1. User に列を足す
 *   2. このクラスに項目を足す（from と apply にも1行ずつ）
 *   3. settings.html にスイッチを1つ足す
 *   コントローラとサービスは触らなくてよい。
 *
 * 画面へは、このクラスをまるごと settings という名前で渡している。
 * 項目ごとにモデルへ詰めると、増やすたびに4つのコントローラを触ることになる。
 * 1つの入れ物にしておけば、コントローラの行は増えない。
 *
 * アノテーションは付いていない。
 * テーブルに対応しないので @Entity は不要で、
 * Spring に管理させる必要もないので @Component なども付けない。
 * 画面ごとに毎回 new される、ただの入れ物として扱う。
 */
public class SettingsForm {

	// ===== フィールド =====

	/** 効果音を鳴らすか */
	private Boolean soundEnabled;

	/** 紙ふぶきを出すか */
	private Boolean confettiEnabled;

	// ===== メソッド（写す） =====

	/**
	 * いまの設定を、画面用に写す。データベース側から画面側への向き。
	 *
	 * static が付いているので、SettingsForm.from(user) のようにクラス名から呼ぶ。
	 * まだ入れ物が存在しない段階で使うため、この形にしている。
	 *
	 * null の扱いは User 側で済んでいる。
	 * isSoundEnabled() は列が空でも true を返すので、ここでは条件を書かない。
	 *
	 * @param user いまの利用者
	 * @return 画面へ渡す入れ物
	 */
	public static SettingsForm from(User user) {

		SettingsForm form = new SettingsForm();

		form.setSoundEnabled(user.isSoundEnabled());
		form.setConfettiEnabled(user.isConfettiEnabled());

		return form;
	}

	/**
	 * 画面で入力された値を、User に書き戻す。画面側からデータベース側への向き。
	 *
	 * from と apply で、行き帰りの2方向がそろう。
	 * 写す作業をこの2つに集めておくと、項目が増えても直す場所が分かりやすい。
	 *
	 * 範囲や null の確認は User 側の setter が持っている。
	 * ここは写すだけで、判断はしない。
	 *
	 * @param user 書き戻す先の利用者
	 */
	public void apply(User user) {
		user.setSoundEnabled(soundEnabled);
		user.setConfettiEnabled(confettiEnabled);
	}

	// ===== メソッド（getter / setter） =====
	// 画面とのやりとりは、この getter と setter を通して行われる。
	//
	// boolean の getter は is で始める決まりになっている。
	// Thymeleaf の ${settings.soundEnabled} も、JSON を読み込む仕組みも、
	// この名前を探しにいく。get で書くと拾われないことがある。

	/** 効果音を鳴らすかを返す */
	public Boolean isSoundEnabled() {
		return soundEnabled;
	}

	/** 効果音を鳴らすかを入れる */
	public void setSoundEnabled(Boolean soundEnabled) {
		this.soundEnabled = soundEnabled;
	}

	/** 紙ふぶきを出すかを返す */
	public Boolean isConfettiEnabled() {
		return confettiEnabled;
	}

	/** 紙ふぶきを出すかを入れる */
	public void setConfettiEnabled(Boolean confettiEnabled) {
		this.confettiEnabled = confettiEnabled;
	}
}