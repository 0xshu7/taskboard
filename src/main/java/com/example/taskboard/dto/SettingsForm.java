package com.example.taskboard.dto;

// ===== インポート =====
// 以下はこのアプリの部品。
// User … 利用者1人。ここから値を写し、ここへ書き戻す

import com.example.taskboard.domain.User;

/**
 * 設定画面の入れ物。画面の入力欄と、このクラスの項目が1対1で対応する。
 *
 * このクラスは dto パッケージに置いている。
 * データベースの形ではなく、画面の形に合わせた入れ物だから。
 *
 * User を画面に直接つながないのは、そのためでもある。
 * 直接つなぐと、画面の都合が User 側に持ち込まれ、
 * データベースの形が画面に引きずられていく。
 *
 * いま持っているのは音量だけ。
 * 通知の入切は届け先の有無で決まるので、この入れ物には入っていない。
 * 項目が1つでもこのクラスを残しているのは、増えたときの置き場所を決めておくため。
 *
 * 【項目を増やすとき】
 *   1. User に列を足す
 *   2. このクラスに項目を足す（from と apply にも1行ずつ）
 *   3. settings.html に入力欄を1つ足す
 *   コントローラとサービスは触らなくてよい。
 *
 * アノテーションは付いていない。
 * テーブルに対応しないので @Entity は不要で、
 * Spring に管理させる必要もないので @Component なども付けない。
 * 画面ごとに毎回 new される、ただの入れ物として扱う。
 */
public class SettingsForm {

	// ===== フィールド =====

	/** 効果音の音量。0〜100 */
	private Integer soundVolume;

	// ===== メソッド（写す） =====

	/**
	 * いまの設定を、画面用に写す。データベース側から画面側への向き。
	 *
	 * static が付いているので、SettingsForm.from(user) のようにクラス名から呼ぶ。
	 * まだ入れ物が存在しない段階で使うため、この形にしている。
	 *
	 * null のときの扱いをここで済ませている。
	 * 画面側で条件を書かずに済み、表示の記述が単純になる。
	 *
	 * @param user いまの利用者
	 * @return 画面へ渡す入れ物
	 */
	public static SettingsForm from(User user) {
		SettingsForm form = new SettingsForm();

		// 音量はあとから足した列なので、それ以前の行では null になる。
		// そのままでは画面のつまみが動かないため、既定値の50を入れる。
		// 条件 ? A : B は「条件が成り立てば A、そうでなければ B」という書き方
		form.setSoundVolume(user.getSoundVolume() == null ? 50 : user.getSoundVolume());

		return form;
	}

	/**
	 * 画面で入力された値を、User に書き戻す。画面側からデータベース側への向き。
	 *
	 * from と apply で、行き帰りの2方向がそろう。
	 * 写す作業をこの2つに集めておくと、項目が増えても直す場所が分かりやすい。
	 *
	 * @param user 書き戻す先の利用者
	 */
	public void apply(User user) {
		user.setSoundVolume(soundVolume);
	}

	// ===== メソッド（getter / setter） =====
	// 画面とのやりとりは、この getter と setter を通して行われる。
	// th:field="*{soundVolume}" と書くと、表示のときに getSoundVolume()、
	// 送信のときに setSoundVolume() が呼ばれる。
	// どちらか一方が欠けていると、その欄だけ動かなくなる。

	/** 効果音の音量を返す */
	public Integer getSoundVolume() {
		return soundVolume;
	}

	/** 効果音の音量を入れる */
	public void setSoundVolume(Integer soundVolume) {
		this.soundVolume = soundVolume;
	}
}