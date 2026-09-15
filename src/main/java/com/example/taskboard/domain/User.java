package com.example.taskboard.domain;

// ===== インポート =====
// java.time.LocalDate     … 日付だけ。最後に通知した日に使う
// java.time.LocalDateTime … 日付＋時刻。登録日時に使う
// java.time.LocalTime     … 時刻だけ（08:00）。通知する時刻に使う
//
// 以下は JPA の部品。
// Entity         … このクラスはテーブルに対応する、という印
// Table          … 対応するテーブル名を指定する
// Id             … 主キーの印
// GeneratedValue … 番号を自動で振らせる
// GenerationType … その振り方の種類（IDENTITY を使う）
// Column         … 列名・空の可否・重複の可否・文字数の上限を指定する

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
 * 役割は2つ。
 * 1. 設定（通知・音量・表示名）の保存先
 * 2. ログインした人を見分ける鍵（googleSub）を持つこと
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
	 * メールアドレス。
	 *
	 * unique = true は「同じ値を2行に入れられない」という制限。
	 * 同じメールで2人登録されるのを、データベース側で防いでいる。
	 */
	@Column(nullable = false, unique = true, length = 255)
	private String email;

	/**
	 * パスワードを BCrypt で変換したもの。まだ未使用。
	 *
	 * 生のパスワードは保存しない。変換したものだけを持ち、
	 * ログイン時は「入力を同じ方法で変換して、一致するか」を見る。
	 * Googleログインだけの人は null のまま。
	 */
	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	/**
	 * Google が発行する、その人を表す固定のID。
	 *
	 * メールアドレスは本人が変更できるが、これは変わらない。
	 * だから「同じ人かどうか」の判定にはこちらを使う。
	 */
	@Column(name = "google_sub", unique = true, length = 64)
	private String googleSub;

	/** 画面に出す名前。設定画面で変えられる */
	@Column(name = "display_name", nullable = false, length = 50)
	private String displayName;

	/** 初回ヒアリングが終わっているか。F-02 を作るときに使う */
	@Column(nullable = false)
	private boolean onboarded = false;

	/** 登録日時。new した時点の時刻がそのまま入る */
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	/** 通知メールを送るかどうか */
	@Column(name = "notify_enabled")
	private boolean notifyEnabled = false;

	/** 通知を送る時刻。通知しないときは null。時刻だけなので LocalTime */
	@Column(name = "notify_time")
	private LocalTime notifyTime;

	/** 通知先。空なら email に送る */
	@Column(name = "notify_email", length = 255)
	private String notifyEmail;

	/** 最後に通知した日。同じ日に2回送らないための印 */
	@Column(name = "last_notified_date")
	private LocalDate lastNotifiedDate;

	/**
	 * 効果音の音量。0〜100。0 で無音。
	 *
	 * int ではなく Integer にしている。あとから足した列なので、
	 * それ以前からある行では null になる。int では受け取れない。
	 *
	 * 通知まわりの列も含め、あとから足した列には nullable = false を付けていない。
	 * 既にデータがある表に NOT NULL の列は追加できないため。
	 */
	@Column(name = "sound_volume")
	private Integer soundVolume = 50;

	// ===== コンストラクタ =====
	// Task には書いていないが、こちらには3つある。
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
	 * email と displayName は必須（NOT NULL）なので、ここで必ず受け取る。
	 */
	public User(String email, String displayName) {
		this.email = email;
		this.displayName = displayName;
	}

	/**
	 * Googleログインから新しい利用者を作る。
	 *
	 * googleSub は最初に1回だけ入れて、あとは変えない値なので、
	 * setter ではなくコンストラクタで受け取る。
	 *
	 * @param googleSub   Google が発行する固定のID
	 * @param email       Google アカウントのメールアドレス
	 * @param displayName Google アカウントの表示名
	 */
	public User(String googleSub, String email, String displayName) {
		this.googleSub = googleSub;
		this.email = email;
		this.displayName = displayName;
	}

	// ===== メソッド =====
	// setter は「変えてよいもの」にだけ用意している。
	// email と googleSub に setter が無いのは、途中で書き換えられると困る値だから。
	// 用意しないことが、そのまま「変えるな」という意思表示になる。
	//
	// boolean の getter は get ではなく is で始める。
	// Thymeleaf も Spring も isXxx() を探しに行くので、get だと画面から読めない。

	/** 主キーを返す */
	public Long getId() {
		return id;
	}

	/** メールアドレスを返す。setter は用意しない（途中で変えない値） */
	public String getEmail() {
		return email;
	}

	/** Google のアカウント識別子を返す。setter は用意しない（途中で変えない値） */
	public String getGoogleSub() {
		return googleSub;
	}

	/** 表示名を返す */
	public String getDisplayName() {
		return displayName;
	}

	/** 表示名を入れる。設定画面から変えられるので setter が要る */
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	/** 初回ヒアリングが終わっているかを返す */
	public boolean isOnboarded() {
		return onboarded;
	}

	/** 初回ヒアリングの完了を記録する */
	public void setOnboarded(boolean onboarded) {
		this.onboarded = onboarded;
	}

	/** 通知を送るかどうかを返す */
	public boolean isNotifyEnabled() {
		return notifyEnabled;
	}

	/** 通知を送るかどうかを入れる */
	public void setNotifyEnabled(boolean notifyEnabled) {
		this.notifyEnabled = notifyEnabled;
	}

	/** 通知する時刻を返す。通知しないなら null */
	public LocalTime getNotifyTime() {
		return notifyTime;
	}

	/** 通知する時刻を入れる */
	public void setNotifyTime(LocalTime notifyTime) {
		this.notifyTime = notifyTime;
	}

	/** 通知先のメールアドレスを返す。空なら email に送る */
	public String getNotifyEmail() {
		return notifyEmail;
	}

	/** 通知先のメールアドレスを入れる */
	public void setNotifyEmail(String notifyEmail) {
		this.notifyEmail = notifyEmail;
	}

	/** 最後に通知した日を返す */
	public LocalDate getLastNotifiedDate() {
		return lastNotifiedDate;
	}

	/** 最後に通知した日を入れる */
	public void setLastNotifiedDate(LocalDate lastNotifiedDate) {
		this.lastNotifiedDate = lastNotifiedDate;
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