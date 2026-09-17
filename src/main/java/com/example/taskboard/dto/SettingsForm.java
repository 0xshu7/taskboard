package com.example.taskboard.dto;

// ===== インポート =====
// java.time.LocalTime … 時刻だけ。画面から来た文字を変換した結果に使う
//
// 以下はこのアプリの部品。
// User … 利用者1人。ここから値を写し、ここへ書き戻す

import java.time.LocalTime;

import com.example.taskboard.domain.User;

/**
 * 設定画面の入れ物。画面の入力欄と、このクラスの項目が1対1で対応する。
 *
  * このクラスは dto パッケージに置いている。
 * データベースの形ではなく、画面の形に合わせた入れ物だから。
 *
 * たとえば通知の時刻は、User では LocalTime だが、ここでは String で持つ。
 * 画面から届く値は必ず文字であり、しかも未入力のときは空文字になる。
 * LocalTime のまま受け取ると、空欄を送った時点で変換に失敗して落ちる。
 * 画面の都合を受け止めるのがこのクラスの役目なので、文字のまま受けている。
 *
 * User を画面に直接つながないのは、そのためでもある。
 * 直接つなぐと、画面の都合が User 側に持ち込まれ、
 * データベースの形が画面に引きずられていく。
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
	// 画面の入力欄と同じ並びにしておくと、突き合わせて確認しやすい。

	/** 表示名 */
	private String displayName;

	/** 通知メールを受け取るかどうか */
	private boolean notifyEnabled;

	/**
	 * 通知する時刻。
	 *
	 * 文字のまま受け取る。LocalTime で受けると、空欄のときに変換で落ちるため。
	 * LocalTime への変換は、下の toTime で自分で行う。
	 */
	private String notifyTime;

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
	 * 画面側で「null なら空文字」のような条件を書かずに済み、
	 * 表示の記述が単純になる。
	 *
	 * @param user いまの利用者
	 * @return 画面へ渡す入れ物
	 */
	public static SettingsForm from(User user) {
		SettingsForm form = new SettingsForm();

		form.setDisplayName(user.getDisplayName());
		form.setNotifyEnabled(user.isNotifyEnabled());

		// 条件 ? A : B は「条件が成り立てば A、そうでなければ B」という書き方。
		// 時刻が未設定なら空文字にする。画面の入力欄が空のまま表示される。
		form.setNotifyTime(user.getNotifyTime() == null ? "" : user.getNotifyTime().toString());

		// 音量はあとから足した列なので、それ以前の行では null になる。
		// そのままでは画面のつまみが動かないため、既定値の50を入れる。
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
		user.setDisplayName(displayName);
		user.setNotifyEnabled(notifyEnabled);
		user.setNotifyTime(toTime(notifyTime));
		user.setSoundVolume(soundVolume);
	}

	/**
	 * "08:00" のような文字を LocalTime に直す。
	 *
	 * 空なら null を返す。null は「通知しない」という意味になる。
	 *
	 * private なので、このクラスの外からは呼べない。
	 * 画面の都合をこのクラスの中だけで処理し、外へ持ち出さないようにしている。
	 *
	 * @param text 画面から来た文字
	 * @return 変換した時刻。空なら null
	 */
	private LocalTime toTime(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		return LocalTime.parse(text);
	}

	// ===== メソッド（getter / setter） =====
	// 画面とのやりとりは、この getter と setter を通して行われる。
	// th:field="*{displayName}" と書くと、表示のときに getDisplayName()、
	// 送信のときに setDisplayName() が呼ばれる。
	// どちらか一方が欠けていると、その欄だけ動かなくなる。

	/** 表示名を返す */
	public String getDisplayName() {
		return displayName;
	}

	/** 表示名を入れる */
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	/** 通知を受け取るかどうかを返す。boolean なので get ではなく is で始める */
	public boolean isNotifyEnabled() {
		return notifyEnabled;
	}

	/** 通知を受け取るかどうかを入れる */
	public void setNotifyEnabled(boolean notifyEnabled) {
		this.notifyEnabled = notifyEnabled;
	}

	/** 通知する時刻を返す。文字のまま */
	public String getNotifyTime() {
		return notifyTime;
	}

	/** 通知する時刻を入れる。文字のまま受ける */
	public void setNotifyTime(String notifyTime) {
		this.notifyTime = notifyTime;
	}

	/** 効果音の音量を返す */
	public Integer getSoundVolume() {
		return soundVolume;
	}

	/** 効果音の音量を入れる */
	public void setSoundVolume(Integer soundVolume) {
		this.soundVolume = soundVolume;
	}
}