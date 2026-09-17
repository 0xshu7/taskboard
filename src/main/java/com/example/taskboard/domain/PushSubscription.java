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
 * 通知の届け先1件。push_subscriptions テーブルの1行にあたる。
 *
 * ブラウザに「通知を受け取ります」と許可をもらうと、3つの文字列が返ってくる。
 * それをそのまま保存しておき、通知を送るときに使う。
 *
 *   endpoint … 届け先のURL。ブラウザの提供元（GoogleやMozilla）が用意する窓口
 *   p256dh   … 本文を暗号化するための公開鍵
 *   auth     … 同じく暗号化に使う秘密の値
 *
 * これは「どの端末のどのブラウザか」を表すもので、誰かを特定する情報ではない。
 * メールアドレスのように、それ自体で連絡が取れるものではない。
 *
 * 本文は p256dh と auth で暗号化して送るので、
 * 途中を通る提供元の窓口からは中身が読めない。
 *
 * 1人が複数持つことがある。会社のパソコンと自宅のパソコンで許可すれば2行になる。
 * だから userId を持たせて、送るときは全部に送る。
 */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

	// ===== フィールド =====

	/** 主キー。保存する前は番号が未定なので、null を入れられる Long にする */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 誰のものか。users テーブルの id を指す */
	@Column(name = "user_id", nullable = false)
	private Long userId;

	/**
	 * 届け先のURL。
	 *
	 * 長い。500文字みておく。
	 * unique = true は、同じ端末が二重に登録されるのを防ぐため。
	 * 許可を取り直すたびに増えると、同じ通知が何度も届く。
	 */
	@Column(nullable = false, unique = true, length = 500)
	private String endpoint;

	/** 本文を暗号化するための公開鍵 */
	@Column(nullable = false, length = 255)
	private String p256dh;

	/** 同じく暗号化に使う値 */
	@Column(nullable = false, length = 255)
	private String auth;

	/** 登録日時。new した時点の時刻がそのまま入る */
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	// ===== コンストラクタ =====

	/**
	 * 引数なしのコンストラクタ。
	 * データベースから読み込むときに JPA が使う。
	 * ふつうのコードから呼ばれないよう protected にしてある。
	 */
	protected PushSubscription() {
	}

	/**
	 * 新しい届け先を作る。
	 *
	 * 3つとも、ブラウザから受け取った値をそのまま入れる。
	 * あとから書き換えることはないので setter は作らない。
	 *
	 * @param userId   利用者のID
	 * @param endpoint 届け先のURL
	 * @param p256dh   暗号化用の公開鍵
	 * @param auth     暗号化用の値
	 */
	public PushSubscription(Long userId, String endpoint, String p256dh, String auth) {
		this.userId = userId;
		this.endpoint = endpoint;
		this.p256dh = p256dh;
		this.auth = auth;
	}

	// ===== メソッド =====
	// setter は作らない。届け先は登録するか消すかのどちらかで、書き換えることがない

	/** 主キーを返す */
	public Long getId() {
		return id;
	}

	/** 誰のものかを返す */
	public Long getUserId() {
		return userId;
	}

	/** 届け先のURLを返す */
	public String getEndpoint() {
		return endpoint;
	}

	/** 暗号化用の公開鍵を返す */
	public String getP256dh() {
		return p256dh;
	}

	/** 暗号化用の値を返す */
	public String getAuth() {
		return auth;
	}

	/** 登録日時を返す */
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
