package com.example.taskboard.domain;

// ===== インポート =====
// java.time.LocalDateTime … 日付＋時刻。登録日時に使う
// java.util.Objects       … null でも落ちずに比べる道具。equals で使う
//
// 以下は JPA の部品。
// Entity          … このクラスはテーブルに対応する、という印
// Table           … 対応するテーブル名を指定する
// Id              … 主キーの印
// GeneratedValue  … 番号を自動で振らせる
// GenerationType  … その振り方の種類（IDENTITY を使う）
// Column          … 列名・空の可否・文字数の上限を指定する
// PrePersist      … 保存される直前に動かすメソッドの印

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
 * 利用者1人。users テーブルの1行にあたる。
 *
 * Googleでログインした人ごとに1行できる。
 * 初めてログインした人は、そのときに登録される。
 *
 * このアプリは個人情報を持たない。
 * Googleに要求しているのは openid だけなので、受け取るのは sub という識別子1つ。
 * メールアドレスも名前も送られてこないし、保存もしない。
 *
 * 持っているのは5つだけ。
 *   id              … 内部の番号
 *   googleSub       … ログインした人を見分ける鍵
 *   createdAt       … いつ登録されたか
 *   soundEnabled    … 効果音を鳴らすか
 *   confettiEnabled … 紙ふぶきを出すか
 *
 * 設定は「入れるか入れないか」だけにしてある。
 * 以前は音量を 0〜100 で持っていたが、効果音は一瞬鳴るだけで、
 * 途中の値を選ぶ場面が無かった。
 * 選べる値を減らすと、設定画面で立ち止まる回数も減る。
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

	/**
	 * 登録日時。
	 *
	 * 初期値をここに書かず、保存の直前に入れている。
	 * 下の onCreate を見ること。
	 */
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	/**
	 * 効果音を鳴らすか。
	 *
	 * 型が boolean ではなく Boolean（null を入れられるほう）なのは、
	 * あとから足した列だから。
	 * それ以前に登録された行には値が無く、null で読み込まれる。
	 * null のときの扱いは getter で面倒を見ている。
	 */
	@Column(name = "sound_enabled")
	private Boolean soundEnabled = true;

	/**
	 * 紙ふぶきを出すか。
	 *
	 * 音とは別に持っている。
	 * 音を切りたい場面（静かな場所）と、演出を切りたい場面（気が散る）は別なので、
	 * 片方でもう片方を兼ねることができない。
	 */
	@Column(name = "confetti_enabled")
	private Boolean confettiEnabled = true;

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

	// ===== メソッド（保存の前に動くもの） =====

	/**
	 * 保存される直前に、登録日時を入れる。
	 *
	 * @PrePersist が付いたメソッドは、JPA が insert の直前に呼ぶ。
	 * 呼ぶのは自分ではなく、仕組みのほう。
	 *
	 * フィールドに = LocalDateTime.now() と書くと、
	 * 「new した瞬間」の時刻が入る。保存の時刻とは別物になる。
	 *
	 * すでに値が入っているときは触らない。
	 * データを移すときなど、こちらで時刻を指定したい場合があるため。
	 */
	@PrePersist
	protected void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	// ===== メソッド（getter / setter） =====
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

	/** 登録日時を返す */
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	/**
	 * 効果音を鳴らすかを返す。
	 *
	 * null のときは true を返す。
	 * この列はあとから足したので、それ以前に登録された行では null になる。
	 *
	 * 既定を true にしているのは、切りたい人だけが切ればよいため。
	 * 最初から鳴らないと、鳴る設定があること自体に気づけない。
	 *
	 * 戻り値を boolean（小文字）にしているので、
	 * 呼び出し側は null を気にせず if にそのまま書ける。
	 */
	public boolean isSoundEnabled() {
		return soundEnabled == null || soundEnabled;
	}

	/**
	 * 効果音を鳴らすかを入れる。
	 *
	 * null が来たら true にする。
	 * 画面のスイッチは true か false しか送らないが、制限しているのは画面だけ。
	 * 設定の保存先は JSON を受け取るので、直接送れば何でも入る。
	 * 守るなら、受け取る側で守る。
	 *
	 * @param soundEnabled 鳴らすかどうか。null でもよい
	 */
	public void setSoundEnabled(Boolean soundEnabled) {
		this.soundEnabled = soundEnabled == null || soundEnabled;
	}

	/**
	 * 紙ふぶきを出すかを返す。null のときは true。
	 */
	public boolean isConfettiEnabled() {
		return confettiEnabled == null || confettiEnabled;
	}

	/**
	 * 紙ふぶきを出すかを入れる。null が来たら true にする。
	 *
	 * @param confettiEnabled 出すかどうか。null でもよい
	 */
	public void setConfettiEnabled(Boolean confettiEnabled) {
		this.confettiEnabled = confettiEnabled == null || confettiEnabled;
	}

	// ===== メソッド（同じ利用者かどうかの判定） =====

	/**
	 * 同じ利用者かどうかを判定する。
	 *
	 * 比べるのは googleSub。id ではない。
	 *
	 * id は保存されるまで null なので、保存前と保存後で結果が変わってしまう。
	 * googleSub はコンストラクタで入り、あとから変わらないので、
	 * いつ比べても同じ答えになる。
	 *
	 * instanceof のあとに変数名を書くと、型の確認と代入を同時にできる。
	 *
	 * Objects.equals は、どちらかが null でも落ちずに比べてくれる。
	 * googleSub.equals(...) と書くと、null のときに落ちる。
	 */
	@Override
	public boolean equals(Object other) {

		if (this == other) {
			return true;
		}
		if (!(other instanceof User user)) {
			return false;
		}
		return Objects.equals(googleSub, user.googleSub);
	}

	/**
	 * ハッシュ値を返す。
	 *
	 * equals で使っているものと同じ項目から作る必要がある。
	 * 違うものを使うと、equals では同じなのにハッシュ値が違う、
	 * という状態になり、Set や Map の中で見つからなくなる。
	 *
	 * equals と hashCode は必ず対で用意する。
	 * 片方だけ書くと、その食い違いが起きる。
	 */
	@Override
	public int hashCode() {
		return Objects.hash(googleSub);
	}
}