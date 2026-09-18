package com.example.taskboard.domain;

// ===== インポート =====
// java.time.LocalDateTime … 日付＋時刻。登録日時に使う
// java.util.Objects       … null でも落ちずに比べる道具。equals で使う
//
// 以下は JPA の部品。
// Entity         … このクラスはテーブルに対応する、という印
// Table          … 対応するテーブル名を指定する
// Id             … 主キーの印
// GeneratedValue … 番号を自動で振らせる
// GenerationType … その振り方の種類（IDENTITY を使う）
// Column         … 列名・空の可否・文字数の上限を指定する
// PrePersist     … 保存される直前に動かすメソッドの印

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 通知の届け先1件。push_subscriptions テーブルの1行にあたる。
 *
 * ブラウザで通知を許可すると1行できる。切ると消える。
 * 1人が複数の端末で許可していることがあるので、利用者1人につき何行でもありうる。
 *
 * 保存しているのは「どの端末のどのブラウザか」を表す文字列と、
 * 暗号化に使う鍵2つだけ。メールアドレスでも名前でもない。
 * endpoint 単体では誰かを特定できない。
 *
 * 本文は届け先ごとの鍵で暗号化して送るので、
 * 途中を通る配信元（GoogleやMozilla）からは中身が読めない。
 *
 * 通知の入切は、この行があるかどうかそのもの。
 * users 側に「通知する／しない」の列は持たせていない。
 * 同じことを2か所に持つと、必ずどこかで食い違う。
 *
 * setter は1つも用意していない。
 * 届け先は「登録するか、消すか」の2択で、途中で書き換えることがない。
 * 用意しないことが、そのまま「変えるな」という意思表示になる。
 */

// ===== アノテーション =====
// @Entity … このクラスはテーブルに対応する、という宣言
// @Table  … 対応するテーブル名

@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

	// ===== フィールド =====

	/** 主キー。保存する前は番号が未定なので、null を入れられる Long にする */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 誰の届け先か。users テーブルの id を指す */
	@Column(name = "user_id", nullable = false)
	private Long userId;

	/**
	 * 届け先のURL。
	 *
	 * ブラウザが発行する長い文字列で、どの端末のどのブラウザかを表す。
	 * 送信のたびに、このURLへ暗号化した本文を投げる。
	 *
	 * unique = true は「同じ値を2行に入れられない」という制限。
	 * 同じ端末が二重に登録されるのを、データベース側で防いでいる。
	 *
	 * 登録の前に findByEndpoint で確認しているので、
	 * ふつうはここで弾かれることはない。二重の守りとして置いてある。
	 *
	 * 500文字にしているのは、提供元によって長さがかなり違うため。
	 */
	@Column(nullable = false, unique = true, length = 500)
	private String endpoint;

	/**
	 * 暗号化に使う公開鍵。
	 *
	 * ブラウザが持つ秘密鍵と対になっていて、
	 * この鍵で暗号化したものは、その端末でしか開けない。
	 */
	@Column(nullable = false, length = 255)
	private String p256dh;

	/**
	 * 暗号化に使う補助の値。
	 *
	 * p256dh と一緒に使う。どちらか片方だけでは本文を作れない。
	 */
	@Column(nullable = false, length = 255)
	private String auth;

	/**
	 * 登録日時。
	 *
	 * 初期値をここに書かず、保存の直前に入れている。
	 * 下の onCreate を見ること。
	 */
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	// ===== コンストラクタ =====
	// 自分でコンストラクタを1つでも書くと、Java が自動で用意する
	// 「引数なしのコンストラクタ」が消える。JPA はそれを必要とするので、手で書き足している。

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
	 * 4つとも、あとから変えない値。
	 * だから setter ではなくコンストラクタで受け取り、
	 * 一度作ったら中身が動かないようにしてある。
	 *
	 * @param userId   誰の届け先か
	 * @param endpoint 届け先のURL
	 * @param p256dh   暗号化に使う公開鍵
	 * @param auth     暗号化に使う補助の値
	 */
	public PushSubscription(Long userId, String endpoint, String p256dh, String auth) {
		this.userId = userId;
		this.endpoint = endpoint;
		this.p256dh = p256dh;
		this.auth = auth;
	}

	// ===== メソッド（保存の前に動くもの） =====

	/**
	 * 保存される直前に、登録日時を入れる。
	 *
	 * @PrePersist が付いたメソッドは、JPA が insert の直前に呼ぶ。
	 * 呼ぶのは自分ではなく、仕組みのほう。
	 *
	 * フィールドに = LocalDateTime.now() と書くと、
	 * 「new した瞬間」の時刻が入る。保存の時刻とは別物になる。
	 */
	@PrePersist
	protected void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	// ===== メソッド（getter） =====
	// setter は1つも無い。読むだけ。

	/** 主キーを返す */
	public Long getId() {
		return id;
	}

	/** 誰の届け先かを返す */
	public Long getUserId() {
		return userId;
	}

	/**
	 * 届け先のURLを返す。
	 *
	 * ログには出さない。長い文字列で、端末を指すものなので。
	 */
	public String getEndpoint() {
		return endpoint;
	}

	/** 暗号化に使う公開鍵を返す */
	public String getP256dh() {
		return p256dh;
	}

	/** 暗号化に使う補助の値を返す */
	public String getAuth() {
		return auth;
	}

	/** 登録日時を返す */
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	// ===== メソッド（同じ届け先かどうかの判定） =====

	/**
	 * 同じ届け先かどうかを判定する。
	 *
	 * 比べるのは endpoint。id ではない。
	 *
	 * User で googleSub を使ったのと同じ考え方。
	 * endpoint はコンストラクタで入り、あとから変わらず、
	 * データベース側でも unique になっている。
	 * id と違って、保存の前後で値が変わらない。
	 */
	@Override
	public boolean equals(Object other) {

		if (this == other) {
			return true;
		}
		if (!(other instanceof PushSubscription subscription)) {
			return false;
		}
		return Objects.equals(endpoint, subscription.endpoint);
	}

	/**
	 * ハッシュ値を返す。
	 *
	 * equals で使っているものと同じ項目から作る。
	 * endpoint は変わらない値なので、Task のように固定値にする必要がない。
	 */
	@Override
	public int hashCode() {
		return Objects.hash(endpoint);
	}
}