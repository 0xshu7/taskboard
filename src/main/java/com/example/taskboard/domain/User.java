package com.example.taskboard.domain;

// ===== インポート =====
// java.time.LocalDateTime … 日付＋時刻。登録日時に使う
//
// 以下は JPA の部品。
// Entity         … このクラスはテーブルに対応する、という印
// Table          … 対応するテーブル名を指定する
// Id             … 主キーの印
// GeneratedValue … 番号を自動で振らせる
// GenerationType … その振り方の種類（IDENTITY を使う）
// Column         … 列名・空の可否・重複の可否・文字数の上限を指定する

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 利用者1人。users テーブルの1行にあたる。
 *
 * Googleでログインした人ごとに1行できる。
 * 初めてログインした人は、そのときに登録される。
 *
 * このアプリは個人情報を持たない。
 * Googleに要求しているのは openid だけなので、受け取るのは sub という識別子1つ。
 * メールアドレスも名前も送られてこないし、保存もしない。
 *
 * 持っているのは4つだけ。
 *   id           … 内部の番号
 *   googleSub    … ログインした人を見分ける鍵
 *   createdAt    … いつ登録されたか
 *   soundVolume  … 効果音の音量
 *
 * 通知を受け取るかどうかは、ここには持たない。
 * push_subscriptions に届け先の行があるかどうかが、そのまま入切を表す。
 * 同じことを2か所に持つと、必ずどこかで食い違う。
 *
 * タスクや提案セットは user_id でこの行を指している。
 * 誰のデータかは、すべてこの id で決まる。
 */

// ===== アノテーション =====
// @Entity … このクラスはテーブルに対応する、という宣言
// @Table  … 対応するテーブル名。users は SQL の予約語ではないのでそのまま使える

@Entity
@Table(name = "users")
public class User {

	// ===== フィールド =====

	/** 主キー。保存する前は番号が未定なので、null を入れられる Long にする */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * Google が発行する、その人を表す固定のID。
	 *
	 * このアプリが持つ唯一の識別子。
	 * 文字列そのものからは誰か分からず、Google に問い合わせても答えは返らない。
	 *
	 * unique = true は「同じ値を2行に入れられない」という制限。
	 * 同じ人が2人として登録されるのを、データベース側で防いでいる。
	 */
	@Column(name = "google_sub", nullable = false, unique = true, length = 64)
	private String googleSub;

	/** 登録日時。new した時点の時刻がそのまま入る */
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	/**
	 * 効果音の音量。0〜100。0 で無音。
	 *
	 * int ではなく Integer にしている。あとから足した列なので、
	 * それ以前からある行では null になる。int では受け取れない。
	 */
	@Column(name = "sound_volume")
	private Integer soundVolume = 50;

	// ===== コンストラクタ =====
	// 自分でコンストラクタを1つでも書くと、Java が自動で用意する
	// 「引数なしのコンストラクタ」が消える。JPA はそれを必要とするので、手で書き足している。

	/**
	 * 引数なしのコンストラクタ。
	 * データベースから読み込むときに JPA が使う。
	 * ふつうのコードから呼ばれないよう protected にしてある。
	 */
	protected User() {
	}

	/**
	 * 新しい利用者を作る。
	 *
	 * 受け取るのは識別子だけ。他に決めることが何も無い。
	 * googleSub は最初に1回だけ入れて、あとは変えない値なので、
	 * setter ではなくコンストラクタで受け取る。
	 *
	 * @param googleSub Google が発行する固定のID
	 */
	public User(String googleSub) {
		this.googleSub = googleSub;
	}

	// ===== メソッド =====
	// setter は「変えてよいもの」にだけ用意している。
	// googleSub に setter が無いのは、途中で書き換えられると困る値だから。
	// 用意しないことが、そのまま「変えるな」という意思表示になる。

	/** 主キーを返す */
	public Long getId() {
		return id;
	}

	/** Google のアカウント識別子を返す。setter は用意しない（途中で変えない値） */
	public String getGoogleSub() {
		return googleSub;
	}

	/** 効果音の音量を返す。画面の data-volume に渡している */
	public Integer getSoundVolume() {
		return soundVolume;
	}

	/** 効果音の音量を入れる */
	public void setSoundVolume(Integer soundVolume) {
		this.soundVolume = soundVolume;
	}

	/** 登録日時を返す */
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}