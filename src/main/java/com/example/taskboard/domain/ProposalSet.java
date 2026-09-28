package com.example.taskboard.domain;

// ===== インポート =====
// java.time.LocalDateTime … 日付＋時刻。押した時刻・決めた時刻に使う
// java.util.Objects       … null でも落ちずに比べる道具。equals で使う
//
// 以下は JPA の部品。
// Entity         … このクラスはテーブルに対応する、という印
// Table          … 対応するテーブル名を指定する
// Id             … 主キーの印
// GeneratedValue … 番号を自動で振らせる
// GenerationType … その振り方の種類（IDENTITY を使う）
// Column         … 列名・空の可否・文字数の上限を指定する
// Enumerated     … 列挙型をどう保存するかを指定する
// EnumType       … その保存の仕方（STRING を使う）
// PrePersist     … 保存される直前に動かすメソッドの印

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 提案1セット。proposal_sets テーブルの1行にあたる。
 *
 * スタートを押すたびに1行できる。カードは3枚ぶら下がる。
 *
 * 選ばれなかったセットも消さずに残す。
 * 「何を出して、何が選ばれなかったか」が、提案の質を測る材料になるため。
 *
 * requestedAt と decidedAt の差が「迷い時間」。
 * 押してから決めるまでにどれだけ止まっていたかが、そのまま記録になる。
 *
 * promptText には、AIへ送った文章をそのまま保存している。
 * プロンプトを直していく期間は、送った文章そのものが残っているほうが役に立つ。
 * 「この3案が出たときのプロンプトはこれ」と後から見返せる。
 *
 * source には AI / MOCK / FALLBACK のどれかが入る。
 * AIが何回失敗したかを、あとから数えられるようにするため。
 *
 * timeOfDay を持っているのは、あとで「時間帯ごとに何が選ばれたか」を
 * 数えるため。requestedAt の時から計算もできるが、そうすると
 * SituationJudge が持っている時間の区切りを SQL 側にも書くことになる。
 * 片方だけ変えたときに、静かに食い違う。
 */

// ===== アノテーション =====
// @Entity … このクラスはテーブルに対応する、という宣言
// @Table  … 対応するテーブル名

@Entity
@Table(name = "proposal_sets")
public class ProposalSet {

	// ===== フィールド =====

	/** 主キー。保存する前は番号が未定なので、null を入れられる Long にする */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 誰のセットか。users テーブルの id を指す */
	@Column(name = "user_id", nullable = false)
	private Long userId;

	/**
	 * スタートを押した時刻。
	 *
	 * 初期値をここに書かず、保存の直前に入れている。
	 * 下の onCreate を見ること。
	 *
	 * decidedAt との差が迷い時間になるので、
	 * 実際に押された時刻とずれると、記録の意味が失われる。
	 */
	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;

	/**
	 * 選んだ、または別の案にした時刻。まだ決めていなければ null。
	 *
	 * requestedAt との差が迷い時間。
	 */
	@Column(name = "decided_at")
	private LocalDateTime decidedAt;

	/**
	 * このとき SituationJudge が決めた時間帯。朝 / 昼 / 夜 / 深夜。
	 *
	 * 集計のために持っている。
	 * 「朝はどの分野が選ばれやすいか」を数えるのに要る。
	 *
	 * 時刻から計算せずに保存するのは、時間の区切りの判断を
	 * SituationJudge 1か所に閉じ込めておくため。
	 */
	@Column(name = "time_of_day", length = 10)
	private String timeOfDay;

	/**
	 * どんな出し方をしたか。いまは NORMAL だけ。
	 *
	 * 提案の出し方を増やしたときに、あとから区別できるようにしてある。
	 * 使い道が増えなければ、列ごと消してよい。
	 */
	@Column(length = 20)
	private String mode = "NORMAL";

	/**
	 * この3案がどこから来たか。AI / MOCK / FALLBACK のどれか。
	 *
	 * AIが失敗した回数を、あとから数えるために持っている。
	 * 「落ちても止まらない」と言うだけでなく、
	 * 何回落ちたかを数字で言えるようにするため。
	 */
	@Column(length = 20)
	private String source = "MOCK";

	/**
	 * セットの状態。OPEN（まだ選んでいない）／CHOSEN（選んだ）／RETOPIC（別の案にした）。
	 *
	 * EnumType.STRING で文字列として保存する。
	 * 数字で保存すると、あとから項目を足したときに過去のデータの意味がずれる。
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ProposalStatus status = ProposalStatus.OPEN;

	/**
	 * AIへ送った文章そのもの。
	 *
	 * 先頭にプロンプトの版（# prompt v5.1）が書き込まれているので、
	 * 「どの版が、どんな3案を出したか」を突き合わせられる。
	 *
	 * ※ length は元のファイルの値をそのまま使うこと。
	 *    ここを変えると起動時に ALTER TABLE が走る。
	 */
	@Column(name = "prompt_text", length = 5000)
	private String promptText;

	// ===== メソッド（保存の前に動くもの） =====

	/**
	 * 保存される直前に、押した時刻を入れる。
	 *
	 * @PrePersist が付いたメソッドは、JPA が insert の直前に呼ぶ。
	 * 呼ぶのは自分ではなく、仕組みのほう。
	 *
	 * フィールドに = LocalDateTime.now() と書くと、
	 * 「new した瞬間」の時刻が入る。
	 *
	 * いまは押されてから作っているので差はほとんど無いが、
	 * AIの応答を待つ数秒ぶんだけ、new と保存の間が空く。
	 * 迷い時間の起点なので、実際に保存された時刻に揃えておく。
	 *
	 * すでに値が入っているときは触らない。
	 * ProposalService が押した時刻を明示的に入れている場合は、そちらを優先する。
	 */
	@PrePersist
	protected void onCreate() {
		if (requestedAt == null) {
			requestedAt = LocalDateTime.now();
		}
	}

	// ===== メソッド（getter / setter） =====

	/** 主キーを返す */
	public Long getId() {
		return id;
	}

	/** 主キーを入れる。ふつうはデータベースが振るので、自分で呼ぶことはない */
	public void setId(Long id) {
		this.id = id;
	}

	/** 誰のセットかを返す */
	public Long getUserId() {
		return userId;
	}

	/** 誰のセットかを入れる */
	public void setUserId(Long userId) {
		this.userId = userId;
	}

	/** スタートを押した時刻を返す */
	public LocalDateTime getRequestedAt() {
		return requestedAt;
	}

	/** スタートを押した時刻を入れる */
	public void setRequestedAt(LocalDateTime requestedAt) {
		this.requestedAt = requestedAt;
	}

	/** 決めた時刻を返す。まだ決めていなければ null */
	public LocalDateTime getDecidedAt() {
		return decidedAt;
	}

	/** 決めた時刻を入れる */
	public void setDecidedAt(LocalDateTime decidedAt) {
		this.decidedAt = decidedAt;
	}

	/** このときの時間帯を返す */
	public String getTimeOfDay() {
		return timeOfDay;
	}

	/** このときの時間帯を入れる */
	public void setTimeOfDay(String timeOfDay) {
		this.timeOfDay = timeOfDay;
	}

	/** 出し方を返す。null のときは NORMAL として扱う */
	public String getMode() {
		return mode != null ? mode : "NORMAL";
	}

	/** 出し方を入れる */
	public void setMode(String mode) {
		this.mode = mode;
	}

	/**
	 * どこから来た3案かを返す。null のときは MOCK として扱う。
	 *
	 * null を返すと、呼び出し側の "AI".equals(source) のような比較で
	 * 静かに false になり、原因が分かりにくい。
	 */
	public String getSource() {
		return source != null ? source : "MOCK";
	}

	/** どこから来た3案かを入れる */
	public void setSource(String source) {
		this.source = source;
	}

	/** 状態を返す。null のときは未選択として扱う */
	public ProposalStatus getStatus() {
		return status != null ? status : ProposalStatus.OPEN;
	}

	/** 状態を入れる */
	public void setStatus(ProposalStatus status) {
		this.status = status;
	}

	/** 送った文章を返す */
	public String getPromptText() {
		return promptText;
	}

	/** 送った文章を入れる */
	public void setPromptText(String promptText) {
		this.promptText = promptText;
	}

	// ===== メソッド（同じセットかどうかの判定） =====

	/**
	 * 同じセットかどうかを判定する。
	 *
	 * 比べるのは id。Task と同じ考え方で、
	 * セットには「変わらない鍵」が他に無いため。
	 *
	 * id が null のとき（まだ保存していないとき）は false を返す。
	 */
	@Override
	public boolean equals(Object other) {

		if (this == other) {
			return true;
		}
		if (!(other instanceof ProposalSet set)) {
			return false;
		}
		return id != null && Objects.equals(id, set.id);
	}

	/**
	 * ハッシュ値を返す。
	 *
	 * 固定の値を返している。id から作ると、保存の前後で値が変わるため。
	 * 詳しくは Task.hashCode の説明を見ること。
	 */
	@Override
	public int hashCode() {
		return 31;
	}
}