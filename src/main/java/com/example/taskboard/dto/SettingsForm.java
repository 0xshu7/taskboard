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
 * 【Boolean（大文字）にしている理由】
 *
 * 画面から届く JSON に、項目が入っていないことがありうる。
 * boolean（小文字）で受けると、そのとき false が入ってしまい、
 * 「切にされた」のか「何も言われていない」のかを見分けられない。
 *
 * Boolean なら、何も届かなかったときは null のまま残る。
 * その違いを apply で使っている。下の説明を見ること。
 *
 * 【getter が get で始まっている理由】
 *
 * 「はい・いいえ」を表す項目の getter は is で始める、と言われることが多いが、
 * それが通るのは boolean（小文字）のときだけ。
 *
 * Boolean に対して isXxx() と書くと、読む仕組みによって拾えたり拾えなかったりする。
 *
 * 　Thymeleaf（Spring の式）… 拾う
 * 　Jackson（JSONに変換）  … 拾わない
 * 　標準の JavaBeans       … 拾わない
 *
 * いまは画面に出すだけなので動くが、
 * この入れ物を JSON で返す場面が出た瞬間、項目が黙って消える。
 * get で書いておけば、どの仕組みからも拾われる。
 *
 * User 側の isSoundEnabled() は boolean（小文字）を返すので、あちらは is で正しい。
 * 同じ名前に見えて、型が違えば決まりも変わる。
 *
 * アノテーションは付いていない。
 * テーブルに対応しないので @Entity は不要で、
 * Spring に管理させる必要もないので @Component なども付けない。
 * 画面ごとに毎回 new される、ただの入れ物として扱う。
 */
public class SettingsForm {

	// ===== フィールド =====

	/** 効果音を鳴らすか。届かないことがあるので null を入れられる Boolean にする */
	private Boolean soundEnabled;

	/** 紙ふぶきを出すか。同じく Boolean */
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
	 * 【届かなかった項目は触らない】
	 *
	 * null のときは何もしない。「切にされた」のではなく
	 * 「何も言われていない」ので、いまの設定をそのまま残す。
	 *
	 * 渡してしまうと、User の setter が null を既定値（true）に直すため、
	 * 送っていない項目が勝手に入になる。
	 *
	 * 　{"soundEnabled": false} だけを送る
	 * 　→ 紙ふぶきが入になる
	 *
	 * 画面の JavaScript は必ず2つまとめて送るので、ふだんは起きない。
	 * ただ、Boolean（大文字）にしたのは
	 * 「false」と「何も届かなかった」を見分けるためなので、
	 * 見分けておきながら同じ扱いにするのでは、型を選んだ意味がなくなる。
	 *
	 * 値そのものの確認は User の setter が持っている。
	 * ここがするのは「渡すかどうか」の判断だけ。
	 *
	 * @param user 書き戻す先の利用者
	 */
	public void apply(User user) {

		if (soundEnabled != null) {
			user.setSoundEnabled(soundEnabled);
		}

		if (confettiEnabled != null) {
			user.setConfettiEnabled(confettiEnabled);
		}
	}

	// ===== メソッド（getter / setter） =====
	// 画面とのやりとりは、この getter と setter を通して行われる。
	//
	// 画面の ${settings.soundEnabled} も、JSON を読み込む仕組みも、
	// 名前から getter と setter を探して値を出し入れする。
	// 名前が合っていないと、エラーにならずに空のままになる。

	/** 効果音を鳴らすかを返す。画面から届かなかったときは null */
	public Boolean getSoundEnabled() {
		return soundEnabled;
	}

	/** 効果音を鳴らすかを入れる */
	public void setSoundEnabled(Boolean soundEnabled) {
		this.soundEnabled = soundEnabled;
	}

	/** 紙ふぶきを出すかを返す。画面から届かなかったときは null */
	public Boolean getConfettiEnabled() {
		return confettiEnabled;
	}

	/** 紙ふぶきを出すかを入れる */
	public void setConfettiEnabled(Boolean confettiEnabled) {
		this.confettiEnabled = confettiEnabled;
	}
}